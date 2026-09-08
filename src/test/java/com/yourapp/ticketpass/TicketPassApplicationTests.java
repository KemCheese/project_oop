package com.yourapp.ticketpass;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Kiểm thử khói (smoke test): xác nhận Spring context nạp được toàn bộ bean của ứng dụng.
 *
 * <p>Test chạy trên cơ sở dữ liệu H2 in-memory, không cần MySQL. Cấu hình dùng cho test
 * nằm ở {@code src/test/resources/application.properties}: Hibernate tự sinh schema
 * ({@code ddl-auto=create-drop}) và Flyway bị tắt để các câu SQL đặc thù MySQL không
 * làm hỏng test.</p>
 *
 * <p>Nếu test này đỏ, nghĩa là có bean cấu hình sai, thiếu property, hoặc entity không
 * ánh xạ được - phải sửa trước khi chạy các test nghiệp vụ khác.</p>
 *
 * <p>Module sở hữu: root (khung ứng dụng).</p>
 * <p>Chủ sở hữu theo phân công: Người C.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class TicketPassApplicationTests {

    @Test
    void contextLoads() {
    }
}
