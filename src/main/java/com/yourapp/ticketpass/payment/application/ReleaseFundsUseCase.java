package com.yourapp.ticketpass.payment.application;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import com.yourapp.ticketpass.payment.domain.DepositRepository;
import com.yourapp.ticketpass.payment.domain.EscrowTransactionRepository;
import com.yourapp.ticketpass.payment.domain.PaymentGateway;
import com.yourapp.ticketpass.shared.event.CompletionReason;
import com.yourapp.ticketpass.shared.event.TicketTransactionCompletedEvent;

import lombok.RequiredArgsConstructor;

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
 * <p><b>Giao dịch:</b> {@code @Transactional} sẽ được thêm ở Prompt 2. Event nên được phát
 * bằng {@code @TransactionalEventListener} ở phía người nghe để chỉ chạy sau khi commit
 * thành công.</p>
 *
 * <p>Module: payment</p>
 *
 * <p>Chủ sở hữu: Người B</p>
 */
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

    /**
     * Giải ngân toàn bộ phần tiền của một giao dịch đã hoàn tất.
     *
     * @param ticketId ID vé của giao dịch
     * @param reason   lý do hoàn tất: hết cửa sổ khiếu nại, người mua xác nhận, hoặc người bán
     *                 thắng tranh chấp
     */
    public void release(Long ticketId, CompletionReason reason) {
        throw new UnsupportedOperationException("TODO [Người B] Prompt 2: thực hiện đúng trình tự đã chốt — "
                + "(1) tra escrow theo ticketId, xác nhận status = HELD (nếu đã RELEASED thì thoát để idempotent); "
                + "(2) tính hoa hồng bằng commissionCalculatorService.calculate(salePrice); "
                + "(3) khấu thuế bằng taxWithholdingService.withhold(salePrice - sellerCommission, sellerId); "
                + "(4) payout cho người bán số sellerNetPayout qua paymentGateway.payout, "
                + "lưu snapshot taxWithheld/sellerNetPayout, đặt status = RELEASED và releasedAt = Instant.now(); "
                + "(5) hoàn cọc cho người bán bằng refundSellerDeposit(ticketId); "
                + "(6) đổi trạng thái vé sang trạng thái kết thúc tương ứng với reason; "
                + "(7) CUỐI CÙNG mới publish TicketTransactionCompletedEvent với đầy đủ số liệu tiền. "
                + "Nhớ thêm @Transactional và KHÔNG gọi trực tiếp module reputation.");
    }

    /**
     * Hoàn cọc cho người bán khi giao dịch kết thúc mà người bán không có lỗi.
     *
     * @param ticketId ID vé của giao dịch
     */
    public void refundSellerDeposit(Long ticketId) {
        throw new UnsupportedOperationException("TODO [Người B] Prompt 2: tra Deposit theo ticketId, "
                + "xác nhận status = LOCKED (nếu đã REFUNDED thì thoát để idempotent, "
                + "nếu đã FORFEITED thì ném BusinessRuleViolationException vì cọc đã bị tịch thu); "
                + "gọi paymentGateway.payout(sellerId, amount, reference) rồi cập nhật "
                + "status = REFUNDED và refundedAt = Instant.now() và save.");
    }
}
