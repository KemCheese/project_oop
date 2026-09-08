package com.yourapp.ticketpass.dispute.application;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.yourapp.ticketpass.dispute.domain.DisputeRepository;
import com.yourapp.ticketpass.dispute.domain.DisputeStatus;
import com.yourapp.ticketpass.payment.application.ReleaseFundsUseCase;
import com.yourapp.ticketpass.shared.event.CompletionReason;
import com.yourapp.ticketpass.ticket.domain.TicketRepository;

import lombok.RequiredArgsConstructor;

/**
 * Job định kỳ đóng các giao dịch và khiếu nại đã hết thời hạn.
 *
 * <p><b>Vì sao cần job này:</b> phần lớn giao dịch kết thúc trong im lặng — người mua dùng vé
 * xong và không bấm gì cả. Nếu chờ người mua xác nhận thủ công thì tiền của người bán sẽ bị
 * giữ vô thời hạn. Job này là cơ chế đóng giao dịch mặc định của hệ thống.</p>
 *
 * <p><b>{@link #autoCompleteExpiredTickets()} — chạy mỗi 10 phút:</b> quét các vé đang ở
 * {@code AWAITING_USE} đã quá {@code disputeWindowClosesAt} và KHÔNG có khiếu nại nào đang mở,
 * chuyển vé sang {@code AUTO_COMPLETE} rồi gọi
 * {@code releaseFundsUseCase.release(ticketId, }{@link CompletionReason#AUTO_COMPLETE}{@code )}
 * để giải ngân cho người bán và hoàn cọc.</p>
 *
 * <p>Điều kiện "không có khiếu nại nào đang mở" là chốt an toàn quan trọng nhất: nếu thiếu, một
 * vé đang tranh chấp có thể bị job tự động giải ngân cho người bán trước khi admin phân xử,
 * và tiền đã chuyển đi thì không thể lấy lại được.</p>
 *
 * <p><b>{@link #expireOverdueSellerResponses()} — chạy mỗi giờ:</b> quét các khiếu nại ở
 * {@code AWAITING_SELLER_RESPONSE} đã quá {@code sellerResponseDeadline} và chuyển sang
 * {@link DisputeStatus#EXPIRED}. Hồ sơ hết hạn vẫn được đưa tới admin xem xét — việc người bán
 * im lặng là tình tiết bất lợi cho người bán, không phải lý do để tự động xử người bán thua.</p>
 *
 * <p><b>Cấu hình:</b> {@code @EnableScheduling} được bật ở
 * {@code com.yourapp.ticketpass.TicketPassApplication}. Không có annotation đó thì hai method
 * dưới đây sẽ không bao giờ chạy.</p>
 *
 * <p>Module: dispute</p>
 *
 * <p>Chủ sở hữu: Người B</p>
 */
@Component
@RequiredArgsConstructor
public class AutoCompleteScheduler {

    /** Truy cập dữ liệu vé để tìm các vé đã hết cửa sổ khiếu nại. */
    private final TicketRepository ticketRepository;

    /** Cổng truy cập dữ liệu khiếu nại, dùng để kiểm tra tranh chấp đang mở và tìm hồ sơ quá hạn. */
    private final DisputeRepository disputeRepository;

    /** Use case giải ngân, được gọi cho từng vé đủ điều kiện tự động hoàn tất. */
    private final ReleaseFundsUseCase releaseFundsUseCase;

    /**
     * Tự động hoàn tất các giao dịch đã hết cửa sổ khiếu nại mà không có tranh chấp.
     *
     * <p>Chạy mỗi 10 phút.</p>
     */
    @Scheduled(cron = "0 */10 * * * *")
    void autoCompleteExpiredTickets() {
        throw new UnsupportedOperationException("TODO [Người B] Prompt 2: (1) lấy danh sách vé ở trạng thái "
                + "AWAITING_USE có disputeWindowClosesAt < Instant.now() qua ticketRepository; "
                + "(2) với từng vé, BỎ QUA nếu disputeRepository.existsByTicketIdAndStatusIn(ticketId, "
                + "List.of(OPEN, AWAITING_SELLER_RESPONSE, SELLER_RESPONDED, UNDER_REVIEW, EXPIRED)) trả true "
                + "— đây là chốt an toàn chống giải ngân một vé đang tranh chấp; "
                + "(3) chuyển vé sang TicketStatus.AUTO_COMPLETE; "
                + "(4) gọi releaseFundsUseCase.release(ticketId, CompletionReason.AUTO_COMPLETE); "
                + "(5) bọc mỗi vé trong try-catch và ghi log lỗi riêng để một vé lỗi không làm dừng "
                + "toàn bộ lượt quét; xử lý từng vé trong transaction riêng, không dùng một transaction chung.");
    }

    /**
     * Đánh dấu hết hạn các khiếu nại mà người bán không phản hồi đúng thời hạn.
     *
     * <p>Chạy mỗi giờ.</p>
     */
    @Scheduled(cron = "0 0 * * * *")
    void expireOverdueSellerResponses() {
        throw new UnsupportedOperationException("TODO [Người B] Prompt 2: (1) gọi "
                + "disputeRepository.findOverdueSellerResponses(Instant.now()) để lấy các khiếu nại ở "
                + "AWAITING_SELLER_RESPONSE đã quá sellerResponseDeadline; "
                + "(2) với từng hồ sơ, gán status = DisputeStatus.EXPIRED rồi save — "
                + "KHÔNG tự động xử người bán thua, hồ sơ vẫn phải qua admin phân xử, "
                + "việc người bán im lặng chỉ là tình tiết bất lợi; "
                + "(3) bọc mỗi hồ sơ trong try-catch và ghi log lỗi riêng để một hồ sơ lỗi không làm dừng "
                + "toàn bộ lượt quét.");
    }
}
