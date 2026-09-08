package com.yourapp.ticketpass.shared.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

/**
 * Cấu hình khấu trừ thuế tại nguồn, đọc từ tiền tố {@code app.tax} trong
 * {@code application.properties}.
 *
 * <p><b>Cảnh báo:</b> đây là bản mô phỏng (stub) phục vụ đồ án, KHÔNG phản ánh luật thuế
 * thực tế của Việt Nam. Mục đích là minh hoạ rằng hệ thống có chỗ cắm cho nghĩa vụ thuế
 * và tách bạch nó khỏi hoa hồng của sàn. Trước khi dùng thật phải thay bằng quy tắc thuế
 * do bộ phận pháp chế/kế toán cung cấp.</p>
 *
 * <p>Lớp này được đăng ký qua {@code @ConfigurationPropertiesScan} khai báo ở
 * {@code TicketPassApplication}.</p>
 *
 * <p>Module sở hữu: shared.</p>
 * <p>Chủ sở hữu theo phân công: Người C.</p>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.tax")
public class TaxConfig {

    /** Bật/tắt khâu khấu trừ thuế. Tắt thì số thuế luôn bằng 0. */
    private boolean enabled;

    /** {@code true} nghĩa là đang chạy ở chế độ mô phỏng, con số chỉ mang tính minh hoạ. */
    private boolean mock;

    /** Tỷ lệ phần trăm thuế, ví dụ {@code 1.5} nghĩa là 1,5%. */
    private BigDecimal ratePercent;

    /** Ngưỡng chịu thuế: khoản chi trả nhỏ hơn ngưỡng này thì không khấu trừ. */
    private BigDecimal taxableThreshold;
}
