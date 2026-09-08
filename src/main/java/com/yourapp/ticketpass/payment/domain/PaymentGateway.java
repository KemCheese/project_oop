package com.yourapp.ticketpass.payment.domain;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Hợp đồng trừu tượng với cổng thanh toán — áp dụng Adapter pattern.
 *
 * <p>Mọi use case trong {@code payment.application} chỉ phụ thuộc vào interface này, không
 * bao giờ gọi trực tiếp SDK hay HTTP API của một nhà cung cấp cụ thể. Nhờ vậy, ở Prompt 2
 * muốn hỗ trợ thêm VNPay hay ZaloPay thì chỉ cần viết adapter mới implement
 * {@code PaymentGateway} trong {@code payment.adapter}, KHÔNG phải sửa bất kỳ dòng nào
 * trong các use case ({@code HoldFundsUseCase}, {@code LockDepositUseCase},
 * {@code ReleaseFundsUseCase}, {@code RefundBuyerUseCase}).</p>
 *
 * <p>Mọi khoá bí mật và endpoint đều được nạp từ
 * {@code com.yourapp.ticketpass.shared.config.PaymentGatewayConfig}, không hardcode
 * trong adapter.</p>
 *
 * <p>Module: payment</p>
 *
 * <p>Chủ sở hữu: Người B</p>
 */
public interface PaymentGateway {

    /**
     * Tên định danh của cổng thanh toán, ví dụ {@code "MOMO_TEST"}.
     *
     * <p>Giá trị này được lưu vào {@code EscrowTransaction.gatewayName} và
     * {@code Deposit.gatewayName} để biết giao dịch đã đi qua cổng nào.</p>
     *
     * @return tên cổng thanh toán
     */
    String gatewayName();

    /**
     * Khởi tạo một giao dịch thu tiền và lấy về đường dẫn để chuyển hướng người dùng.
     *
     * @param request thông tin yêu cầu thanh toán
     * @return kết quả có {@code payUrl} để chuyển hướng người dùng sang cổng thanh toán
     */
    PaymentResult initPayment(PaymentRequest request);

    /**
     * Xác thực dữ liệu callback/IPN nhận được từ cổng thanh toán.
     *
     * <p>Bắt buộc phải kiểm tra chữ ký (signature) trước khi tin vào nội dung, vì endpoint
     * IPN là công khai và có thể bị giả mạo.</p>
     *
     * @param callbackParams toàn bộ tham số cổng thanh toán gửi tới
     * @return kết quả đã xác thực; {@code success = false} nếu chữ ký không hợp lệ
     */
    PaymentResult verifyCallback(Map<String, String> callbackParams);

    /**
     * Thực hiện thu tiền (capture) cho một giao dịch đã được cấp quyền trước đó.
     *
     * @param gatewayTransactionId mã giao dịch của cổng thanh toán
     * @param amount               số tiền cần thu
     * @return kết quả thao tác capture
     */
    PaymentResult capture(String gatewayTransactionId, BigDecimal amount);

    /**
     * Hoàn tiền toàn phần hoặc một phần cho một giao dịch đã thu.
     *
     * @param gatewayTransactionId mã giao dịch cần hoàn
     * @param amount               số tiền hoàn
     * @param reason               lý do hoàn tiền, dùng để đối soát
     * @return kết quả thao tác hoàn tiền
     */
    PaymentResult refund(String gatewayTransactionId, BigDecimal amount, String reason);

    /**
     * Chi trả tiền từ tài khoản sàn về cho người nhận (giải ngân cho người bán, hoàn cọc,
     * bồi thường cho người mua).
     *
     * @param recipientUserId ID người dùng nhận tiền
     * @param amount          số tiền chi trả
     * @param reference       mã tham chiếu nội bộ để đối soát khoản chi
     * @return kết quả thao tác chi trả
     */
    PaymentResult payout(Long recipientUserId, BigDecimal amount, String reference);
}
