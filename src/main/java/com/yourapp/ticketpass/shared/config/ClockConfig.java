package com.yourapp.ticketpass.shared.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Cấu hình {@link Clock} hệ thống phục vụ tiêm phụ thuộc thời gian và kiểm thử time-travel.
 *
 * <p>Module: shared</p>
 * <p>Chủ sở hữu: Người C (Dev C)</p>
 */
@Configuration
public class ClockConfig {

    /**
     * Cung cấp đồng hồ chuẩn theo múi giờ mặc định của hệ thống.
     *
     * @return thể hiện {@link Clock} dùng chung toàn ứng dụng
     */
    @Bean
    public Clock systemClock() {
        return Clock.systemDefaultZone();
    }
}
