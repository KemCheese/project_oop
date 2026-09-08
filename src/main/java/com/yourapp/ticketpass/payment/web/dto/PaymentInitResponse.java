package com.yourapp.ticketpass.payment.web.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Kết quả trả về cho giao diện sau khi khởi tạo một giao dịch thanh toán (ký quỹ hoặc cọc).
 *
 * <p>Giao diện dùng {@code payUrl} để chuyển hướng người dùng sang trang của cổng thanh toán,
 * và giữ {@code referenceId} để tra lại trạng thái giao dịch khi người dùng quay về.</p>
 *
 * <p>Module: payment</p>
 *
 * <p>Chủ sở hữu: Người B</p>
 */
@Getter
@Setter
@NoArgsConstructor
public class PaymentInitResponse {

    /** {@code true} nếu khởi tạo giao dịch thành công. */
    private boolean success;

    /** Đường dẫn trang thanh toán của cổng để chuyển hướng người dùng. */
    private String payUrl;

    /** Mã tham chiếu nội bộ của giao dịch vừa tạo. */
    private String referenceId;

    /** Thông điệp hiển thị cho người dùng, đặc biệt hữu ích khi khởi tạo thất bại. */
    private String message;
}
