package com.yourapp.ticketpass.admin.web.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * Thống kê tổng quan các thẻ đầu trang cho màn hình quản trị người dùng {@code /admin/users}.
 *
 * <p>Module: admin</p>
 * <p>Chủ sở hữu: Người C (Dev C)</p>
 */
@Getter
@Builder
public class AdminUserStatsDto {

    private final long totalUsers;
    private final long activeUsers;
    private final long lockedUsers;
    private final long warningUsers;
}

