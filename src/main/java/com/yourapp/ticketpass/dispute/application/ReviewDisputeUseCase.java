package com.yourapp.ticketpass.dispute.application;

import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import com.yourapp.ticketpass.dispute.domain.Dispute;
import com.yourapp.ticketpass.dispute.domain.DisputeEvidenceRepository;
import com.yourapp.ticketpass.dispute.domain.DisputeRepository;
import com.yourapp.ticketpass.payment.application.RefundBuyerUseCase;
import com.yourapp.ticketpass.payment.application.ReleaseFundsUseCase;
import com.yourapp.ticketpass.shared.event.DisputeResolvedEvent;
import com.yourapp.ticketpass.ticket.application.TicketStateMachineService;

import lombok.RequiredArgsConstructor;

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
        throw new UnsupportedOperationException("TODO [Người B] Prompt 2: gộp danh sách khiếu nại ở các trạng thái "
                + "OPEN, AWAITING_SELLER_RESPONSE, SELLER_RESPONDED, UNDER_REVIEW và EXPIRED "
                + "bằng disputeRepository.findByStatus, sắp xếp theo filedAt tăng dần "
                + "(hồ sơ cũ nhất xử lý trước) và trả về danh sách hợp nhất.");
    }

    /**
     * Admin phán quyết người mua thắng tranh chấp.
     *
     * @param disputeId      ID khiếu nại
     * @param adminId        ID admin ra phán quyết
     * @param resolutionNote ghi chú giải thích căn cứ phán quyết
     * @return hồ sơ khiếu nại sau khi đã phán quyết
     */
    public Dispute resolveBuyerWins(Long disputeId, Long adminId, String resolutionNote) {
        throw new UnsupportedOperationException("TODO [Người B] Prompt 2: theo đúng trình tự đã chốt — "
                + "(1) tra Dispute, từ chối nếu đã có phán quyết; "
                + "(2) gọi refundBuyerUseCase.refundWithForfeitedDeposit(ticketId, resolutionNote) ĐỒNG BỘ "
                + "để hoàn 100% escrow cho người mua và tịch thu cọc người bán, nhận về ForfeitBreakdown; "
                + "(3) lưu snapshot totalDeposit/founderCut/buyerCompensation từ ForfeitBreakdown vào Dispute, "
                + "gán status = RESOLVED_BUYER_WINS, outcome = DisputeOutcome.BUYER_WINS, "
                + "reviewedByAdminId = adminId, reviewedAt = Instant.now(), resolutionNote rồi save; "
                + "(4) gọi ticketStateMachineService.transition(ticketId, TicketStatus.RESOLVED_BUYER_WINS); "
                + "(5) CUỐI CÙNG mới publish DisputeResolvedEvent với outcome = BUYER_WINS. "
                + "Nhớ thêm @Transactional và KHÔNG gọi trực tiếp module reputation.");
    }

    /**
     * Admin phán quyết người bán thắng tranh chấp.
     *
     * @param disputeId      ID khiếu nại
     * @param adminId        ID admin ra phán quyết
     * @param resolutionNote ghi chú giải thích căn cứ phán quyết
     * @return hồ sơ khiếu nại sau khi đã phán quyết
     */
    public Dispute resolveSellerWins(Long disputeId, Long adminId, String resolutionNote) {
        throw new UnsupportedOperationException("TODO [Người B] Prompt 2: theo đúng trình tự đã chốt — "
                + "(1) tra Dispute, từ chối nếu đã có phán quyết; "
                + "(2) gọi releaseFundsUseCase.release(ticketId, CompletionReason.DISPUTE_RESOLVED_SELLER_WINS) "
                + "ĐỒNG BỘ để giải ngân cho người bán và hoàn đủ cọc; "
                + "(3) gán status = RESOLVED_SELLER_WINS, outcome = DisputeOutcome.SELLER_WINS, "
                + "reviewedByAdminId = adminId, reviewedAt = Instant.now(), resolutionNote rồi save "
                + "(cọc không bị tịch thu nên founderCut và buyerCompensation để 0); "
                + "(4) gọi ticketStateMachineService.transition(ticketId, TicketStatus.RESOLVED_SELLER_WINS); "
                + "(5) CUỐI CÙNG mới publish DisputeResolvedEvent với outcome = SELLER_WINS. "
                + "Nhớ thêm @Transactional và KHÔNG gọi trực tiếp module reputation.");
    }
}
