package com.yourapp.ticketpass.payment.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Kết quả trả về từ cổng thanh toán — đối tượng đầu ra trung lập của {@link PaymentGateway}.
 *
 * <p>Một kiểu kết quả duy nhất được dùng cho tất cả thao tác của cổng (khởi tạo, xác thực
 * callback, capture, hoàn tiền, chi trả) để các use case chỉ phải xử lý một dạng phản hồi.</p>
 *
 * <p><b>Lưu ý:</b> {@code payUrl} CHỈ có giá trị khi kết quả được trả về từ
 * {@link PaymentGateway#initPayment(PaymentRequest)}. Với các thao tác khác
 * ({@code verifyCallback}, {@code capture}, {@code refund}, {@code payout}) trường này
 * luôn là {@code null} vì không có bước chuyển hướng người dùng.</p>
 *
 * <p>Module: payment</p>
 *
 * <p>Chủ sở hữu: Người B</p>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResult {

    /** {@code true} nếu cổng thanh toán báo thao tác thành công. */
    private boolean success;

    /** Mã giao dịch do cổng thanh toán cấp, dùng để đối soát và hoàn tiền về sau. */
    private String gatewayTransactionId;

    /** Mã/nhãn trạng thái nguyên bản của cổng thanh toán, giữ lại để phục vụ đối soát. */
    private String gatewayStatus;

    /** URL để chuyển hướng người dùng sang trang thanh toán; chỉ có giá trị khi trả về từ {@code initPayment}. */
    private String payUrl;

    /** Thông điệp mô tả kết quả, dùng để hiển thị hoặc ghi log lý do thất bại. */
    private String message;

    /** Payload gốc của cổng thanh toán (thường là JSON), giữ nguyên để phục vụ tra soát tranh chấp. */
    private String rawPayload;
}
