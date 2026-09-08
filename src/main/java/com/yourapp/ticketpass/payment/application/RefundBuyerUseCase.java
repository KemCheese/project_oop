package com.yourapp.ticketpass.payment.application;

import org.springframework.stereotype.Service;

import com.yourapp.ticketpass.payment.domain.DepositRepository;
import com.yourapp.ticketpass.payment.domain.EscrowTransactionRepository;
import com.yourapp.ticketpass.payment.domain.PaymentGateway;
import com.yourapp.ticketpass.shared.dto.ForfeitBreakdown;

import lombok.RequiredArgsConstructor;

/**
 * Use case hoàn tiền cho người mua — dùng khi <b>người mua THẮNG tranh chấp</b> hoặc khi giao
 * dịch bị huỷ trước lúc chuyển vé.
 *
 * <p><b>Hai nhánh xử lý:</b></p>
 * <ul>
 *   <li>{@link #refundFull} — chỉ hoàn 100% tiền ký quỹ cho người mua, không tịch thu cọc.
 *       Dùng khi giao dịch bị huỷ mà người bán không có lỗi.</li>
 *   <li>{@link #refundWithForfeitedDeposit} — người mua thắng tranh chấp: hoàn 100% tiền ký quỹ
 *       cho người mua, ĐỒNG THỜI tịch thu cọc của người bán theo {@link ForfeitDepositPolicy}
 *       rồi cộng thêm phần {@code buyer_compensation} cho người mua.</li>
 * </ul>
 *
 * <p><b>Công thức chia cọc bị tịch thu (đã chốt):</b></p>
 * <pre>
 * founder_cut         = min(tong_coc, max(5.000d, 7% * tong_coc))
 * buyer_compensation  = tong_coc - founder_cut
 * </pre>
 *
 * <p>Nghĩa là khi thắng tranh chấp, người mua nhận về tổng cộng
 * {@code salePrice + buyer_compensation}: vừa lấy lại đủ tiền đã nộp, vừa được bồi thường từ
 * cọc của người bán. Phần {@code founder_cut} là chi phí sàn giữ lại cho công tác xử lý
 * tranh chấp.</p>
 *
 * <p><b>Giao dịch:</b> {@code @Transactional} sẽ được thêm ở Prompt 2 — hoàn escrow và tịch thu
 * cọc phải cùng thành công hoặc cùng thất bại, không được hoàn tiền mà cọc vẫn còn khoá.</p>
 *
 * <p>Module: payment</p>
 *
 * <p>Chủ sở hữu: Người B</p>
 */
@Service
@RequiredArgsConstructor
public class RefundBuyerUseCase {

    /** Cổng truy cập dữ liệu giao dịch ký quỹ. */
    private final EscrowTransactionRepository escrowTransactionRepository;

    /** Cổng truy cập dữ liệu khoản cọc. */
    private final DepositRepository depositRepository;

    /** Chính sách chia cọc bị tịch thu, giữ nguyên công thức đã chốt kèm chặn trần. */
    private final ForfeitDepositPolicy forfeitDepositPolicy;

    /** Cổng thanh toán để thực hiện hoàn tiền và chi bồi thường. */
    private final PaymentGateway paymentGateway;

    /**
     * Hoàn 100% tiền ký quỹ cho người mua, không tác động tới cọc của người bán.
     *
     * @param ticketId ID vé của giao dịch
     * @param reason   lý do hoàn tiền, dùng để đối soát và hiển thị cho người dùng
     */
    public void refundFull(Long ticketId, String reason) {
        throw new UnsupportedOperationException("TODO [Người B] Prompt 2: tra escrow theo ticketId, "
                + "xác nhận status = HELD (nếu đã REFUNDED thì thoát để idempotent); "
                + "gọi paymentGateway.refund(gatewayTransactionId, amountHeld, reason); "
                + "cập nhật status = REFUNDED, amountRefunded = amountHeld, amountHeld = 0, "
                + "refundedAt = Instant.now() rồi save. Nhớ thêm @Transactional.");
    }

    /**
     * Hoàn 100% tiền ký quỹ cho người mua và tịch thu cọc của người bán để bồi thường thêm.
     *
     * @param ticketId ID vé của giao dịch
     * @param reason   lý do tịch thu cọc, thường là ghi chú phán quyết tranh chấp của admin
     * @return chi tiết chia cọc bị tịch thu, để bên gọi lưu snapshot và phát event
     */
    public ForfeitBreakdown refundWithForfeitedDeposit(Long ticketId, String reason) {
        throw new UnsupportedOperationException("TODO [Người B] Prompt 2: (1) gọi refundFull(ticketId, reason) "
                + "để hoàn 100% tiền ký quỹ cho người mua; "
                + "(2) tra Deposit theo ticketId, xác nhận status = LOCKED; "
                + "(3) gọi forfeitDepositPolicy.computeForfeit(deposit.getAmount()) để lấy ForfeitBreakdown; "
                + "(4) chi buyer_compensation cho người mua qua paymentGateway.payout(buyerId, buyerCompensation, reference) "
                + "— bỏ qua bước chi nếu buyerCompensation bằng 0 do cọc quá nhỏ; "
                + "(5) cập nhật Deposit: status = FORFEITED, founderCut, buyerCompensation, "
                + "forfeitReason = reason, forfeitedAt = Instant.now() rồi save; "
                + "(6) trả về ForfeitBreakdown. Nhớ thêm @Transactional.");
    }
}
