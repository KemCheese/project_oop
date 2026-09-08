package com.yourapp.ticketpass.user.domain;

/**
 * Vai trò của một tài khoản trong hệ thống.
 *
 * <p>Giá trị của enum này được {@code UserDetailsServiceImpl} ánh xạ sang authority của
 * Spring Security theo quy ước {@code "ROLE_" + role.name()}, tức là
 * {@code ROLE_USER} và {@code ROLE_ADMIN}. Nhờ vậy {@code shared.config.SecurityConfig}
 * có thể phân quyền bằng {@code hasRole("ADMIN")} mà không cần biết chi tiết module
 * {@code user} lưu vai trò như thế nào.</p>
 *
 * <p>Module: user</p>
 *
 * Chủ sở hữu: Người C
 */
public enum UserRole {

    /** Người dùng thông thường: đăng bán vé, mua vé, mở khiếu nại. */
    USER,

    /** Quản trị viên sàn: duyệt xác minh, xử lý tranh chấp, xem báo cáo. */
    ADMIN
}
