package com.yourapp.ticketpass.ticket.application;

import com.yourapp.ticketpass.shared.event.TicketUpcomingReminderEvent;
import com.yourapp.ticketpass.ticket.domain.Ticket;
import com.yourapp.ticketpass.ticket.domain.TicketRepository;
import com.yourapp.ticketpass.ticket.domain.TimeSlot;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service đảm nhận việc đánh dấu nhắc lịch nguyên tử (idempotent) và phát {@link TicketUpcomingReminderEvent}.
 *
 * <p>Tách thành bean riêng so với {@link TicketReminderScheduler} để bảo toàn ranh giới proxy
 * {@code @Transactional} (tránh lỗi AOP self-invocation) và sử dụng mặc định {@code Propagation.REQUIRED}
 * nhằm tham gia cùng transaction khi được gọi từ {@code markDepositLocked} (tránh deadlock InnoDB).</p>
 *
 * <p>Module: ticket</p>
 * <p>Chủ sở hữu: Người A (phối hợp Dev C)</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TicketReminderService {

    private final TicketRepository ticketRepository;
    private final TicketScheduleService ticketScheduleService;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    @Value("${app.reminder.lead-hours:24}")
    private long leadHours = 24L;

    /**
     * Đánh dấu và phát sự kiện nhắc lịch nếu vé đủ điều kiện và chưa từng được nhắc trước đó.
     *
     * @param ticket vé cần nhắc
     * @param lastMinutePurchase {@code true} nếu đây là thông báo nhắc sớm ngay lúc mua sát giờ (&lt;= 24h)
     * @return {@code true} nếu claim và phát event thành công, {@code false} nếu bỏ qua hoặc đã nhắc trước đó
     */
    @Transactional
    public boolean triggerReminderIfDue(Ticket ticket, boolean lastMinutePurchase) {
        if (ticket == null || ticket.getId() == null || ticket.getBuyerId() == null
                || ticket.getEventStartAt() == null || ticket.getReminded24hAt() != null) {
            return false;
        }

        Instant now = resolveNow();
        Instant windowEnd = now.plus(Duration.ofHours(resolveLeadHours()));

        // Chỉ nhắc nếu sự kiện vẫn còn ở tương lai và nằm trong cửa sổ nhắc lịch
        if (!ticket.getEventStartAt().isAfter(now) || ticket.getEventStartAt().isAfter(windowEnd)) {
            return false;
        }

        // Đọc đầy đủ dữ liệu trước khi markReminded24hIfUnset (clearAutomatically = true) xóa L1 cache
        Long ticketId = ticket.getId();
        Long sellerId = ticket.getSellerId();
        Long buyerId = ticket.getBuyerId();
        String title = ticket.getTitle();
        String venue = ticket.getVenue();
        Instant eventStartAt = ticket.getEventStartAt();
        Instant effectiveEndAt = ticketScheduleService != null
                ? ticketScheduleService.resolveTimeSlot(ticket).map(TimeSlot::end).orElse(ticket.getEventEndAt())
                : ticket.getEventEndAt();

        int updatedRows = ticketRepository.markReminded24hIfUnset(ticketId, now);
        if (updatedRows != 1) {
            return false;
        }
        // Đồng bộ lại trạng thái trong bộ nhớ cho entity caller đang giữ
        ticket.setReminded24hAt(now);

        if (eventPublisher != null) {
            eventPublisher.publishEvent(new TicketUpcomingReminderEvent(
                    ticketId,
                    sellerId,
                    buyerId,
                    title,
                    venue,
                    eventStartAt,
                    effectiveEndAt,
                    lastMinutePurchase,
                    now
            ));
        }

        log.info("[TicketReminderService] Đã phát sự kiện nhắc lịch vé #{} cho buyerId={} (lastMinute={}).",
                ticketId, buyerId, lastMinutePurchase);
        return true;
    }

    /**
     * Gọi ngay sau khi người bán hoàn tất khóa cọc 50%: nếu sự kiện bắt đầu trong vòng
     * {@code <= leadHours}, nhắc sớm cho người mua ngay 1 lần.
     */
    @Transactional
    public boolean triggerEarlyReminderIfWithinLeadWindow(Ticket ticket) {
        return triggerReminderIfDue(ticket, true);
    }

    /** Cho phép test cấu hình leadHours không cần Spring context. */
    void setLeadHours(long leadHours) {
        this.leadHours = leadHours;
    }

    long resolveLeadHours() {
        return leadHours > 0 ? leadHours : 24L;
    }

    private Instant resolveNow() {
        Instant instant = clock != null ? clock.instant() : null;
        return instant != null ? instant : Instant.now();
    }
}
