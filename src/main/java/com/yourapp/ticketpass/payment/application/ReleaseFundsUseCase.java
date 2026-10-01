package com.yourapp.ticketpass.payment.application;

import java.math.BigDecimal;
import java.time.Instant;

import org.springframework.context.ApplicationEventPublisher;
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
import com.yourapp.ticketpass.shared.dto.CommissionBreakdown;
import com.yourapp.ticketpass.shared.dto.TaxWithholding;
import com.yourapp.ticketpass.shared.event.CompletionReason;
import com.yourapp.ticketpass.shared.event.TicketTransactionCompletedEvent;
import com.yourapp.ticketpass.shared.exception.BusinessRuleViolationException;
import com.yourapp.ticketpass.shared.exception.ResourceNotFoundException;
import com.yourapp.ticketpass.ticket.application.TicketStateMachineService;
import com.yourapp.ticketpass.ticket.domain.TicketStatus;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Use case giải ngân: kết thúc phần tiền của một giao dịch thành công — trả tiền cho người bán
 * và hoàn cọc cho người bán.
 *
 * <p><b>TRÌNH TỰ BẮT BUỘC của {@link #release} (không được đổi thứ tự):</b></p>
 * <ol>
 *   <li><b>Tính hoa hồng</b> — {@code commissionCalculatorService.calculate(salePrice)} để biết
 *       phần sàn thu và phần người bán chịu.</li>
 *   <li><b>Khấu thuế (mock)</b> — {@code taxWithholdingService.withhold(sellerGross, sellerId)}
 *       trên khoản người bán còn lại sau hoa hồng.</li>
 *   <li><b>Payout cho người bán</b> — {@code paymentGateway.payout(...)} với số tiền
 *       {@code sellerNetPayout}, rồi cập nhật escrow sang {@code RELEASED}.</li>
 *   <li><b>Hoàn cọc cho người bán</b> — {@code paymentGateway.payout(...)} khoản cọc, rồi cập
 *       nhật {@code Deposit} sang {@code REFUNDED}.</li>
 *   <li><b>Đổi trạng thái vé</b> — đưa vé về trạng thái kết thúc tương ứng với
 *       {@link CompletionReason}.</li>
 *   <li><b>Phát event</b> — {@code eventPublisher.publishEvent(new TicketTransactionCompletedEvent(...))}.</li>
 * </ol>
 *
 * <p><b>Event chỉ được publish SAU KHI toàn bộ phần tiền đã xong.</b> Nếu phát event sớm rồi
 * bước payout thất bại, các module nghe event sẽ đã cộng uy tín và gửi thông báo "giao dịch
 * thành công" cho một giao dịch thực tế chưa hoàn tất — sai lệch này không thể sửa bằng
 * rollback vì thông báo đã gửi đi.</p>
 *
 * <p><b>Ranh giới module (nguyên tắc 2):</b> mọi side-effect ngoài luồng tiền — cộng điểm uy
 * tín, gửi thông báo, ghi thống kê — đều đi qua {@link TicketTransactionCompletedEvent}.
 * Use case này KHÔNG import và KHÔNG gọi trực tiếp module {@code reputation} hay module thông
 * báo. Chỉ riêng phần tiền và trạng thái vé mới được gọi đồng bộ, theo ngoại lệ đã chốt.</p>
 *
 * <p><b>Giao dịch:</b> {@code @Transactional} được thêm cho {@link #release}. Event nên được phát
 * bằng {@code @TransactionalEventListener} ở phía người nghe để chỉ chạy sau khi commit
 * thành công.</p>
 *
 * <p>Module: payment</p>
 *
 * <p>Chủ sở hữu: Người B</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReleaseFundsUseCase {

    /** Cổng truy cập dữ liệu giao dịch ký quỹ. */
    private final EscrowTransactionRepository escrowTransactionRepository;

    /** Cổng truy cập dữ liệu khoản cọc. */
    private final DepositRepository depositRepository;

    /** Dịch vụ tính hoa hồng — bước 1 của trình tự giải ngân. */
    private final CommissionCalculatorService commissionCalculatorService;

    /** Dịch vụ khấu thuế (stub) — bước 2 của trình tự giải ngân. */
    private final TaxWithholdingService taxWithholdingService;

    /** Cổng thanh toán để thực hiện chi trả và hoàn cọc. */
    private final PaymentGateway paymentGateway;

    /** Bộ phát event trong tiến trình, dùng để thông báo giao dịch đã hoàn tất. */
    private final ApplicationEventPublisher eventPublisher;

    /** Dịch vụ chuyển trạng thái vé — bước 5 của trình tự giải ngân. */
    private final TicketStateMachineService stateMachineService;

    /**
     * Giải ngân toàn bộ phần tiền của một giao dịch đã hoàn tất.
     *
     * <p><b>Trình tự bắt buộc (6 bước):</b>
     * (1) Tra escrow theo ticketId, xác nhận status = HELD (nếu đã RELEASED thì thoát để idempotent).
     * (2) Tính hoa hồng bằng commissionCalculatorService.calculate(salePrice).
     * (3) Khấu thuế bằng taxWithholdingService.withhold(sellerGross, sellerId).
     * (4) Payout cho người bán số sellerNetPayout qua paymentGateway.payout,
     *     lưu snapshot taxWithheld/sellerNetPayout, đặt status = RELEASED và releasedAt.
     *     Sau đó hoàn cọc cho người bán bằng refundSellerDeposit(ticketId).
     * (5) Đổi trạng thái vé sang trạng thái kết thúc tương ứng với reason.
     * (6) CUỐI CÙNG mới publish TicketTransactionCompletedEvent với đầy đủ số liệu tiền.</p>
     *
     * @param ticketId ID vé của giao dịch
     * @param reason   lý do hoàn tất: hết cửa sổ khiếu nại, người mua xác nhận, hoặc người bán
     *                 thắng tranh chấp
     */
    @Transactional
    public void release(Long ticketId, CompletionReason reason) {
        // ── Bước 0: Tra escrow theo ticketId ──────────────────────────────────────
        EscrowTransaction tx = escrowTransactionRepository.findByTicketId(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy giao dịch ký quỹ cho vé #" + ticketId));

        // Idempotent: nếu đã RELEASED thì thoát ngay
        if (tx.getStatus() == EscrowStatus.RELEASED) {
            log.info("[ReleaseFunds] Escrow id={} đã ở RELEASED, bỏ qua gọi trùng.", tx.getId());
            return;
        }

        // Chỉ cho phép giải ngân khi escrow ở trạng thái HELD
        if (tx.getStatus() != EscrowStatus.HELD) {
            throw new BusinessRuleViolationException(
                    "Giao dịch ký quỹ #" + tx.getId() + " đang ở trạng thái " + tx.getStatus()
                    + ", không thể giải ngân. Chỉ cho phép giải ngân khi status = HELD.");
        }

        BigDecimal salePrice = tx.getSalePrice();
        Long sellerId = tx.getSellerId();
        Long buyerId = tx.getBuyerId();

        // ── Bước 1: Tính hoa hồng ────────────────────────────────────────────────
        CommissionBreakdown commission = commissionCalculatorService.calculate(salePrice);
        BigDecimal sellerCommission = commission.getSellerShare();
        BigDecimal buyerCommission = commission.getBuyerShare();

        // ── Bước 2: Khấu thuế (mock) ─────────────────────────────────────────────
        // sellerGross = salePrice - sellerCommission (phần thu gộp của người bán sau hoa hồng)
        BigDecimal sellerGross = salePrice.subtract(sellerCommission);
        TaxWithholding tax = taxWithholdingService.withhold(sellerGross, sellerId);
        BigDecimal taxWithheld = tax.getTaxAmount();
        BigDecimal sellerNetPayout = tax.getNetAmount();

        // ── Bước 3: Payout cho người bán ──────────────────────────────────────────
        String payoutRef = "RELEASE-ESCROW-" + tx.getId();
        PaymentResult payoutResult = paymentGateway.payout(sellerId, sellerNetPayout, payoutRef);
        if (payoutResult == null || !payoutResult.isSuccess()) {
            String msg = (payoutResult != null && payoutResult.getMessage() != null)
                    ? payoutResult.getMessage() : "Cổng thanh toán không phản hồi hoặc trả về lỗi";
            throw new BusinessRuleViolationException(
                    "Payout cho người bán thất bại (escrowId=" + tx.getId() + "): " + msg);
        }

        // Lưu snapshot và cập nhật trạng thái escrow
        tx.setSellerCommission(sellerCommission);
        tx.setBuyerCommission(buyerCommission);
        tx.setTotalCommission(commission.getTotalCommission());
        tx.setTaxWithheld(taxWithheld);
        tx.setSellerNetPayout(sellerNetPayout);
        tx.setStatus(EscrowStatus.RELEASED);
        tx.setReleasedAt(Instant.now());
        tx = escrowTransactionRepository.save(tx);
        log.info("[ReleaseFunds] Escrow id={} RELEASED, sellerNetPayout={}, taxWithheld={}",
                tx.getId(), sellerNetPayout, taxWithheld);

        // ── Bước 4: Hoàn cọc cho người bán ───────────────────────────────────────
        BigDecimal depositRefunded = BigDecimal.ZERO;
        Deposit deposit = null;
        try {
            deposit = refundSellerDeposit(ticketId);
            depositRefunded = deposit.getAmount();
        } catch (ResourceNotFoundException e) {
            // Có thể chưa có deposit (ví dụ flow chưa đến bước khoá cọc) — ghi log nhưng không dừng
            log.warn("[ReleaseFunds] Không tìm thấy deposit cho vé #{}, bỏ qua hoàn cọc.", ticketId);
        }

        // ── Bước 5: Đổi trạng thái vé ────────────────────────────────────────────
        TicketStatus targetStatus = mapReasonToTicketStatus(reason);
        stateMachineService.transition(ticketId, targetStatus);
        log.info("[ReleaseFunds] Vé #{} chuyển sang {}", ticketId, targetStatus);

        // ── Bước 6: CUỐI CÙNG mới publish event ──────────────────────────────────
        TicketTransactionCompletedEvent event = new TicketTransactionCompletedEvent(
                ticketId,
                tx.getId(),
                deposit != null ? deposit.getId() : null,
                sellerId,
                buyerId,
                salePrice,
                buyerCommission,
                sellerCommission,
                taxWithheld,
                sellerNetPayout,
                depositRefunded,
                reason,
                Instant.now());
        eventPublisher.publishEvent(event);
        log.info("[ReleaseFunds] Publish TicketTransactionCompletedEvent cho vé #{}, reason={}", ticketId, reason);
    }

    /**
     * Hoàn cọc cho người bán khi giao dịch kết thúc mà người bán không có lỗi.
     *
     * @param ticketId ID vé của giao dịch
     * @return bản ghi cọc sau khi cập nhật sang {@code REFUNDED}
     */
    public Deposit refundSellerDeposit(Long ticketId) {
        Deposit deposit = depositRepository.findByTicketId(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy khoản cọc cho vé #" + ticketId));

        // Idempotent: nếu đã REFUNDED thì trả về ngay
        if (deposit.getStatus() == DepositStatus.REFUNDED) {
            log.info("[ReleaseFunds] Deposit id={} đã ở REFUNDED, bỏ qua hoàn cọc trùng.", deposit.getId());
            return deposit;
        }

        // Cọc đã bị tịch thu: không được hoàn
        if (deposit.getStatus() == DepositStatus.FORFEITED) {
            throw new BusinessRuleViolationException(
                    "Khoản cọc #" + deposit.getId() + " đã bị tịch thu, không thể hoàn lại.");
        }

        // Chỉ cho phép hoàn khi status = LOCKED
        if (deposit.getStatus() != DepositStatus.LOCKED) {
            throw new BusinessRuleViolationException(
                    "Khoản cọc #" + deposit.getId() + " đang ở trạng thái " + deposit.getStatus()
                    + ", không thể hoàn. Chỉ cho phép hoàn khi status = LOCKED.");
        }

        // Gọi gateway để chi trả cọc
        String refundRef = "REFUND-DEPOSIT-" + deposit.getId();
        PaymentResult payoutResult = paymentGateway.payout(
                deposit.getSellerId(), deposit.getAmount(), refundRef);
        if (payoutResult == null || !payoutResult.isSuccess()) {
            String msg = (payoutResult != null && payoutResult.getMessage() != null)
                    ? payoutResult.getMessage() : "Cổng thanh toán không phản hồi hoặc trả về lỗi";
            throw new BusinessRuleViolationException(
                    "Hoàn cọc thất bại (depositId=" + deposit.getId() + "): " + msg);
        }

        deposit.setStatus(DepositStatus.REFUNDED);
        deposit.setRefundedAt(Instant.now());
        deposit = depositRepository.save(deposit);
        log.info("[ReleaseFunds] Deposit id={} REFUNDED, amount={}", deposit.getId(), deposit.getAmount());

        return deposit;
    }

    /**
     * Map {@link CompletionReason} sang {@link TicketStatus} tương ứng.
     */
    private TicketStatus mapReasonToTicketStatus(CompletionReason reason) {
        return switch (reason) {
            case AUTO_COMPLETE, BUYER_CONFIRMED -> TicketStatus.AUTO_COMPLETE;
            case DISPUTE_RESOLVED_SELLER_WINS -> TicketStatus.RESOLVED_SELLER_WINS;
        };
    }
}
