package com.yourapp.ticketpass.ticket.application;

import com.yourapp.ticketpass.shared.dto.ConflictingTicketInfo;
import com.yourapp.ticketpass.ticket.domain.Ticket;
import com.yourapp.ticketpass.ticket.domain.TicketAdapter;
import com.yourapp.ticketpass.ticket.domain.TicketRepository;
import com.yourapp.ticketpass.ticket.domain.TicketStatus;
import com.yourapp.ticketpass.ticket.domain.TimeSlot;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service kiểm tra trùng khung giờ sử dụng vé của người mua (Schedule Conflict Detection).
 *
 * <p>Áp dụng Value Object {@link TimeSlot} (khoảng nửa mở {@code [start, end)}) kết hợp
 * Strategy {@link TicketAdapter#defaultEventDuration()} làm fallback cho các vé cũ chưa lưu
 * {@code eventEndAt}. Chỉ đối chiếu trên 6 trạng thái Buyer đang sở hữu vé có hiệu lực.</p>
 *
 * <p>Module: ticket</p>
 * <p>Chủ sở hữu: Người A (phối hợp Dev C)</p>
 */
@Service
@RequiredArgsConstructor
public class TicketScheduleService {

    /** 6 trạng thái vé mà người mua đang nắm quyền/sở hữu hiệu lực theo phương án 3.PA A. */
    public static final Set<TicketStatus> ACTIVE_BUYER_SCHEDULE_STATUSES = Collections.unmodifiableSet(EnumSet.of(
            TicketStatus.PAYMENT_ESCROWED,
            TicketStatus.DEPOSIT_LOCKED,
            TicketStatus.TRANSFERRED,
            TicketStatus.AWAITING_USE,
            TicketStatus.AUTO_COMPLETE,
            TicketStatus.RESOLVED_SELLER_WINS
    ));

    private static final Duration FALLBACK_DEFAULT_DURATION = Duration.ofHours(2);
    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FULL_FORMATTER = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");

    private final TicketRepository ticketRepository;
    private final List<TicketAdapter> ticketAdapters;

    /**
     * Tìm danh sách vé mà {@code buyerId} đã sở hữu có khung giờ trùng với {@code targetTicket}.
     *
     * @param buyerId id người mua dự kiến
     * @param targetTicket vé đang xem / chuẩn bị mua
     * @return danh sách thông tin vé trùng giờ (rỗng nếu không có xung đột)
     */
    @Transactional(readOnly = true)
    public List<ConflictingTicketInfo> findConflictingTickets(Long buyerId, Ticket targetTicket) {
        if (buyerId == null || targetTicket == null) {
            return Collections.emptyList();
        }
        Optional<TimeSlot> targetSlotOpt = resolveTimeSlot(targetTicket);
        if (targetSlotOpt.isEmpty()) {
            return Collections.emptyList();
        }
        TimeSlot targetSlot = targetSlotOpt.get();

        List<Ticket> candidates = ticketRepository.findPotentialScheduleConflicts(
                buyerId,
                ACTIVE_BUYER_SCHEDULE_STATUSES,
                targetSlot.end(),
                targetSlot.start(),
                targetTicket.getId()
        );
        if (candidates == null || candidates.isEmpty()) {
            return Collections.emptyList();
        }

        List<ConflictingTicketInfo> conflicts = new ArrayList<>();
        for (Ticket candidate : candidates) {
            if (candidate == null) {
                continue;
            }
            // Kiểm tra lại phòng trường hợp test mock trả về vé trùng ID hoặc ngoài 6 trạng thái hiệu lực
            if (targetTicket.getId() != null && targetTicket.getId().equals(candidate.getId())) {
                continue;
            }
            if (candidate.getStatus() == null || !ACTIVE_BUYER_SCHEDULE_STATUSES.contains(candidate.getStatus())) {
                continue;
            }

            Optional<TimeSlot> candidateSlotOpt = resolveTimeSlot(candidate);
            if (candidateSlotOpt.isEmpty()) {
                continue;
            }
            TimeSlot candidateSlot = candidateSlotOpt.get();
            if (targetSlot.overlaps(candidateSlot)) {
                conflicts.add(toConflictInfo(candidate, candidateSlot));
            }
        }
        return conflicts;
    }

    /**
     * Suy ra {@link TimeSlot} hiệu lực cho một vé: ưu tiên {@code eventEndAt} nếu hợp lệ,
     * nếu null thì fallback theo {@link TicketAdapter#defaultEventDuration()}.
     */
    public Optional<TimeSlot> resolveTimeSlot(Ticket ticket) {
        if (ticket == null || ticket.getEventStartAt() == null) {
            return Optional.empty();
        }
        Instant start = ticket.getEventStartAt();
        Instant end = ticket.getEventEndAt();
        if (end != null && end.isAfter(start)) {
            return Optional.of(new TimeSlot(start, end));
        }
        Duration fallback = resolveFallbackDuration(ticket);
        return Optional.of(new TimeSlot(start, start.plus(fallback)));
    }

    /**
     * Kiểm tra thời điểm kết thúc của vé có phải là giá trị ước tính (fallback) hay không.
     */
    public boolean isEstimatedEndTime(Ticket ticket) {
        if (ticket == null || ticket.getEventStartAt() == null) {
            return false;
        }
        Instant end = ticket.getEventEndAt();
        return end == null || !end.isAfter(ticket.getEventStartAt());
    }

    private Duration resolveFallbackDuration(Ticket ticket) {
        if (ticketAdapters != null && ticket.getTicketType() != null) {
            for (TicketAdapter adapter : ticketAdapters) {
                if (adapter != null && adapter.supportedType() == ticket.getTicketType()) {
                    Duration duration = adapter.defaultEventDuration();
                    if (duration != null && !duration.isNegative() && !duration.isZero()) {
                        return duration;
                    }
                }
            }
        }
        return FALLBACK_DEFAULT_DURATION;
    }

    private ConflictingTicketInfo toConflictInfo(Ticket candidate, TimeSlot slot) {
        return new ConflictingTicketInfo(
                candidate.getId(),
                candidate.getTitle(),
                candidate.getEventName(),
                candidate.getVenue(),
                candidate.getStatus().name(),
                formatStatusLabel(candidate.getStatus()),
                slot.start(),
                slot.end(),
                isEstimatedEndTime(candidate),
                formatTimeRange(slot.start(), slot.end())
        );
    }

    private String formatTimeRange(Instant start, Instant end) {
        ZonedDateTime startZdt = start.atZone(VIETNAM_ZONE);
        ZonedDateTime endZdt = end.atZone(VIETNAM_ZONE);
        if (startZdt.toLocalDate().equals(endZdt.toLocalDate())) {
            return TIME_FORMATTER.format(startZdt) + " - " + TIME_FORMATTER.format(endZdt)
                    + " " + DATE_FORMATTER.format(startZdt);
        }
        return FULL_FORMATTER.format(startZdt) + " - " + FULL_FORMATTER.format(endZdt);
    }

    private String formatStatusLabel(TicketStatus status) {
        return switch (status) {
            case PAYMENT_ESCROWED -> "Đã ký quỹ (chờ người bán khóa cọc)";
            case DEPOSIT_LOCKED -> "Người bán đã khóa cọc";
            case TRANSFERRED -> "Đã mở mã vé QR";
            case AWAITING_USE -> "Đang giữ vé chờ sử dụng";
            case AUTO_COMPLETE -> "Giao dịch đã hoàn tất";
            case RESOLVED_SELLER_WINS -> "Đã chốt sở hữu (tranh chấp đã đóng)";
            default -> status.name();
        };
    }
}
