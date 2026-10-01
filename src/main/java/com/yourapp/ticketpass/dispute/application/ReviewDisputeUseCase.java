package com.yourapp.ticketpass.dispute.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.yourapp.ticketpass.dispute.domain.Dispute;
import com.yourapp.ticketpass.dispute.domain.DisputeEvidenceRepository;
import com.yourapp.ticketpass.dispute.domain.DisputeRepository;
import com.yourapp.ticketpass.dispute.domain.DisputeStatus;
import com.yourapp.ticketpass.payment.application.RefundBuyerUseCase;
import com.yourapp.ticketpass.payment.application.ReleaseFundsUseCase;
import com.yourapp.ticketpass.shared.dto.ForfeitBreakdown;
import com.yourapp.ticketpass.shared.event.CompletionReason;
import com.yourapp.ticketpass.shared.event.DisputeOutcome;
import com.yourapp.ticketpass.shared.event.DisputeResolvedEvent;
import com.yourapp.ticketpass.shared.exception.BusinessRuleViolationException;
import com.yourapp.ticketpass.shared.exception.ResourceNotFoundException;
import com.yourapp.ticketpass.ticket.application.TicketStateMachineService;
import com.yourapp.ticketpass.ticket.domain.TicketStatus;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Use case admin phân xử tranh chấp — điểm kết thúc của một khiếu nại.
 *
 * <p><b>HAI NHÁNH PHÁN QUYẾT (trình tự bắt buộc, không được đổi):</b></p>
 * <pre>
 * BUYER_WINS  : refundBuyerUseCase.refundWithForfeitedDeposit(...) -&gt; ve chuyen RESOLVED_BUYER_WINS
 *               -&gt; publish DisputeResolvedEvent(BUYER_WINS)
 *
 * SELLER_WINS : releaseFundsUseCase.release(ticketId, CompletionReason.DISPUTE_RESOLVED_SELLER_WINS)
 *               -&gt; ve chuyen RESOLVED_SELLER_WINS
 *               -&gt; publish DisputeResolvedEvent(SELLER_WINS)
 * </pre>
 *
 * <p>Ở nhánh {@code BUYER_WINS}, người mua được hoàn 100% tiền ký quỹ và nhận thêm
 * {@code buyer_compensation} trích từ cọc bị tịch thu của người bán, theo công thức đã chốt:</p>
 * <pre>
 * founder_cut         = min(tong_coc, max(5.000d, 7% * tong_coc))
 * buyer_compensation  = tong_coc - founder_cut
 * </pre>
 *
 * <p>Ở nhánh {@code SELLER_WINS}, giao dịch được kết thúc như một giao dịch bình thường: người
 * bán nhận tiền sau khi trừ hoa hồng và thuế, đồng thời được hoàn đủ cọc.</p>
 *
 * <p><b>Ranh giới module — hai loại lời gọi khác nhau:</b></p>
 * <ul>
 *   <li><b>Phần tiền và trạng thái vé: gọi TRỰC TIẾP, ĐỒNG BỘ</b>, trong cùng transaction, qua
 *       {@link RefundBuyerUseCase}, {@link ReleaseFundsUseCase} và
 *       {@link TicketStateMachineService}. Đây là ngoại lệ đã chốt của nguyên tắc 2: phán quyết
 *       và dòng tiền phải cùng thành công hoặc cùng thất bại, không được để hồ sơ đã ghi
 *       "người mua thắng" mà tiền chưa hoàn.</li>
 *   <li><b>Phần uy tín và thông báo: đi qua EVENT</b> — phát
 *       {@link DisputeResolvedEvent} và để module {@code reputation} cùng module thông báo tự
 *       lắng nghe. Use case này KHÔNG import và KHÔNG gọi trực tiếp module {@code reputation}.</li>
 * </ul>
 *
 * <p>Event chỉ được publish sau khi phần tiền đã xử lý xong, cùng lý do như ở
 * {@link ReleaseFundsUseCase}: thông báo đã gửi đi thì không thể rollback.</p>
 *
 * <p><b>Giao dịch:</b> {@code @Transactional} sẽ được thêm ở Prompt 2. Khoá lạc quan
 * {@code @Version} trên {@code Dispute} bảo đảm hai admin không thể cùng phán quyết một hồ sơ.</p>
 *
 * <p>Module: dispute</p>
 *
 * <p>Chủ sở hữu: Người B</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewDisputeUseCase {

    /** Cổng truy cập dữ liệu khiếu nại. */
    private final DisputeRepository disputeRepository;

    /** Cổng truy cập dữ liệu bằng chứng, dùng để dựng hồ sơ cho admin xem. */
    private final DisputeEvidenceRepository disputeEvidenceRepository;

    /** Use case hoàn tiền cho người mua kèm tịch thu cọc; gọi đồng bộ ở nhánh BUYER_WINS. */
    private final RefundBuyerUseCase refundBuyerUseCase;

    /** Use case giải ngân cho người bán; gọi đồng bộ ở nhánh SELLER_WINS. */
    private final ReleaseFundsUseCase releaseFundsUseCase;

    /** Máy trạng thái vé — nơi duy nhất được phép đổi trạng thái vé. */
    private final TicketStateMachineService ticketStateMachineService;

    /** Bộ phát event, dùng để thông báo tranh chấp đã được phân xử. */
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Liệt kê các khiếu nại đang chờ admin xử lý.
     *
     * @return danh sách khiếu nại cần xem xét, sắp xếp theo mức độ cấp thiết
     */
    public List<Dispute> listPendingReview() {
        List<DisputeStatus> pendingStatuses = List.of(
                DisputeStatus.OPEN,
                DisputeStatus.AWAITING_SELLER_RESPONSE,
                DisputeStatus.SELLER_RESPONDED,
                DisputeStatus.UNDER_REVIEW,
                DisputeStatus.EXPIRED
        );

        List<Dispute> result = new ArrayList<>();
        for (DisputeStatus status : pendingStatuses) {
            List<Dispute> disputes = disputeRepository.findByStatus(status);
            if (disputes != null) {
                result.addAll(disputes);
            }
        }

        // Sắp xếp theo filedAt tăng dần (hồ sơ cũ nhất xử lý trước)
        result.sort(Comparator.comparing(Dispute::getFiledAt, Comparator.nullsLast(Comparator.naturalOrder())));
        return result;
    }

    /**
     * Admin phán quyết người mua thắng tranh chấp.
     *
     * @param disputeId      ID khiếu nại
     * @param adminId        ID admin ra phán quyết
     * @param resolutionNote ghi chú giải thích căn cứ phán quyết
     * @return hồ sơ khiếu nại sau khi đã phán quyết
     */
    @Transactional
    public Dispute resolveBuyerWins(Long disputeId, Long adminId, String resolutionNote) {
        if (disputeId == null) {
            throw new BusinessRuleViolationException("ID khiếu nại không được để trống.");
        }
        if (adminId == null) {
            throw new BusinessRuleViolationException("ID quản trị viên không được để trống.");
        }
        if (resolutionNote == null || resolutionNote.trim().isEmpty()) {
            throw new BusinessRuleViolationException("Ghi chú phán quyết của quản trị viên không được để trống.");
        }

        // (1) Tra Dispute, từ chối nếu đã có phán quyết
        Dispute dispute = disputeRepository.findById(disputeId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy khiếu nại với ID: " + disputeId));

        DisputeStatus currentStatus = dispute.getStatus();
        if (currentStatus == DisputeStatus.RESOLVED_BUYER_WINS || currentStatus == DisputeStatus.RESOLVED_SELLER_WINS) {
            throw new BusinessRuleViolationException("Khiếu nại #" + disputeId + " đã có phán quyết trước đó: " + currentStatus);
        }
        if (currentStatus == DisputeStatus.WITHDRAWN) {
            throw new BusinessRuleViolationException("Khiếu nại #" + disputeId + " đã được người mua rút đơn.");
        }

        // (2) Gọi refundBuyerUseCase.refundWithForfeitedDeposit(ticketId, resolutionNote) ĐỒNG BỘ
        // để hoàn 100% escrow cho người mua và tịch thu cọc người bán, nhận về ForfeitBreakdown
        ForfeitBreakdown breakdown = refundBuyerUseCase.refundWithForfeitedDeposit(
                dispute.getTicketId(), resolutionNote.trim());

        // (3) Lưu snapshot totalDeposit/founderCut/buyerCompensation từ ForfeitBreakdown vào Dispute,
        // gán status = RESOLVED_BUYER_WINS, outcome = DisputeOutcome.BUYER_WINS,
        // reviewedByAdminId = adminId, reviewedAt = Instant.now(), resolutionNote rồi save
        Instant now = Instant.now();
        dispute.setTotalDeposit(breakdown != null ? breakdown.getTotalDeposit() : BigDecimal.ZERO);
        dispute.setFounderCut(breakdown != null ? breakdown.getFounderCut() : BigDecimal.ZERO);
        dispute.setBuyerCompensation(breakdown != null ? breakdown.getBuyerCompensation() : BigDecimal.ZERO);
        dispute.setStatus(DisputeStatus.RESOLVED_BUYER_WINS);
        dispute.setOutcome(DisputeOutcome.BUYER_WINS);
        dispute.setReviewedByAdminId(adminId);
        dispute.setReviewedAt(now);
        dispute.setResolutionNote(resolutionNote.trim());

        Dispute savedDispute = disputeRepository.save(dispute);

        // (4) Gọi ticketStateMachineService.transition(ticketId, TicketStatus.RESOLVED_BUYER_WINS)
        ticketStateMachineService.transition(dispute.getTicketId(), TicketStatus.RESOLVED_BUYER_WINS);

        // (5) CUỐI CÙNG mới publish DisputeResolvedEvent với outcome = BUYER_WINS
        // KHÔNG gọi trực tiếp module reputation (nguyên tắc 2)
        DisputeResolvedEvent event = new DisputeResolvedEvent(
                savedDispute.getId(),
                savedDispute.getTicketId(),
                savedDispute.getEscrowTransactionId(),
                savedDispute.getDepositId(),
                savedDispute.getBuyerId(),
                savedDispute.getSellerId(),
                DisputeOutcome.BUYER_WINS,
                savedDispute.getTotalDeposit(),
                savedDispute.getFounderCut(),
                savedDispute.getBuyerCompensation(),
                adminId,
                now
        );
        eventPublisher.publishEvent(event);
        log.info("[ReviewDispute] Admin #{} phán quyết BUYER_WINS cho khiếu nại #{}", adminId, disputeId);

        return savedDispute;
    }

    /**
     * Admin phán quyết người bán thắng tranh chấp.
     *
     * @param disputeId      ID khiếu nại
     * @param adminId        ID admin ra phán quyết
     * @param resolutionNote ghi chú giải thích căn cứ phán quyết
     * @return hồ sơ khiếu nại sau khi đã phán quyết
     */
    @Transactional
    public Dispute resolveSellerWins(Long disputeId, Long adminId, String resolutionNote) {
        if (disputeId == null) {
            throw new BusinessRuleViolationException("ID khiếu nại không được để trống.");
        }
        if (adminId == null) {
            throw new BusinessRuleViolationException("ID quản trị viên không được để trống.");
        }
        if (resolutionNote == null || resolutionNote.trim().isEmpty()) {
            throw new BusinessRuleViolationException("Ghi chú phán quyết của quản trị viên không được để trống.");
        }

        // (1) Tra Dispute, từ chối nếu đã có phán quyết
        Dispute dispute = disputeRepository.findById(disputeId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy khiếu nại với ID: " + disputeId));

        DisputeStatus currentStatus = dispute.getStatus();
        if (currentStatus == DisputeStatus.RESOLVED_BUYER_WINS || currentStatus == DisputeStatus.RESOLVED_SELLER_WINS) {
            throw new BusinessRuleViolationException("Khiếu nại #" + disputeId + " đã có phán quyết trước đó: " + currentStatus);
        }
        if (currentStatus == DisputeStatus.WITHDRAWN) {
            throw new BusinessRuleViolationException("Khiếu nại #" + disputeId + " đã được người mua rút đơn.");
        }

        // (2) Gọi releaseFundsUseCase.release(ticketId, CompletionReason.DISPUTE_RESOLVED_SELLER_WINS) ĐỒNG BỘ
        // để giải ngân cho người bán và hoàn đủ cọc
        releaseFundsUseCase.release(dispute.getTicketId(), CompletionReason.DISPUTE_RESOLVED_SELLER_WINS);

        // (3) Gán status = RESOLVED_SELLER_WINS, outcome = DisputeOutcome.SELLER_WINS,
        // reviewedByAdminId = adminId, reviewedAt = Instant.now(), resolutionNote rồi save
        // (cọc không bị tịch thu nên founderCut và buyerCompensation để 0)
        Instant now = Instant.now();
        dispute.setStatus(DisputeStatus.RESOLVED_SELLER_WINS);
        dispute.setOutcome(DisputeOutcome.SELLER_WINS);
        dispute.setReviewedByAdminId(adminId);
        dispute.setReviewedAt(now);
        dispute.setResolutionNote(resolutionNote.trim());
        dispute.setFounderCut(BigDecimal.ZERO);
        dispute.setBuyerCompensation(BigDecimal.ZERO);
        if (dispute.getTotalDeposit() == null) {
            dispute.setTotalDeposit(BigDecimal.ZERO);
        }

        Dispute savedDispute = disputeRepository.save(dispute);

        // (4) Gọi ticketStateMachineService.transition(ticketId, TicketStatus.RESOLVED_SELLER_WINS)
        ticketStateMachineService.transition(dispute.getTicketId(), TicketStatus.RESOLVED_SELLER_WINS);

        // (5) CUỐI CÙNG mới publish DisputeResolvedEvent với outcome = SELLER_WINS
        // KHÔNG gọi trực tiếp module reputation (nguyên tắc 2)
        DisputeResolvedEvent event = new DisputeResolvedEvent(
                savedDispute.getId(),
                savedDispute.getTicketId(),
                savedDispute.getEscrowTransactionId(),
                savedDispute.getDepositId(),
                savedDispute.getBuyerId(),
                savedDispute.getSellerId(),
                DisputeOutcome.SELLER_WINS,
                savedDispute.getTotalDeposit(),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                adminId,
                now
        );
        eventPublisher.publishEvent(event);
        log.info("[ReviewDispute] Admin #{} phán quyết SELLER_WINS cho khiếu nại #{}", adminId, disputeId);

        return savedDispute;
    }
}
