package com.yourapp.ticketpass.ticket.application;

import com.yourapp.ticketpass.ticket.domain.Ticket;
import com.yourapp.ticketpass.ticket.domain.TicketRepository;
import com.yourapp.ticketpass.ticket.domain.TicketStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeliveryTimeoutScheduler {

    private final TicketRepository ticketRepository;
    private final TransferTicketService transferTicketService;

    @Value("${app.delivery.timeout-minutes:30}")
    private long timeoutMinutes = 30;

    @Scheduled(cron = "${app.scheduler.delivery-timeout-cron:0 */1 * * * *}")
    public void scanDeliveryTimeouts() {
        Instant now = Instant.now();
        Instant deadline = now.minus(Duration.ofMinutes(timeoutMinutes));

        List<Ticket> overdueTickets = ticketRepository.findOverdueDeliveryTickets(deadline);
        if (overdueTickets == null || overdueTickets.isEmpty()) {
            return;
        }

        log.info("[DeliveryTimeoutScheduler] Tìm thấy {} vé TRANSFERRED quá hạn 30 phút nhận vé", overdueTickets.size());

        for (Ticket ticket : overdueTickets) {
            Long ticketId = ticket.getId();
            try {
                if (ticket.getStatus() != TicketStatus.TRANSFERRED) {
                    continue;
                }

                log.info("[DeliveryTimeoutScheduler] Vé #{} quá hạn 30 phút nhận vé. Tự động chuyển sang AWAITING_USE.", ticketId);
                transferTicketService.autoConfirmReceipt(ticketId);
            } catch (Exception e) {
                log.error("[DeliveryTimeoutScheduler] Lỗi khi tự động xác nhận nhận vé #{}: {}", ticketId, e.getMessage(), e);
            }
        }
    }
}
