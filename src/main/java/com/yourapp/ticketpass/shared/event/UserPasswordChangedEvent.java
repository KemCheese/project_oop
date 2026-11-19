package com.yourapp.ticketpass.shared.event;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Sự kiện domain: Người dùng vừa đổi mật khẩu thành công.
 *
 * <p>Được lắng nghe ở phase {@code AFTER_COMMIT} để thu hồi mọi phiên đăng nhập cũ
 * ngoại trừ phiên hiện tại sau khi mật khẩu mới đã lưu an toàn vào CSDL.</p>
 *
 * <p>Module: shared</p>
 * <p>Chủ sở hữu theo phân công: Người C (Dev C)</p>
 */
@Getter
@AllArgsConstructor
public class UserPasswordChangedEvent {

    private final Long userId;
    private final String userEmail;
    private final String keepSessionId;
    private final Instant occurredAt;
}
