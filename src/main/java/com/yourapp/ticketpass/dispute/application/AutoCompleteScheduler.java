package com.yourapp.ticketpass.dispute.application;

import java.time.Instant;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.yourapp.ticketpass.dispute.domain.Dispute;
import com.yourapp.ticketpass.dispute.domain.DisputeRepository;
import com.yourapp.ticketpass.dispute.domain.DisputeStatus;
import com.yourapp.ticketpass.payment.application.ReleaseFundsUseCase;
import com.yourapp.ticketpass.shared.event.CompletionReason;
import com.yourapp.ticketpass.ticket.domain.Ticket;
import com.yourapp.ticketpass.ticket.domain.TicketRepository;
import com.yourapp.ticketpass.ticket.domain.TicketStatus;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Job định kỳ đóng các giao dịch và khiếu nại đã hết thời hạn.
 *
 * <p>
 * <b>Vì sao cần job này:</b> phần lớn giao dịch kết thúc trong im lặng — người
 * mua dùng vé
 * xong và không bấm gì cả. Nếu chờ người mua xác nhận thủ công thì tiền của
 * người bán sẽ bị
 * giữ vô thời hạn. Job này là cơ chế đóng giao dịch mặc định của hệ thống.
 * </p>
 *
 * <p>
 * <b>{@link #autoCompleteExpiredTickets()} — chạy mỗi 10 phút:</b> quét các vé
 * đang ở
 * {@code AWAITING_USE} đã quá {@code disputeWindowClosesAt} và KHÔNG có khiếu
 * nại nào đang mở,
 * chuyển vé sang {@code AUTO_COMPLETE} rồi gọi
 * {@code releaseFundsUseCase.release(ticketId, }{@link CompletionReason#AUTO_COMPLETE}{@code )}
 * để giải ngân cho người bán và hoàn cọc.
 * </p>
 *
 * <p>
 * Điều kiện "không có khiếu nại nào đang mở" là chốt an toàn quan trọng nhất:
 * nếu thiếu, một
 * vé đang tranh chấp có thể bị job tự động giải ngân cho người bán trước khi
 * admin phân xử,
 * và tiền đã chuyển đi thì không thể lấy lại được.
 * </p>
 *
 * <p>
 * <b>{@link #expireOverdueSellerResponses()} — chạy mỗi giờ:</b> quét các khiếu
 * nại ở
 * {@code AWAITING_SELLER_RESPONSE} đã quá {@code sellerResponseDeadline} và
 * chuyển sang
 * {@link DisputeStatus#EXPIRED}. Hồ sơ hết hạn vẫn được đưa tới admin xem xét —
 * việc người bán
 * im lặng là tình tiết bất lợi cho người bán, không phải lý do để tự động xử
 * người bán thua.
 * </p>
 *
 * <p>
 * <b>Cấu hình:</b> {@code @EnableScheduling} được bật ở
 * {@code com.yourapp.ticketpass.TicketPassApplication}. Không có annotation đó
 * thì hai method
 * dưới đây sẽ không bao giờ chạy.
 * </p>
 *
 * <p>
 * Module: dispute
 * </p>
 *
 * <p>
 * Chủ sở hữu: Người B
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AutoCompleteScheduler {

    /** Truy cập dữ liệu vé để tìm các vé đã hết cửa sổ khiếu nại. */
    private final TicketRepository ticketRepository;

    /**
     * Cổng truy cập dữ liệu khiếu nại, dùng để kiểm tra tranh chấp đang mở và tìm
     * hồ sơ quá hạn.
     */
    private final DisputeRepository disputeRepository;

    /** Use case giải ngân, được gọi cho từng vé đủ điều kiện tự động hoàn tất. */
    private final ReleaseFundsUseCase releaseFundsUseCase;

    /**
     * Tự động hoàn tất các giao dịch đã hết cửa sổ khiếu nại mà không có tranh
     * chấp.
     *
     * <p>
     * Chạy mỗi 10 phút.
     * </p>
     */
    @Scheduled(cron = "0 */10 * * * *")
    void autoCompleteExpiredTickets() {
        Instant now = Instant.now();
        // (1) Lấy danh sách vé ở trạng thái AWAITING_USE có disputeWindowClosesAt < Instant.now() qua ticketRepository
        List<Ticket> dueTickets = ticketRepository.findDueForAutoComplete(TicketStatus.AWAITING_USE, now);
        if (dueTickets == null || dueTickets.isEmpty()) {
            return;
        }

        List<DisputeStatus> blockingStatuses = List.of(
                DisputeStatus.OPEN,
                DisputeStatus.AWAITING_SELLER_RESPONSE,
                DisputeStatus.SELLER_RESPONDED,
                DisputeStatus.UNDER_REVIEW,
                DisputeStatus.EXPIRED
        );

        for (Ticket ticket : dueTickets) {
            Long ticketId = ticket.getId();
            try {
                // (2) BỎ QUA nếu có khiếu nại chưa giải quyết — chốt an toàn chống giải ngân vé đang tranh chấp
                boolean hasActiveDispute = disputeRepository.existsByTicketIdAndStatusIn(ticketId, blockingStatuses);
                if (hasActiveDispute) {
                    log.warn("[AutoCompleteScheduler] Vé #{} đang có khiếu nại chưa xử lý xong, bỏ qua tự động hoàn tất.", ticketId);
                    continue;
                }

                // (3) & (4) releaseFundsUseCase.release tự động hoàn tất giao dịch trong transaction riêng:
                // giải ngân cho seller, hoàn cọc seller, chuyển vé sang AUTO_COMPLETE và publish TicketTransactionCompletedEvent
                releaseFundsUseCase.release(ticketId, CompletionReason.AUTO_COMPLETE);
                log.info("[AutoCompleteScheduler] Tự động hoàn tất và giải ngân thành công cho vé #{}", ticketId);
            } catch (Exception e) {
                // (5) Bọc mỗi vé trong try-catch riêng để một vé lỗi không làm dừng toàn bộ lượt quét
                log.error("[AutoCompleteScheduler] Lỗi khi tự động hoàn tất cho vé #{}: {}", ticketId, e.getMessage(), e);
            }
        }
    }

    /**
     * Đánh dấu hết hạn các khiếu nại mà người bán không phản hồi đúng thời hạn.
     *
     * <p>
     * Chạy mỗi giờ.
     * </p>
     */
    @Scheduled(cron = "0 0 * * * *")
    void expireOverdueSellerResponses() {
        Instant now = Instant.now();
        // (1) Lấy các khiếu nại ở AWAITING_SELLER_RESPONSE đã quá sellerResponseDeadline
        List<Dispute> overdueDisputes = disputeRepository.findOverdueSellerResponses(now);
        if (overdueDisputes == null || overdueDisputes.isEmpty()) {
            return;
        }

        for (Dispute dispute : overdueDisputes) {
            try {
                // (2) Gán status = DisputeStatus.EXPIRED rồi save — KHÔNG tự động xử người bán thua
                dispute.setStatus(DisputeStatus.EXPIRED);
                disputeRepository.save(dispute);
                log.info("[AutoCompleteScheduler] Khiếu nại #{} quá hạn phản hồi của người bán, đã chuyển sang EXPIRED", dispute.getId());
            } catch (Exception e) {
                // (3) Bọc mỗi hồ sơ trong try-catch riêng để một hồ sơ lỗi không làm dừng toàn bộ lượt quét
                log.error("[AutoCompleteScheduler] Lỗi khi chuyển trạng thái EXPIRED cho khiếu nại #{}: {}", dispute.getId(), e.getMessage(), e);
            }
        }
    }
}
