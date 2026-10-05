package com.yourapp.ticketpass.shared.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cấu hình thông tin chuyển khoản trung gian, đọc từ tiền tố {@code app.payment.manual-transfer}
 * trong {@code application.properties}.
 *
 * <p>Hệ thống sử dụng cơ chế chuyển khoản thủ công qua QR: người dùng quét mã QR của tài khoản
 * trung gian (sàn), chuyển khoản qua ngân hàng, sau đó nhấn nút xác nhận trên giao diện.
 * Admin sẽ kiểm tra thủ công khoản chuyển khoản thật sự đã vào tài khoản hay chưa.</p>
 *
 * <p>Lớp này được đăng ký qua {@code @ConfigurationPropertiesScan} khai báo ở
 * {@code TicketPassApplication}.</p>
 *
 * <p>Module sở hữu: shared.</p>
 * <p>Chủ sở hữu theo phân công: Người B.</p>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.payment.manual-transfer")
public class PaymentGatewayConfig {

    /** Tên định danh cổng thanh toán, ví dụ {@code MANUAL_TRANSFER}. */
    private String gatewayName;

    /** Tên chủ tài khoản trung gian nhận tiền (hiển thị trên trang QR). */
    private String accountHolder;

    /** Số tài khoản trung gian nhận tiền. */
    private String accountNumber;

    /** Tên ngân hàng của tài khoản trung gian. */
    private String bankName;

    /** Đường dẫn tới ảnh QR code chuyển khoản (tương đối hoặc tuyệt đối). */
    private String qrImageUrl;

    /** Tiền tố nội dung chuyển khoản, hệ thống sẽ ghép thêm mã giao dịch vào sau. */
    private String transferNotePrefix;
}
