package com.yourapp.ticketpass;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Điểm khởi động (entry point) của ứng dụng TicketPass - sàn giao dịch vé trung gian.
 *
 * <p>Lớp này chỉ làm nhiệm vụ bootstrap Spring Boot:</p>
 * <ul>
 *   <li>{@code @SpringBootApplication}: bật auto-configuration và quét component từ
 *       package gốc {@code com.yourapp.ticketpass} trở xuống (tất cả các module con:
 *       ticket, payment, dispute, user, reputation, shared).</li>
 *   <li>{@code @ConfigurationPropertiesScan}: đăng ký các lớp cấu hình gắn
 *       {@code @ConfigurationProperties} nằm trong {@code shared.config}
 *       (PaymentGatewayConfig, CommissionConfig, TaxConfig) mà không cần
 *       thêm {@code @EnableConfigurationProperties} thủ công cho từng lớp.</li>
 *   <li>{@code @EnableScheduling}: bật cơ chế chạy tác vụ theo lịch, cần cho
 *       {@code dispute.scheduler.AutoCompleteScheduler} (tự động hoàn tất giao dịch
 *       khi hết cửa sổ khiếu nại).</li>
 * </ul>
 *
 * <p>Module sở hữu: root (khung ứng dụng).</p>
 * <p>Chủ sở hữu theo phân công: Người C.</p>
 */
@SpringBootApplication
@ConfigurationPropertiesScan("com.yourapp.ticketpass.shared.config")
@EnableScheduling
public class TicketPassApplication {

    public static void main(String[] args) {
        SpringApplication.run(TicketPassApplication.class, args);
    }
}
