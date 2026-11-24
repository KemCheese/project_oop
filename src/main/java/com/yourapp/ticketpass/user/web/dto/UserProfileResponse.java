package com.yourapp.ticketpass.user.web.dto;

import com.yourapp.ticketpass.user.domain.UserRole;
import com.yourapp.ticketpass.user.domain.UserStatus;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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

    private boolean phoneVerified;

    private boolean identityVerified;

    private String avatarUrl;

    private String bankName;

    private String bankAccountNumber;

    private String bankAccountHolder;

    private BigDecimal reputationScore;

    private String reputationTierName;

    private String reputationTierColor;

    private String reputationLimitDescription;

    private Integer totalTransactions;

    private Instant createdAt;
}
