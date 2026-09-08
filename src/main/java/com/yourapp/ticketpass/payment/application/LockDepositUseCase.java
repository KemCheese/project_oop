package com.yourapp.ticketpass.payment.application;

import java.math.BigDecimal;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.yourapp.ticketpass.payment.domain.Deposit;
import com.yourapp.ticketpass.payment.domain.DepositRepository;
import com.yourapp.ticketpass.payment.domain.PaymentGateway;
import com.yourapp.ticketpass.payment.domain.PaymentResult;
import com.yourapp.ticketpass.ticket.application.TransferTicketService;

import lombok.RequiredArgsConstructor;

/**
 * Use case khoá tiền cọc của người bán.
 *
 * <p><b>Quy tắc đã chốt:</b> tiền cọc bằng <b>50% giá bán</b>, tỷ lệ này đọc từ
 * {@code app.deposit.percent} chứ không hardcode. Cọc là khoản bảo đảm để nếu người bán giao
 * vé không hợp lệ hoặc không chuyển vé, sàn có nguồn tiền bồi thường cho người mua mà không
 * phải chờ đòi lại từ người bán.</p>
 *
 * <p><b>Luồng hai bước:</b> {@link #initDepositPayment} tạo bản ghi {@link Deposit} ở
 * {@code PENDING} và lấy {@code payUrl}; {@link #confirmLocked} xử lý callback/IPN, chuyển bản
 * ghi sang {@code LOCKED} và đẩy vé sang {@code DEPOSIT_LOCKED}.</p>
 *
 * <p><b>Giao dịch:</b> {@code @Transactional} sẽ được thêm ở Prompt 2 cho
 * {@link #confirmLocked}.</p>
 *
 * <p><b>Ngoại lệ của nguyên tắc 2 (đã chốt):</b> use case này gọi
 * {@code transferTicketService.markDepositLocked(...)} TRỰC TIẾP và ĐỒNG BỘ trong cùng
 * transaction vì đây là luồng tiền, không được để trạng thái vé lệch với trạng thái cọc.</p>
 *
 * <p>Module: payment</p>
 *
 * <p>Chủ sở hữu: Người B</p>
 */
@Service
@RequiredArgsConstructor
public class LockDepositUseCase {

    /** Cổng truy cập dữ liệu khoản cọc. */
    private final DepositRepository depositRepository;

    /** Cổng thanh toán, được tiêm qua abstraction nên không phụ thuộc nhà cung cấp cụ thể. */
    private final PaymentGateway paymentGateway;

    /** Dịch vụ chuyển trạng thái vé của module ticket; gọi đồng bộ trong cùng transaction. */
    private final TransferTicketService transferTicketService;

    /**
     * Khởi tạo giao dịch khoá cọc và lấy đường dẫn thanh toán cho người bán.
     *
     * @param ticketId  ID vé cần bảo đảm bằng cọc
     * @param sellerId  ID người bán phải khoá cọc
     * @param salePrice giá bán vé, dùng làm căn cứ tính 50% tiền cọc
     * @return kết quả có {@code payUrl} để chuyển hướng người bán sang cổng thanh toán
     */
    public PaymentResult initDepositPayment(Long ticketId, Long sellerId, BigDecimal salePrice) {
        throw new UnsupportedOperationException("TODO [Người B] Prompt 2: (1) kiểm tra vé chưa có cọc đang hiệu lực "
                + "qua depositRepository.findByTicketId; "
                + "(2) đọc tỷ lệ cọc từ app.deposit.percent (mặc định 50) và tính "
                + "amount = salePrice * depositPercent / 100, làm tròn HALF_UP tới 2 chữ số thập phân; "
                + "(3) tạo Deposit với status = PENDING, depositPercent, amount, "
                + "gatewayName = paymentGateway.gatewayName() rồi save để lấy id làm referenceId; "
                + "(4) dựng PaymentRequest (referenceId, payerUserId = sellerId, amount, orderInfo, returnUrl, ipnUrl) "
                + "và gọi paymentGateway.initPayment; "
                + "(5) nếu cổng trả thất bại thì cập nhật status = FAILED; trả về PaymentResult có payUrl.");
    }

    /**
     * Xác nhận cọc đã bị khoá thành công, dựa trên callback/IPN của cổng thanh toán.
     *
     * @param gatewayTransactionId mã giao dịch của cổng thanh toán
     * @param callbackParams       toàn bộ tham số cổng thanh toán gửi tới, dùng để xác thực chữ ký
     * @return bản ghi cọc sau khi đã chuyển sang trạng thái {@code LOCKED}
     */
    public Deposit confirmLocked(String gatewayTransactionId, Map<String, String> callbackParams) {
        throw new UnsupportedOperationException("TODO [Người B] Prompt 2: (1) gọi paymentGateway.verifyCallback("
                + "callbackParams) và từ chối ngay nếu chữ ký không hợp lệ; "
                + "(2) tra bản ghi Deposit theo referenceId trong callback, "
                + "ném ResourceNotFoundException nếu không có; "
                + "(3) nếu bản ghi đã ở LOCKED thì trả về nguyên trạng để bảo đảm idempotent với IPN gửi lặp; "
                + "(4) cập nhật status = LOCKED, lockedAt = Instant.now(), gatewayTransactionId rồi save; "
                + "(5) gọi transferTicketService.markDepositLocked(ticketId, deposit.getId()) ĐỒNG BỘ "
                + "trong cùng transaction theo ngoại lệ đã chốt của nguyên tắc 2. "
                + "Nhớ thêm @Transactional cho method này.");
    }
}
