package com.yourapp.ticketpass.admin.web.dto;

import com.yourapp.ticketpass.user.domain.UserRole;
import com.yourapp.ticketpass.user.domain.UserStatus;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AdminUserModerationView {

    private final Long id;
    private final String email;
    private final String fullName;
    private final String phoneNumber;
    private final UserRole role;
    private final UserStatus status;
    private final boolean emailVerified;

    private final BigDecimal reputationScore;
    private final int reputationScoreInt;
    private final String reputationTierName;
    private final String reputationTierColor;
    private final int totalTransactions;
    private final int disputesLost;
    private final int cancelledListings;

    private final boolean permanentlyBanned;
    private final boolean flaggedRisk;
    private final String riskReason;

    private final String moderationReason;
    private final Instant moderatedAt;
    private final Long moderatedByAdminId;
    private final Instant createdAt;
}
