package com.yourapp.ticketpass.payment.domain;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Yêu cầu thanh toán gửi tới cổng thanh toán — đối tượng đầu vào trung lập của
 * {@link PaymentGateway}.
 *
 * <p>Đây là DTO thuần, không phải entity. Nhờ có lớp trung lập này, các use case trong
 * {@code payment.application} không cần biết cổng thanh toán cụ thể yêu cầu tham số gì:
 * việc chuyển đổi sang định dạng riêng của cổng thanh toán hoặc cơ chế chuyển khoản QR
 * thuộc trách nhiệm của adapter tương ứng.</p>
 *
 * <p>Module: payment</p>
 *
 * <p>Chủ sở hữu: Người B</p>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PaymentRequest {

    /** Mã tham chiếu nội bộ của sàn cho giao dịch này; cổng thanh toán sẽ trả lại nguyên văn. */
    private String referenceId;

    /** ID người dùng thực hiện trả tiền: người mua khi nộp escrow, người bán khi khoá cọc. */
    private Long payerUserId;

    /** Số tiền cần thu. */
    private BigDecimal amount;

    /** Nội dung giao dịch hiển thị cho người dùng trên trang của cổng thanh toán. */
    private String orderInfo;

    /** URL trình duyệt người dùng sẽ được chuyển về sau khi thao tác xong trên cổng. */
    private String returnUrl;

    /** URL nhận thông báo server-to-server (IPN) từ cổng thanh toán. */
    private String ipnUrl;

    /** Dữ liệu bổ sung đính kèm giao dịch, cổng thanh toán trả lại nguyên văn trong callback. */
    private String extraData;
}
