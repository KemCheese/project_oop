package com.yourapp.ticketpass.user.web.dto;

import com.yourapp.ticketpass.user.domain.UserRole;
import com.yourapp.ticketpass.user.domain.UserStatus;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Dữ liệu hồ sơ người dùng trả về cho giao diện.
 *
 * <p>DTO này <b>không</b> chứa entity của bất kỳ module nào - kể cả entity {@code User} của
 * chính module này - nên có thể trả thẳng ra view mà không kéo theo trạng thái JPA.
 * Hai trường {@code reputationScore} và {@code totalTransactions} <b>không</b> thuộc module
 * {@code user}: chúng được tầng {@code web} lắp thêm bằng cách hỏi module
 * {@code reputation} (qua {@code ReputationRepository}/{@code UpdateReputationUseCase}) rồi
 * gán vào DTO. Nhờ vậy module {@code user} không phụ thuộc ngược vào module
 * {@code reputation} ở tầng domain.</p>
 *
 * <p>{@code avatarUrl} là URL công khai dựng từ {@code avatarStorageKey} thông qua
 * {@code FileStorageService.buildPublicUrl}, không phải đường dẫn tệp trên máy chủ.</p>
 *
 * <p>Module: user</p>
 *
 * Chủ sở hữu: Người C
 */
@Getter
@Setter
@NoArgsConstructor
public class UserProfileResponse {

    private Long id;

    private String email;

    private String fullName;

    private String phoneNumber;

    private UserRole role;

    private UserStatus status;

    private boolean emailVerified;

    private boolean identityVerified;

    /** URL công khai của ảnh đại diện; null khi người dùng chưa tải ảnh lên. */
    private String avatarUrl;

    /** Điểm uy tín 0-100 lấy từ module reputation; null khi chưa có bản ghi điểm. */
    private BigDecimal reputationScore;

    /** Tổng số giao dịch lấy từ module reputation; null khi chưa có bản ghi điểm. */
    private Integer totalTransactions;

    private Instant createdAt;
}
