package com.yourapp.ticketpass.user.web.dto;

import com.yourapp.ticketpass.user.domain.UserRole;
import com.yourapp.ticketpass.user.domain.UserStatus;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Bản rút gọn của hồ sơ người dùng, dùng cho danh sách và bảng quản trị.
 *
 * <p>Chỉ giữ những trường cần hiển thị trên một dòng danh sách để tránh tải thừa dữ liệu
 * nhạy cảm (số tài khoản ngân hàng, số điện thoại) ra màn hình chung. Giống
 * {@code UserProfileResponse}, trường {@code reputationScore} do tầng {@code web} lắp thêm
 * từ module {@code reputation}.</p>
 *
 * <p>Module: user</p>
 *
 * Chủ sở hữu: Người C
 */
@Getter
@Setter
@NoArgsConstructor
public class UserSummaryResponse {

    private Long id;

    private String email;

    private String fullName;

    private UserRole role;

    private UserStatus status;

    /** Điểm uy tín 0-100 lấy từ module reputation; null khi chưa có bản ghi điểm. */
    private BigDecimal reputationScore;
}
