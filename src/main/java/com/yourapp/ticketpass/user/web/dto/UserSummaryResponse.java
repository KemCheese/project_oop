package com.yourapp.ticketpass.user.web.dto;

import com.yourapp.ticketpass.user.domain.UserRole;
import com.yourapp.ticketpass.user.domain.UserStatus;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class UserSummaryResponse {

    private Long id;

    private String email;

    private String fullName;

    private UserRole role;

    private UserStatus status;

    private BigDecimal reputationScore;
}
