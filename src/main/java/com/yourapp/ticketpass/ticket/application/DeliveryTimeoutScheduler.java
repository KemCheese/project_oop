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

/**
 * Scheduler tự động xác nhận nhận vé khi quá hạn 30 phút (UC-06b — Auto-Confirm Delivery).
 *
 * <p><b>Bối cảnh:</b> Vé đang ở trạng thái {@link TicketStatus#TRANSFERRED} (Người bán đã nạp cọc 50%
 * và hệ thống đã mở khoá ảnh QR ẩn cho Người mua).</p>
 *
 * <p><b>Hành động khi quá hạn:</b></p>
 * <ul>
 *   <li>Nếu sau 30 phút Người mua không bấm "Đã nhận vé" và không bấm báo lỗi kỹ thuật ảnh QR:
 *       Hệ thống tự động chuyển trạng thái vé sang {@link TicketStatus#AWAITING_USE}.</li>
 *   <li>Ghi nhận {@code receivedAt = now} và bắt đầu đếm ngược cửa sổ khiếu nại (BR-09) bình thường
 *       (2h với phim, 3h với xe khách, 24h với concert) để bảo vệ quyền lợi khiếu nại cho Buyer.</li>
 *   <li>Nếu vé đã được gắn trạng thái {@link TicketStatus#TECHNICAL_ISSUE}, scheduler tự động bỏ qua
 *       để bảo vệ cọc của Người bán không bị hoàn tất oan.</li>
 * </ul>
 *
 * <p>Module: ticket</p>
 * Chu so huu: Nguoi A / Dev C
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeliveryTimeoutScheduler {

    private final TicketRepository ticketRepository;
    private final TransferTicketService transferTicketService;

    @Value("${app.delivery.timeout-minutes:30}")
    private long timeoutMinutes = 30;

    /**
     * Quét định kỳ mỗi phút các vé TRANSFERRED đã quá hạn 30 phút kể từ transferredAt.
     */
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
                // Kiểm tra lại trạng thái hiện tại (bảo đảm không phải TECHNICAL_ISSUE)
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
