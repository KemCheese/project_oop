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

/**
 * Scheduler tự động xử lý quá hạn nạp cọc của Người bán (UC-04b — Seller Deposit Timeout).
 *
 * <p><b>Bối cảnh:</b> Vé đang ở trạng thái {@link TicketStatus#PAYMENT_ESCROWED} (Buyer đã nạp 100%
 * giá bán + phí sàn vào Escrow). Người bán có 30 phút kể từ thời điểm ký quỹ ({@code escrowedAt})
 * để nạp cọc 50% bảo đảm giao dịch.</p>
 *
 * <p><b>Hành động khi quá hạn:</b></p>
 * <ol>
 *   <li>Kích hoạt hoàn trả 100% tiền vé + 100% phí sàn về tài khoản của Người mua
 *       qua {@link RefundBuyerUseCase#refundFull}. Buyer không chịu bất kỳ chi phí nào.</li>
 *   <li>Chuyển trạng thái vé sang {@link TicketStatus#CANCELLED} qua {@link TicketStateMachineService}.
 *       Tuyệt đối không để vé tự động quay lại {@code LISTED} (AC-01.3).</li>
 *   <li>Áp chế tài phạt trừ −15 điểm uy tín đối với Người bán qua {@link UpdateReputationUseCase#recordDepositTimeout}
 *       để triệt tiêu hành vi bỏ rơi giao dịch.</li>
 * </ol>
 *
 * <p>Module: payment</p>
 * Chu so huu: Nguoi B / Dev C
 */
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

    /**
     * Quét định kỳ mỗi phút các vé PAYMENT_ESCROWED đã quá hạn 30 phút kể từ escrowedAt.
     */
    @Scheduled(cron = "${app.scheduler.deposit-timeout-cron:0 */1 * * * *}")
    public void scanDepositTimeouts() {
        Instant now = Instant.now();
        Instant deadline = now.minus(Duration.ofMinutes(timeoutMinutes));

        List<Ticket> overdueTickets = ticketRepository.findOverdueDepositTickets(deadline);
        if (overdueTickets == null || overdueTickets.isEmpty()) {
            return;
        }

        log.info("[DepositTimeoutScheduler] Tìm thấy {} vé quá hạn nạp cọc (deadline={})", overdueTickets.size(), deadline);

        for (Ticket ticket : overdueTickets) {
            Long ticketId = ticket.getId();
            Long sellerId = ticket.getSellerId();

            try {
                log.warn("[DepositTimeoutScheduler] Vé #{} quá hạn nạp cọc (escrowedAt={}). Tiến hành hoàn tiền, huỷ vé và phạt người bán.",
                        ticketId, ticket.getEscrowedAt());

                // 1. Hoàn trả 100% tiền vé + phí sàn cho Người mua
                refundBuyerUseCase.refundFull(ticketId, "Quá hạn nạp cọc 30 phút (Seller Deposit Timeout - UC-04b)");

                // 2. Chuyển vé sang CANCELLED (gỡ khỏi sàn, không quay lại LISTED)
                ticketStateMachineService.transition(ticketId, TicketStatus.CANCELLED);

                // 3. Phạt trừ −15 điểm uy tín của Người bán
                updateReputationUseCase.recordDepositTimeout(sellerId);

                if (eventPublisher != null) {
                    eventPublisher.publishEvent(new DepositTimeoutEvent(
                            ticketId,
                            sellerId,
                            ticket.getBuyerId(),
                            ticket.getTitle(),
                            now
                    ));
                }

                log.info("[DepositTimeoutScheduler] Đã xử lý thành công timeout nạp cọc cho vé #{}, sellerId={}", ticketId, sellerId);
            } catch (Exception e) {
                // Bọc try-catch để lỗi một vé không làm gián đoạn toàn bộ batch
                log.error("[DepositTimeoutScheduler] Lỗi khi xử lý timeout nạp cọc cho vé #{}: {}", ticketId, e.getMessage(), e);
            }
        }
    }
}
