package com.yourapp.ticketpass.admin.web.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AdminUserStatsDto {

    private final long totalUsers;
    private final long activeUsers;
    private final long lockedUsers;
    private final long warningUsers;
}
