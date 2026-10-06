package com.yourapp.ticketpass.ticket.application;

import com.yourapp.ticketpass.shared.event.TicketExpiredEvent;
import com.yourapp.ticketpass.ticket.domain.Ticket;
import com.yourapp.ticketpass.ticket.domain.TicketRepository;
import com.yourapp.ticketpass.ticket.domain.TicketStatus;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduler tự động gỡ các vé đang rao bán (LISTED) đã quá hạn thời gian sự kiện (eventStartAt <= now).
 *
 * <p><b>Hành động khi quá hạn:</b></p>
 * <ol>
 *   <li>Chuyển trạng thái vé từ {@link TicketStatus#LISTED} sang {@link TicketStatus#CANCELLED}
 *       qua {@link TicketStateMachineService}.</li>
 *   <li>Bắn sự kiện {@link TicketExpiredEvent} để module notification thông báo cho Người bán (Seller)
 *       và tất cả những người dùng đã theo dõi (đánh sao) vé này.</li>
 * </ol>
 *
 * <p>Module: ticket</p>
 * <p>Chủ sở hữu: Dev C / Người A</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TicketExpirationScheduler {

    private final TicketRepository ticketRepository;
    private final TicketStateMachineService ticketStateMachineService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Quét định kỳ mỗi phút các vé LISTED mà thời gian sự kiện đã quá hạn (eventStartAt <= now).
     */
    @Scheduled(cron = "${app.scheduler.ticket-expiration-cron:0 */1 * * * *}")
    public void scanExpiredListedTickets() {
        Instant now = Instant.now();
        List<Ticket> expiredTickets = ticketRepository.findExpiredListedTickets(now);
        if (expiredTickets == null || expiredTickets.isEmpty()) {
            return;
        }

        log.info("[TicketExpirationScheduler] Tìm thấy {} vé LISTED quá hạn sự kiện", expiredTickets.size());

        for (Ticket ticket : expiredTickets) {
            Long ticketId = ticket.getId();
            try {
                if (ticket.getStatus() != TicketStatus.LISTED) {
                    continue;
                }

                log.info("[TicketExpirationScheduler] Vé #{} (eventStartAt={}) đã quá hạn. Tự động chuyển sang CANCELLED.",
                        ticketId, ticket.getEventStartAt());
                ticketStateMachineService.transition(ticketId, TicketStatus.CANCELLED);

                if (eventPublisher != null) {
                    eventPublisher.publishEvent(new TicketExpiredEvent(
                            ticketId,
                            ticket.getSellerId(),
                            ticket.getTitle(),
                            now
                    ));
                }

                log.info("[TicketExpirationScheduler] Đã xử lý gỡ vé quá hạn thành công cho vé #{}", ticketId);
            } catch (Exception e) {
                log.error("[TicketExpirationScheduler] Lỗi khi tự động gỡ vé quá hạn #{}: {}", ticketId, e.getMessage(), e);
            }
        }
    }
}
