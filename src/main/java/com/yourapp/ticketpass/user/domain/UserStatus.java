package com.yourapp.ticketpass.user.domain;

/**
 * Trạng thái vòng đời của một tài khoản người dùng.
 *
 * <p>Chỉ tài khoản ở trạng thái {@link #ACTIVE} mới được phép đăng nhập; các trạng thái
 * còn lại bị {@code UserDetailsServiceImpl} chặn ngay tại bước xác thực.</p>
 *
 * <p>Module: user</p>
 *
 * Chủ sở hữu: Người C
 */
public enum UserStatus {

    /** Vừa đăng ký, chưa xác minh email nên chưa được đăng nhập. */
    PENDING_VERIFICATION,

    /** Tài khoản bình thường, được phép sử dụng đầy đủ chức năng. */
    ACTIVE,

    /** Bị tạm khoá có thời hạn do vi phạm, có thể khôi phục. */
    SUSPENDED,

    /** Bị cấm vĩnh viễn, không khôi phục. */
    BANNED
}
