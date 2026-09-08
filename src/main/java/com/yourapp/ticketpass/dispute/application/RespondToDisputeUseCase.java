package com.yourapp.ticketpass.dispute.application;

import org.springframework.stereotype.Service;

import com.yourapp.ticketpass.dispute.domain.Dispute;
import com.yourapp.ticketpass.dispute.domain.DisputeEvidenceRepository;
import com.yourapp.ticketpass.dispute.domain.DisputeRepository;
import com.yourapp.ticketpass.shared.storage.FileStorageService;

import lombok.RequiredArgsConstructor;

/**
 * Use case xử lý phản hồi của hai bên trong một khiếu nại đang mở: người bán giải trình,
 * hoặc người mua chủ động rút khiếu nại.
 *
 * <p><b>Người bán giải trình:</b> chỉ được gửi khi khiếu nại còn ở
 * {@code AWAITING_SELLER_RESPONSE} và chưa quá {@code sellerResponseDeadline}. Sau khi gửi, hồ
 * sơ chuyển sang {@code SELLER_RESPONDED} và sẵn sàng để admin xem xét. Người bán quá hạn không
 * phản hồi sẽ bị {@code AutoCompleteScheduler} chuyển hồ sơ sang {@code EXPIRED}.</p>
 *
 * <p><b>Người mua rút khiếu nại:</b> chỉ được rút khi chưa có phán quyết. Rút khiếu nại đưa vé
 * trở lại luồng hoàn tất bình thường, tức tiền sẽ được giải ngân cho người bán khi hết cửa sổ.
 * Việc rút là quyết định không thể đảo ngược, người mua không được mở lại khiếu nại cho cùng
 * một vé.</p>
 *
 * <p><b>Giao dịch:</b> {@code @Transactional} sẽ được thêm ở Prompt 2.</p>
 *
 * <p>Module: dispute</p>
 *
 * <p>Chủ sở hữu: Người B</p>
 */
@Service
@RequiredArgsConstructor
public class RespondToDisputeUseCase {

    /** Cổng truy cập dữ liệu khiếu nại. */
    private final DisputeRepository disputeRepository;

    /** Cổng truy cập dữ liệu bằng chứng, dùng khi người bán gửi kèm bằng chứng giải trình. */
    private final DisputeEvidenceRepository disputeEvidenceRepository;

    /** Cổng lưu trữ tệp cho bằng chứng phía người bán (nguyên tắc 3). */
    private final FileStorageService fileStorageService;

    /**
     * Người bán gửi giải trình cho một khiếu nại.
     *
     * @param disputeId ID khiếu nại
     * @param sellerId  ID người bán đang giải trình
     * @param response  nội dung giải trình
     * @return hồ sơ khiếu nại sau khi đã ghi nhận giải trình
     */
    public Dispute submitSellerResponse(Long disputeId, Long sellerId, String response) {
        throw new UnsupportedOperationException("TODO [Người B] Prompt 2: (1) tra Dispute theo disputeId, "
                + "ném ResourceNotFoundException nếu không có; "
                + "(2) xác minh sellerId đúng là người bán của khiếu nại; "
                + "(3) kiểm tra status đang là OPEN hoặc AWAITING_SELLER_RESPONSE và "
                + "Instant.now() chưa vượt sellerResponseDeadline — quá hạn thì ném "
                + "BusinessRuleViolationException; "
                + "(4) gán sellerResponse = response, sellerRespondedAt = Instant.now(), "
                + "status = SELLER_RESPONDED rồi save và trả về. Nhớ thêm @Transactional.");
    }

    /**
     * Người mua rút khiếu nại đã mở.
     *
     * @param disputeId ID khiếu nại
     * @param buyerId   ID người mua đang rút khiếu nại
     * @param reason    lý do rút, lưu vào {@code resolutionNote} để có dấu vết
     */
    public void withdrawDispute(Long disputeId, Long buyerId, String reason) {
        throw new UnsupportedOperationException("TODO [Người B] Prompt 2: (1) tra Dispute theo disputeId; "
                + "(2) xác minh buyerId đúng là người mua đã mở khiếu nại; "
                + "(3) từ chối nếu khiếu nại đã có phán quyết (RESOLVED_BUYER_WINS, RESOLVED_SELLER_WINS) "
                + "hoặc đã WITHDRAWN; "
                + "(4) gán status = WITHDRAWN, outcome = DisputeOutcome.WITHDRAWN, "
                + "resolutionNote = reason, reviewedAt = Instant.now() rồi save; "
                + "(5) đưa vé trở lại luồng hoàn tất bình thường bằng cách chuyển vé về AWAITING_USE "
                + "để AutoCompleteScheduler tiếp tục xử lý khi hết cửa sổ. Nhớ thêm @Transactional.");
    }
}
