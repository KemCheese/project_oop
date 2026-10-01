package com.yourapp.ticketpass.payment.application;

import java.math.BigDecimal;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.yourapp.ticketpass.payment.domain.Deposit;
import com.yourapp.ticketpass.payment.domain.DepositRepository;
import com.yourapp.ticketpass.payment.domain.DepositStatus;
import com.yourapp.ticketpass.payment.domain.EscrowStatus;
import com.yourapp.ticketpass.payment.domain.EscrowTransaction;
import com.yourapp.ticketpass.payment.domain.EscrowTransactionRepository;
import com.yourapp.ticketpass.payment.domain.PaymentGateway;
import com.yourapp.ticketpass.payment.domain.PaymentResult;
import com.yourapp.ticketpass.shared.dto.ForfeitBreakdown;
import com.yourapp.ticketpass.shared.exception.BusinessRuleViolationException;
import com.yourapp.ticketpass.shared.exception.ResourceNotFoundException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

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
 * <p><b>Giao dịch:</b> {@code @Transactional} được thêm — hoàn escrow và tịch thu
 * cọc phải cùng thành công hoặc cùng thất bại, không được hoàn tiền mà cọc vẫn còn khoá.</p>
 *
 * <p>Module: payment</p>
 *
 * <p>Chủ sở hữu: Người B</p>
 */
@Slf4j
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
     * @return bản ghi giao dịch ký quỹ sau khi chuyển sang {@code REFUNDED}
     */
    @Transactional
    public EscrowTransaction refundFull(Long ticketId, String reason) {
        // Tra escrow theo ticketId
        EscrowTransaction tx = escrowTransactionRepository.findByTicketId(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy giao dịch ký quỹ cho vé #" + ticketId));

        // Idempotent: nếu đã REFUNDED thì thoát ngay
        if (tx.getStatus() == EscrowStatus.REFUNDED) {
            log.info("[RefundBuyer] Escrow id={} đã ở REFUNDED, bỏ qua gọi trùng.", tx.getId());
            return tx;
        }

        // Chỉ cho phép hoàn tiền khi escrow ở trạng thái HELD
        if (tx.getStatus() != EscrowStatus.HELD) {
            throw new BusinessRuleViolationException(
                    "Giao dịch ký quỹ #" + tx.getId() + " đang ở trạng thái " + tx.getStatus()
                    + ", không thể hoàn tiền. Chỉ cho phép hoàn tiền khi status = HELD.");
        }

        // Gọi gateway hoàn tiền
        BigDecimal amountToRefund = tx.getAmountHeld();
        PaymentResult refundResult = paymentGateway.refund(
                tx.getGatewayTransactionId(), amountToRefund, reason);
        if (refundResult == null || !refundResult.isSuccess()) {
            String msg = (refundResult != null && refundResult.getMessage() != null)
                    ? refundResult.getMessage() : "Cổng thanh toán không phản hồi hoặc trả về lỗi";
            throw new BusinessRuleViolationException(
                    "Hoàn tiền thất bại (escrowId=" + tx.getId() + "): " + msg);
        }

        // Cập nhật escrow: status = REFUNDED, amountRefunded = amountHeld, amountHeld = 0
        tx.setAmountRefunded(amountToRefund);
        tx.setAmountHeld(BigDecimal.ZERO);
        tx.setStatus(EscrowStatus.REFUNDED);
        tx.setRefundedAt(Instant.now());
        tx = escrowTransactionRepository.save(tx);
        log.info("[RefundBuyer] Escrow id={} REFUNDED, amountRefunded={}", tx.getId(), tx.getAmountRefunded());
        return tx;
    }

    /**
     * Hoàn 100% tiền ký quỹ cho người mua và tịch thu cọc của người bán để bồi thường thêm.
     *
     * <p>Trình tự thực hiện:
     * (1) Tra Deposit theo ticketId, xác nhận status = LOCKED (fail-fast trước khi gọi gateway hoàn tiền).
     * (2) Gọi {@link #refundFull} hoàn 100% tiền ký quỹ.
     * (3) Gọi {@link ForfeitDepositPolicy#computeForfeit} để tính chia cọc.
     * (4) Chi {@code buyerCompensation} cho người mua qua {@link PaymentGateway#payout}
     *     (bỏ qua nếu buyerCompensation = 0 do cọc quá nhỏ).
     * (5) Cập nhật Deposit: status = FORFEITED, lưu snapshot founderCut/buyerCompensation,
     *     forfeitReason, forfeitedAt.
     * (6) Trả về ForfeitBreakdown.</p>
     *
     * @param ticketId ID vé của giao dịch
     * @param reason   lý do tịch thu cọc, thường là ghi chú phán quyết tranh chấp của admin
     * @return chi tiết chia cọc bị tịch thu, để bên gọi lưu snapshot và phát event
     */
    @Transactional
    public ForfeitBreakdown refundWithForfeitedDeposit(Long ticketId, String reason) {
        // (1) Fail-fast: Tra cứu và kiểm tra Deposit trước khi gọi Gateway hoàn tiền để chống lệch pha DB vs Gateway
        Deposit deposit = depositRepository.findByTicketId(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy khoản cọc cho vé #" + ticketId));

        // Idempotent: nếu cọc đã FORFEITED thì trả về breakdown từ snapshot đã lưu
        if (deposit.getStatus() == DepositStatus.FORFEITED) {
            log.info("[RefundBuyer] Deposit id={} đã ở FORFEITED, trả về breakdown từ snapshot.", deposit.getId());
            return new ForfeitBreakdown(deposit.getAmount(), deposit.getFounderCut(), deposit.getBuyerCompensation());
        }

        // Chỉ cho phép tịch thu khi cọc đang ở trạng thái LOCKED
        if (deposit.getStatus() != DepositStatus.LOCKED) {
            throw new BusinessRuleViolationException(
                    "Khoản cọc #" + deposit.getId() + " đang ở trạng thái " + deposit.getStatus()
                    + ", không thể tịch thu. Chỉ cho phép tịch thu khi status = LOCKED.");
        }

        // (2) Hoàn 100% tiền ký quỹ cho người mua (nhận về tx đã cập nhật)
        EscrowTransaction tx = refundFull(ticketId, reason);

        // (3) Tính cách chia cọc bị tịch thu
        ForfeitBreakdown breakdown = forfeitDepositPolicy.computeForfeit(deposit.getAmount());

        // (4) Chi buyer_compensation cho người mua (nếu > 0)
        BigDecimal buyerCompensation = breakdown.getBuyerCompensation();
        if (buyerCompensation.compareTo(BigDecimal.ZERO) > 0) {
            Long buyerId = tx.getBuyerId();

            String payoutRef = "FORFEIT-COMPENSATION-" + deposit.getId();
            PaymentResult payoutResult = paymentGateway.payout(buyerId, buyerCompensation, payoutRef);
            if (payoutResult == null || !payoutResult.isSuccess()) {
                String msg = (payoutResult != null && payoutResult.getMessage() != null)
                        ? payoutResult.getMessage() : "Cổng thanh toán không phản hồi hoặc trả về lỗi";
                throw new BusinessRuleViolationException(
                        "Chi bồi thường cho người mua thất bại (depositId=" + deposit.getId()
                        + "): " + msg);
            }
            log.info("[RefundBuyer] Chi bồi thường {} cho buyerId={}", buyerCompensation, buyerId);
        } else {
            log.info("[RefundBuyer] buyerCompensation = 0 (cọc quá nhỏ), bỏ qua payout.");
        }

        // (5) Cập nhật Deposit: FORFEITED
        deposit.setStatus(DepositStatus.FORFEITED);
        deposit.setFounderCut(breakdown.getFounderCut());
        deposit.setBuyerCompensation(buyerCompensation);
        deposit.setForfeitReason(reason);
        deposit.setForfeitedAt(Instant.now());
        depositRepository.save(deposit);
        log.info("[RefundBuyer] Deposit id={} FORFEITED, founderCut={}, buyerCompensation={}",
                deposit.getId(), breakdown.getFounderCut(), buyerCompensation);

        // (6) Trả về ForfeitBreakdown
        return breakdown;
    }
}
