package com.yourapp.ticketpass.payment.application;

import com.yourapp.ticketpass.reputation.application.UpdateReputationUseCase;
import com.yourapp.ticketpass.shared.event.DepositTimeoutEvent;
import com.yourapp.ticketpass.ticket.application.TicketStateMachineService;
import com.yourapp.ticketpass.ticket.domain.Ticket;
import com.yourapp.ticketpass.ticket.domain.TicketRepository;
import com.yourapp.ticketpass.ticket.domain.TicketStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DepositTimeoutScheduler {

    private final TicketRepository ticketRepository;
    private final RefundBuyerUseCase refundBuyerUseCase;
    private final TicketStateMachineService ticketStateMachineService;
    private final UpdateReputationUseCase updateReputationUseCase;

    @Autowired(required = false)
    private ApplicationEventPublisher eventPublisher;

    @Value("${app.deposit.timeout-minutes:30}")
    private long timeoutMinutes = 30;

    @Scheduled(cron = "${app.scheduler.deposit-timeout-cron:0 */1 * * * *}")
    public void scanDepositTimeouts() {
        Instant now = Instant.now();
        Instant deadline = now.minus(Duration.ofMinutes(timeoutMinutes));

        List<Ticket> overdueTickets = ticketRepository.findOverdueDepositTickets(deadline);
        if (overdueTickets == null || overdueTickets.isEmpty()) {
            return;
        }

        log.info("[DepositTimeoutScheduler] Tìm thấy {} vé quá hạn nạp cọc (deadline={})", overdueTickets.size(),
                deadline);

        for (Ticket ticket : overdueTickets) {
            Long ticketId = ticket.getId();
            Long sellerId = ticket.getSellerId();

            try {
                log.warn(
                        "[DepositTimeoutScheduler] Vé #{} quá hạn nạp cọc (escrowedAt={}). Tiến hành hoàn tiền, huỷ vé và phạt người bán.",
                        ticketId, ticket.getEscrowedAt());

                refundBuyerUseCase.refundFull(ticketId, "Quá hạn nạp cọc 30 phút (Seller Deposit Timeout - UC-04b)");

                ticketStateMachineService.transition(ticketId, TicketStatus.CANCELLED);

                updateReputationUseCase.recordDepositTimeout(sellerId, ticketId);

                if (eventPublisher != null) {
                    eventPublisher.publishEvent(new DepositTimeoutEvent(
                            ticketId,
                            sellerId,
                            ticket.getBuyerId(),
                            ticket.getTitle(),
                            now));
                }

                log.info("[DepositTimeoutScheduler] Đã xử lý thành công timeout nạp cọc cho vé #{}, sellerId={}",
                        ticketId, sellerId);
            } catch (Exception e) {
                log.error("[DepositTimeoutScheduler] Lỗi khi xử lý timeout nạp cọc cho vé #{}: {}", ticketId,
                        e.getMessage(), e);
            }
        }
    }
}
