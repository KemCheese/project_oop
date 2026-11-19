package com.yourapp.ticketpass.user.domain;

/**
 * Trạng thái vòng đời của một tài khoản người dùng.
 *
 * <p>
 * Chỉ tài khoản ở trạng thái {@link #ACTIVE} mới được phép đăng nhập; các trạng
 * thái
 * còn lại bị {@code UserDetailsServiceImpl} chặn ngay tại bước xác thực.
 * </p>
 *
 * <p>
 * Module: user
 * </p>
 *
 * Chủ sở hữu: Người C
 */
public enum UserStatus {

    /** Vừa đăng ký, chưa xác minh email nên chưa được đăng nhập. */
    PENDING_VERIFICATION,

    /** Tài khoản bình thường, được phép sử dụng đầy đủ chức năng. */
    ACTIVE,

    /**
     * Bị tạm khoá do vi phạm hoặc nghi ngờ gian lận, có thể được Admin mở khoá lại.
     */
    SUSPENDED,

    /**
     * Bị cấm do vi phạm nghiêm trọng; chỉ Admin mới có quyền xem xét mở khoá lại
     * (ADR-0009).
     */
    BANNED
}
