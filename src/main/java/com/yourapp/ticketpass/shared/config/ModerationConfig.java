package com.yourapp.ticketpass.shared.config;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Cấu hình ngưỡng cảnh báo rủi ro và kiểm duyệt tài khoản, đọc từ tiền tố
 * {@code app.moderation} trong {@code application.properties}.
 *
 * <p>Lớp này được đăng ký tự động qua {@code @ConfigurationPropertiesScan} khai báo ở
 * {@code TicketPassApplication}.</p>
 *
 * <p>Module sở hữu: shared.</p>
 * <p>Chủ sở hữu theo phân công: Người C.</p>
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app.moderation")
public class ModerationConfig {

    /**
     * Ngưỡng điểm uy tín thấp: tài khoản có điểm nhỏ hơn ngưỡng này
     * sẽ bị gắn cờ cảnh báo trên màn hình quản trị.
     */
    @DecimalMin(value = "0.00", message = "Ngưỡng cảnh báo uy tín tối thiểu phải >= 0.00")
    private BigDecimal lowReputationThreshold = new BigDecimal("60.00");

    /**
     * Số lần thua tranh chấp từ ngưỡng này trở lên sẽ bị gắn cờ cảnh báo rủi ro.
     */
    @Min(value = 1, message = "Ngưỡng cảnh báo số lần thua tranh chấp tối thiểu phải >= 1")
    private int maxDisputesLostWarning = 2;
}
