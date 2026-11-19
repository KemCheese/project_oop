package com.yourapp.ticketpass.admin.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.yourapp.ticketpass.admin.application.AdminUserModerationQueryService.ModerationOverview;
import com.yourapp.ticketpass.admin.web.dto.AdminUserModerationView;
import com.yourapp.ticketpass.reputation.domain.ReputationRepository;
import com.yourapp.ticketpass.reputation.domain.UserReputationScore;
import com.yourapp.ticketpass.shared.config.ModerationConfig;
import com.yourapp.ticketpass.user.domain.User;
import com.yourapp.ticketpass.user.domain.UserRepository;
import com.yourapp.ticketpass.user.domain.UserRole;
import com.yourapp.ticketpass.user.domain.UserStatus;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("Unit Test - AdminUserModerationQueryService (Batch Reputation & Risk Flagging)")
class AdminUserModerationQueryServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private ReputationRepository reputationRepository;

    private ModerationConfig moderationConfig;
    private AdminUserModerationQueryService service;

    @BeforeEach
    void setUp() {
        moderationConfig = new ModerationConfig();
        moderationConfig.setLowReputationThreshold(new BigDecimal("60.00"));
        moderationConfig.setMaxDisputesLostWarning(2);
        service = new AdminUserModerationQueryService(userRepository, reputationRepository, moderationConfig);
    }

    @Test
    @DisplayName("Gắn cờ cảnh báo khi điểm < 60 hoặc thua >= 2 tranh chấp, miễn cảnh báo cho ADMIN")
    void loadModerationOverview_ShouldFlagRiskyUsersAndBuildStats() {
        User admin = new User();
        admin.setId(1L);
        admin.setEmail("admin@ticketpass.vn");
        admin.setRole(UserRole.ADMIN);
        admin.setStatus(UserStatus.ACTIVE);

        User goodSeller = new User();
        goodSeller.setId(2L);
        goodSeller.setEmail("good@ticketpass.vn");
        goodSeller.setRole(UserRole.USER);
        goodSeller.setStatus(UserStatus.ACTIVE);

        User riskySeller = new User();
        riskySeller.setId(3L);
        riskySeller.setEmail("risk@ticketpass.vn");
        riskySeller.setRole(UserRole.USER);
        riskySeller.setStatus(UserStatus.SUSPENDED);
        riskySeller.setModerationReason("Gian lận");

        UserReputationScore goodScore = new UserReputationScore();
        goodScore.setUserId(2L);
        goodScore.setScore(new BigDecimal("95.00"));
        goodScore.setDisputesLost(0);
        goodScore.setTotalTransactions(5);

        UserReputationScore riskyScore = new UserReputationScore();
        riskyScore.setUserId(3L);
        riskyScore.setScore(new BigDecimal("48.00"));
        riskyScore.setDisputesLost(3);
        riskyScore.setTotalTransactions(4);

        when(userRepository.searchForAdmin(null, null)).thenReturn(List.of(admin, goodSeller, riskySeller));
        when(reputationRepository.findByUserIdIn(any())).thenReturn(List.of(goodScore, riskyScore));

        ModerationOverview overview = service.loadModerationOverview(null, null, true);

        assertThat(overview.stats().getTotalUsers()).isEqualTo(3);
        assertThat(overview.stats().getActiveUsers()).isEqualTo(2);
        assertThat(overview.stats().getLockedUsers()).isEqualTo(1);
        assertThat(overview.stats().getWarningUsers()).isEqualTo(1);

        assertThat(overview.users()).hasSize(1);
        AdminUserModerationView flagged = overview.users().get(0);
        assertThat(flagged.getId()).isEqualTo(3L);
        assertThat(flagged.isFlaggedRisk()).isTrue();
        assertThat(flagged.getRiskReason()).contains("60").contains("2");
    }
}

