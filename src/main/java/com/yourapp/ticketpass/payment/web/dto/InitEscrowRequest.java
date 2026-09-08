package com.yourapp.ticketpass.payment.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Dữ liệu người mua gửi lên khi bắt đầu thanh toán vào ký quỹ.
 *
 * <p>ID người mua KHÔNG nằm trong DTO này: nó được lấy từ phiên đăng nhập ở controller.
 * Nếu nhận {@code buyerId} từ phía người dùng thì một người có thể đặt tên người khác để
 * thanh toán, đây là lỗ hổng bảo mật.</p>
 *
 * <p>Module: payment</p>
 *
 * <p>Chủ sở hữu: Người B</p>
 */
@Getter
@Setter
@NoArgsConstructor
public class InitEscrowRequest {

    /** ID vé mà người mua muốn mua. */
    @NotNull
    private Long ticketId;

    /** Phương thức thanh toán người mua chọn, dùng để chọn adapter cổng thanh toán tương ứng. */
    @NotBlank
    private String paymentMethod;
}
