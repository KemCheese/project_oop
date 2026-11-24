package com.yourapp.ticketpass.reputation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.yourapp.ticketpass.reputation.domain.ReputationRepository;
import com.yourapp.ticketpass.reputation.domain.ReputationTier;
import com.yourapp.ticketpass.reputation.domain.ReputationTierPolicy;
import com.yourapp.ticketpass.reputation.domain.UserReputationScore;
import com.yourapp.ticketpass.shared.exception.BusinessRuleViolationException;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReputationPolicyServiceTest - Strategy Pattern 6 bậc uy tín (0-5) & kiểm tra biên hạn mức")
class ReputationPolicyServiceTest {

    @Mock
    private ReputationRepository reputationRepository;

    private ReputationPolicyService policyService;

    @BeforeEach
    void setUp() {
        policyService = ReputationPolicyService.withDefaultPolicies(reputationRepository);
    }

    private void stubUserScore(Long userId, String score) {
        UserReputationScore rep = new UserReputationScore();
        rep.setUserId(userId);
        rep.setScore(new BigDecimal(score));
        when(reputationRepository.findByUserId(userId)).thenReturn(Optional.of(rep));
    }

    @ParameterizedTest(name = "Điểm {0} -> Bậc {1}, giá {2} đ -> allowed={3}")
    @CsvSource({
            "5.00, EXCELLENT, 1,          true",
            "5.00, EXCELLENT, 9999999,    true",
            "5.00, EXCELLENT, 10000000,   true",
            "5.00, EXCELLENT, 50000000,   true",
            "4.00, GOOD,      9999999,    true",
            "4.00, GOOD,      10000000,   false",
            "4.00, GOOD,      12000000,   false",
            "3.00, NORMAL,    5999999,    true",
            "3.00, NORMAL,    6000000,    false",
            "3.00, NORMAL,    8500000,    false",
            "2.00, WARNING,   999999,     true",
            "2.00, WARNING,   1000000,    false",
            "2.00, WARNING,   1500000,    false",
            "1.00, DANGER,    99999,      true",
            "1.00, DANGER,    100000,     false",
            "1.00, DANGER,    250000,     false",
            "0.00, BANNED,    1,          false",
            "0.00, BANNED,    50000,      false"
    })
    @DisplayName("Kiểm tra biên hạn mức tuyệt đối cho cả 6 bậc uy tín (0..5)")
    void shouldEnforceExactTransactionLimitBoundaries(String scoreStr,
                                                      ReputationTier expectedTier,
                                                      long priceVnd,
                                                      boolean expectedAllowed) {
        Long userId = 10L;
        stubUserScore(userId, scoreStr);

        ReputationTierPolicy resolved = policyService.resolvePolicyForUser(userId);
        assertThat(resolved.tier()).isEqualTo(expectedTier);
        assertThat(resolved.isAllowed(BigDecimal.valueOf(priceVnd))).isEqualTo(expectedAllowed);

        if (expectedAllowed) {
            assertThatCode(() -> policyService.assertCanTransact(userId, BigDecimal.valueOf(priceVnd), "mua vé"))
                    .doesNotThrowAnyException();
        } else {
            assertThatThrownBy(() -> policyService.assertCanTransact(userId, BigDecimal.valueOf(priceVnd), "mua vé"))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining(expectedTier.getDisplayName());
        }
    }

    @Test
    @DisplayName("Người dùng chưa có bản ghi uy tín -> mặc định bậc Bình thường (điểm 3, < 6tr)")
    void shouldFallbackToNormalTierWhenReputationMissing() {
        when(reputationRepository.findByUserId(99L)).thenReturn(Optional.empty());

        ReputationTierPolicy policy = policyService.resolvePolicyForUser(99L);
        assertThat(policy.tier()).isEqualTo(ReputationTier.NORMAL);

        assertThatCode(() -> policyService.assertCanTransact(99L, new BigDecimal("5999999"), "đăng bán vé"))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> policyService.assertCanTransact(99L, new BigDecimal("6000000"), "đăng bán vé"))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("đăng bán vé")
                .hasMessageContaining("Bình thường");
    }

    @Test
    @DisplayName("Chuẩn hóa điểm ngoài biên (< 0 -> BANNED, > 5 -> EXCELLENT) và từ chối giá bán <= 0")
    void shouldNormalizeOutOfRangeScoresAndRejectInvalidPrice() {
        assertThat(policyService.resolvePolicy(new BigDecimal("-3.00")).tier()).isEqualTo(ReputationTier.BANNED);
        assertThat(policyService.resolvePolicy(new BigDecimal("100.00")).tier()).isEqualTo(ReputationTier.EXCELLENT);

        assertThatThrownBy(() -> policyService.assertCanTransact(1L, BigDecimal.ZERO, "mua vé"))
                .isInstanceOf(BusinessRuleViolationException.class);
        assertThatThrownBy(() -> policyService.assertCanTransact(1L, new BigDecimal("-1000"), "mua vé"))
                .isInstanceOf(BusinessRuleViolationException.class);
        assertThatThrownBy(() -> policyService.assertCanTransact(null, new BigDecimal("50000"), "mua vé"))
                .isInstanceOf(BusinessRuleViolationException.class);
    }
}
