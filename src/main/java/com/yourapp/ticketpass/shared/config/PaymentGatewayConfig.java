package com.yourapp.ticketpass.shared.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cấu hình kết nối tới cổng thanh toán MoMo, đọc từ tiền tố {@code app.payment.momo}
 * trong {@code application.properties}.
 *
 * <p>Toàn bộ thông số môi trường thử nghiệm (endpoint, khoá đối tác, URL trả về, URL nhận
 * thông báo IPN) được tách ra file cấu hình để không hardcode khoá bí mật trong mã nguồn
 * và để đổi sang môi trường thật chỉ bằng cách thay giá trị.</p>
 *
 * <p>Lớp này được đăng ký qua {@code @ConfigurationPropertiesScan} khai báo ở
 * {@code TicketPassApplication}.</p>
 *
 * <p>Module sở hữu: shared.</p>
 * <p>Chủ sở hữu theo phân công: Người C.</p>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.payment.momo")
public class PaymentGatewayConfig {

    /** Tên định danh cổng thanh toán, ví dụ {@code MOMO_TEST}. */
    private String gatewayName;

    /** Địa chỉ API tạo yêu cầu thanh toán. */
    private String endpoint;

    /** Mã đối tác do MoMo cấp. */
    private String partnerCode;

    /** Khoá truy cập do MoMo cấp. */
    private String accessKey;

    /** Khoá bí mật dùng để ký chữ ký HMAC của yêu cầu. Không được commit giá trị thật. */
    private String secretKey;

    /** URL trình duyệt người dùng được chuyển về sau khi thanh toán xong. */
    private String returnUrl;

    /** URL để MoMo gọi ngược lại máy chủ báo kết quả (IPN). */
    private String ipnUrl;

    /** Thời gian chờ tối đa khi gọi API cổng thanh toán, tính bằng giây. */
    private int requestTimeoutSeconds;
}
