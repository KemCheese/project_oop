package com.yourapp.ticketpass.dispute.application;

import java.time.Instant;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.yourapp.ticketpass.dispute.domain.Dispute;
import com.yourapp.ticketpass.dispute.domain.DisputeEvidenceRepository;
import com.yourapp.ticketpass.dispute.domain.DisputeRepository;
import com.yourapp.ticketpass.dispute.domain.DisputeStatus;
import com.yourapp.ticketpass.shared.event.DisputeOutcome;
import com.yourapp.ticketpass.shared.event.DisputeRespondedEvent;
import com.yourapp.ticketpass.shared.event.DisputeWithdrawnEvent;
import com.yourapp.ticketpass.shared.exception.BusinessRuleViolationException;
import com.yourapp.ticketpass.shared.exception.ResourceNotFoundException;
import com.yourapp.ticketpass.shared.storage.FileStorageService;
import com.yourapp.ticketpass.ticket.application.TicketStateMachineService;
import com.yourapp.ticketpass.ticket.domain.TicketStatus;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

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
@Slf4j
@Service
@RequiredArgsConstructor
public class RespondToDisputeUseCase {

    /** Cổng truy cập dữ liệu khiếu nại. */
    private final DisputeRepository disputeRepository;

    /** Cổng truy cập dữ liệu bằng chứng, dùng khi người bán gửi kèm bằng chứng giải trình. */
    private final DisputeEvidenceRepository disputeEvidenceRepository;

    /** Cổng lưu trữ tệp cho bằng chứng phía người bán (nguyên tắc 3). */
    private final FileStorageService fileStorageService;

    /** Máy trạng thái vé — nơi duy nhất được phép đổi trạng thái vé. */
    private final TicketStateMachineService ticketStateMachineService;

    @Autowired(required = false)
    private ApplicationEventPublisher eventPublisher;

    /**
     * Người bán gửi giải trình cho một khiếu nại.
     *
     * @param disputeId ID khiếu nại
     * @param sellerId  ID người bán đang giải trình
     * @param response  nội dung giải trình
     * @return hồ sơ khiếu nại sau khi đã ghi nhận giải trình
     */
    @Transactional
    public Dispute submitSellerResponse(Long disputeId, Long sellerId, String response) {
        if (disputeId == null) {
            throw new BusinessRuleViolationException("ID khiếu nại không được để trống.");
        }
        if (sellerId == null) {
            throw new BusinessRuleViolationException("ID người bán không được để trống.");
        }
        if (response == null || response.trim().isEmpty()) {
            throw new BusinessRuleViolationException("Nội dung giải trình của người bán không được để trống.");
        }

        // (1) Tra Dispute theo disputeId, ném ResourceNotFoundException nếu không có
        Dispute dispute = disputeRepository.findById(disputeId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy khiếu nại với ID: " + disputeId));

        // (2) Xác minh sellerId đúng là người bán của khiếu nại
        if (!Objects.equals(dispute.getSellerId(), sellerId)) {
            throw new BusinessRuleViolationException("Người dùng (ID: " + sellerId + ") không phải là người bán của khiếu nại này.");
        }

        // (3) Kiểm tra status đang là OPEN, AWAITING_SELLER_RESPONSE hoặc UNDER_REVIEW (chưa có phán quyết cuối cùng)
        DisputeStatus currentStatus = dispute.getStatus();
        if (currentStatus != DisputeStatus.OPEN
                && currentStatus != DisputeStatus.AWAITING_SELLER_RESPONSE
                && currentStatus != DisputeStatus.UNDER_REVIEW) {
            throw new BusinessRuleViolationException("Khiếu nại không ở trạng thái cho phép người bán gửi giải trình (trạng thái hiện tại: " + currentStatus + ").");
        }

        // Chặn gửi lặp lại nếu người bán đã giải trình trước đó
        if (dispute.getSellerResponse() != null && !dispute.getSellerResponse().isBlank()) {
            throw new BusinessRuleViolationException("Bạn đã gửi giải trình cho khiếu nại này trước đó.");
        }

        // Kiểm tra Instant.now() chưa vượt sellerResponseDeadline — quá hạn thì ném BusinessRuleViolationException
        Instant now = Instant.now();
        if (dispute.getSellerResponseDeadline() != null && now.isAfter(dispute.getSellerResponseDeadline())) {
            throw new BusinessRuleViolationException("Đã quá hạn gửi giải trình của người bán (hạn chót: " + dispute.getSellerResponseDeadline() + ").");
        }

        // (4) Gán sellerResponse = response, sellerRespondedAt = Instant.now(), status = SELLER_RESPONDED rồi save và trả về
        dispute.setSellerResponse(response.trim());
        dispute.setSellerRespondedAt(now);
        dispute.setStatus(DisputeStatus.SELLER_RESPONDED);

        Dispute savedDispute = disputeRepository.save(dispute);
        log.info("Người bán (ID: {}) đã gửi giải trình thành công cho khiếu nại (ID: {})", sellerId, disputeId);

        if (eventPublisher != null) {
            eventPublisher.publishEvent(new DisputeRespondedEvent(
                    disputeId,
                    dispute.getTicketId(),
                    sellerId,
                    dispute.getBuyerId(),
                    "Vé #" + dispute.getTicketId(),
                    now
            ));
        }
        return savedDispute;
    }

    /**
     * Người mua rút khiếu nại đã mở.
     *
     * @param disputeId ID khiếu nại
     * @param buyerId   ID người mua đang rút khiếu nại
     * @param reason    lý do rút, lưu vào {@code resolutionNote} để có dấu vết
     */
    @Transactional
    public void withdrawDispute(Long disputeId, Long buyerId, String reason) {
        if (disputeId == null) {
            throw new BusinessRuleViolationException("ID khiếu nại không được để trống.");
        }
        if (buyerId == null) {
            throw new BusinessRuleViolationException("ID người mua không được để trống.");
        }
        if (reason == null || reason.trim().isEmpty()) {
            throw new BusinessRuleViolationException("Lý do rút khiếu nại không được để trống.");
        }

        // (1) Tra Dispute theo disputeId
        Dispute dispute = disputeRepository.findById(disputeId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy khiếu nại với ID: " + disputeId));

        // (2) Xác minh buyerId đúng là người mua đã mở khiếu nại
        if (!Objects.equals(dispute.getBuyerId(), buyerId)) {
            throw new BusinessRuleViolationException("Người dùng (ID: " + buyerId + ") không phải là người mua đã mở khiếu nại này.");
        }

        // (3) Từ chối nếu khiếu nại đã có phán quyết (RESOLVED_BUYER_WINS, RESOLVED_SELLER_WINS) hoặc đã WITHDRAWN
        DisputeStatus currentStatus = dispute.getStatus();
        if (currentStatus == DisputeStatus.RESOLVED_BUYER_WINS || currentStatus == DisputeStatus.RESOLVED_SELLER_WINS) {
            throw new BusinessRuleViolationException("Không thể rút khiếu nại đã có phán quyết của quản trị viên (trạng thái: " + currentStatus + ").");
        }
        if (currentStatus == DisputeStatus.WITHDRAWN) {
            throw new BusinessRuleViolationException("Khiếu nại này đã được rút trước đó.");
        }

        // (4) Gán status = WITHDRAWN, outcome = DisputeOutcome.WITHDRAWN, resolutionNote = reason, reviewedAt = Instant.now() rồi save
        Instant now = Instant.now();
        dispute.setStatus(DisputeStatus.WITHDRAWN);
        dispute.setOutcome(DisputeOutcome.WITHDRAWN);
        dispute.setResolutionNote(reason.trim());
        dispute.setReviewedAt(now);
        disputeRepository.save(dispute);

        // (5) Đưa vé trở lại luồng hoàn tất bình thường bằng cách chuyển vé về AWAITING_USE
        // để AutoCompleteScheduler tiếp tục xử lý khi hết cửa sổ.
        ticketStateMachineService.transition(dispute.getTicketId(), TicketStatus.AWAITING_USE);
        log.info("Người mua (ID: {}) đã rút khiếu nại (ID: {}). Vé (ID: {}) đã được chuyển về AWAITING_USE.",
                buyerId, disputeId, dispute.getTicketId());

        if (eventPublisher != null) {
            eventPublisher.publishEvent(new DisputeWithdrawnEvent(
                    disputeId,
                    dispute.getTicketId(),
                    dispute.getSellerId(),
                    buyerId,
                    "Vé #" + dispute.getTicketId(),
                    now
            ));
        }
    }
}
