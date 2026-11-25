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

@Service
@RequiredArgsConstructor
public class TicketScheduleService {

    public static final Set<TicketStatus> ACTIVE_BUYER_SCHEDULE_STATUSES = Collections.unmodifiableSet(EnumSet.of(
            TicketStatus.PAYMENT_ESCROWED,
            TicketStatus.DEPOSIT_LOCKED,
            TicketStatus.AWAITING_USE,
            TicketStatus.DISPUTE_UNDER_REVIEW,
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
            case AWAITING_USE -> "Đã mở mã vé QR · Chờ sự kiện";
            case DISPUTE_UNDER_REVIEW -> "Đang trong quá trình xử lý khiếu nại";
            case AUTO_COMPLETE -> "Giao dịch đã hoàn tất";
            case RESOLVED_SELLER_WINS -> "Đã chốt sở hữu (tranh chấp đã đóng)";
            default -> status.name();
        };
    }
}
