package com.yourapp.ticketpass.shared.event;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Sự kiện domain: Admin thực hiện kiểm duyệt trạng thái tài khoản người dùng
 * (tạm khóa, cấm vĩnh viễn hoặc kích hoạt lại).
 *
 * <p>Sử dụng {@code targetStatusName} kiểu {@link String} (ví dụ {@code "ACTIVE"},
 * {@code "SUSPENDED"}, {@code "BANNED"}) thay vì enum của module {@code user} để giữ
 * module {@code shared} hoàn toàn độc lập với domain entity.</p>
 *
 * <p>Module: shared</p>
 * <p>Chủ sở hữu theo phân công: Người C (Dev C)</p>
 */
@Getter
@AllArgsConstructor
public class UserAccountModeratedEvent {

    private final Long userId;
    private final String userEmail;
    private final Long adminId;
    private final String targetStatusName;
    private final String reason;
    private final Instant occurredAt;
}
