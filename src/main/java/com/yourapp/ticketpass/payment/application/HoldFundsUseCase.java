package com.yourapp.ticketpass.payment.application;

import java.math.BigDecimal;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.yourapp.ticketpass.payment.domain.EscrowTransaction;
import com.yourapp.ticketpass.payment.domain.EscrowTransactionRepository;
import com.yourapp.ticketpass.payment.domain.PaymentGateway;
import com.yourapp.ticketpass.payment.domain.PaymentResult;
import com.yourapp.ticketpass.ticket.application.TransferTicketService;

import lombok.RequiredArgsConstructor;

/**
 * Use case giữ tiền của người mua vào ký quỹ: người mua nộp 100% giá bán, sàn giữ hộ
 * cho tới khi giao dịch hoàn tất hoặc tranh chấp được xử lý.
 *
 * <p><b>Luồng hai bước bất đồng bộ với cổng thanh toán:</b></p>
 * <ol>
 *   <li>{@link #initEscrowPayment} tạo bản ghi {@link EscrowTransaction} ở trạng thái
 *       {@code PENDING} và lấy {@code payUrl} để chuyển hướng người mua.</li>
 *   <li>{@link #confirmHeld} được gọi khi cổng thanh toán báo về (IPN), chuyển bản ghi sang
 *       {@code HELD} và đẩy vé sang {@code PAYMENT_ESCROWED}.</li>
 * </ol>
 *
 * <p><b>Giao dịch:</b> annotation {@code @Transactional} sẽ được thêm ở Prompt 2 cho
 * {@link #confirmHeld} — bước này ghi cả bản ghi escrow và trạng thái vé nên hai thay đổi
 * phải cùng thành công hoặc cùng thất bại.</p>
 *
 * <p><b>Ngoại lệ của nguyên tắc 2 (đã chốt):</b> use case này gọi
 * {@code transferTicketService.markPaymentEscrowed(...)} TRỰC TIẾP và ĐỒNG BỘ trong cùng
 * transaction, không đi qua event. Lý do: đây là luồng tiền — không thể có tình huống tiền đã
 * bị giữ mà trạng thái vé chưa cập nhật, vì khoảng trống đó cho phép vé được bán cho người
 * khác. Chỉ các side-effect không thuộc luồng tiền (uy tín, thông báo) mới đi qua event.</p>
 *
 * <p><b>Idempotent:</b> cổng thanh toán có thể gửi IPN nhiều lần cho cùng một giao dịch, nên
 * {@link #confirmHeld} phải nhận ra bản ghi đã ở {@code HELD} và trả về nguyên trạng thay vì
 * xử lý lại.</p>
 *
 * <p>Module: payment</p>
 *
 * <p>Chủ sở hữu: Người B</p>
 */
@Service
@RequiredArgsConstructor
public class HoldFundsUseCase {

    /** Cổng truy cập dữ liệu giao dịch ký quỹ. */
    private final EscrowTransactionRepository escrowTransactionRepository;

    /** Cổng thanh toán, được tiêm qua abstraction nên không phụ thuộc nhà cung cấp cụ thể. */
    private final PaymentGateway paymentGateway;

    /** Dịch vụ tính hoa hồng, dùng để lưu snapshot phần tiền ngay khi tạo escrow. */
    private final CommissionCalculatorService commissionCalculatorService;

    /** Dịch vụ chuyển trạng thái vé của module ticket; gọi đồng bộ trong cùng transaction. */
    private final TransferTicketService transferTicketService;

    /**
     * Khởi tạo giao dịch ký quỹ và lấy đường dẫn thanh toán cho người mua.
     *
     * @param ticketId  ID vé được mua
     * @param buyerId   ID người mua
     * @param sellerId  ID người bán
     * @param salePrice giá bán vé, cũng là số tiền người mua phải nộp (100% giá bán)
     * @return kết quả có {@code payUrl} để chuyển hướng người mua sang cổng thanh toán
     */
    public PaymentResult initEscrowPayment(Long ticketId, Long buyerId, Long sellerId, BigDecimal salePrice) {
        throw new UnsupportedOperationException("TODO [Người B] Prompt 2: (1) kiểm tra vé chưa có escrow đang hiệu "
                + "lực qua escrowTransactionRepository.findByTicketId và buyerId != sellerId; "
                + "(2) gọi commissionCalculatorService.calculate(salePrice) để lưu snapshot "
                + "buyerCommission/sellerCommission/totalCommission/tierCode; "
                + "(3) tạo EscrowTransaction với status = PENDING, amountHeld = 0, gatewayName = paymentGateway.gatewayName() "
                + "rồi save để lấy id làm referenceId; "
                + "(4) dựng PaymentRequest (referenceId, payerUserId = buyerId, amount = salePrice, orderInfo, returnUrl, ipnUrl) "
                + "và gọi paymentGateway.initPayment; "
                + "(5) nếu cổng trả thất bại thì cập nhật status = FAILED kèm failureReason; "
                + "trả về PaymentResult có payUrl.");
    }

    /**
     * Xác nhận tiền đã được giữ thành công, dựa trên callback/IPN của cổng thanh toán.
     *
     * @param gatewayTransactionId mã giao dịch của cổng thanh toán
     * @param callbackParams       toàn bộ tham số cổng thanh toán gửi tới, dùng để xác thực chữ ký
     * @return bản ghi ký quỹ sau khi đã chuyển sang trạng thái {@code HELD}
     */
    public EscrowTransaction confirmHeld(String gatewayTransactionId, Map<String, String> callbackParams) {
        throw new UnsupportedOperationException("TODO [Người B] Prompt 2: (1) gọi paymentGateway.verifyCallback("
                + "callbackParams) và từ chối ngay nếu chữ ký không hợp lệ; "
                + "(2) tra bản ghi qua escrowTransactionRepository.findByGatewayTransactionId hoặc theo referenceId "
                + "trong callback, ném ResourceNotFoundException nếu không có; "
                + "(3) nếu bản ghi đã ở HELD thì trả về nguyên trạng để bảo đảm idempotent với IPN gửi lặp; "
                + "(4) cập nhật status = HELD, amountHeld = salePrice, heldAt = Instant.now(), "
                + "gatewayTransactionId rồi save; "
                + "(5) gọi transferTicketService.markPaymentEscrowed(ticketId, buyerId, tx.getId()) ĐỒNG BỘ "
                + "trong cùng transaction theo ngoại lệ đã chốt của nguyên tắc 2. "
                + "Nhớ thêm @Transactional cho method này.");
    }
}
