package com.yourapp.ticketpass.reputation.application;

import com.yourapp.ticketpass.reputation.domain.ReputationRepository;
import com.yourapp.ticketpass.reputation.domain.ReputationTier;
import com.yourapp.ticketpass.reputation.domain.ReputationTierPolicy;
import com.yourapp.ticketpass.reputation.domain.UserReputationScore;
import com.yourapp.ticketpass.reputation.domain.policy.BannedReputationPolicy;
import com.yourapp.ticketpass.reputation.domain.policy.DangerReputationPolicy;
import com.yourapp.ticketpass.reputation.domain.policy.ExcellentReputationPolicy;
import com.yourapp.ticketpass.reputation.domain.policy.GoodReputationPolicy;
import com.yourapp.ticketpass.reputation.domain.policy.NormalReputationPolicy;
import com.yourapp.ticketpass.reputation.domain.policy.WarningReputationPolicy;
import com.yourapp.ticketpass.shared.exception.BusinessRuleViolationException;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReputationPolicyService {

    private final ReputationRepository reputationRepository;
    private final List<ReputationTierPolicy> policies;

    public ReputationPolicyService(ReputationRepository reputationRepository,
                                   List<ReputationTierPolicy> policies) {
        this.reputationRepository = reputationRepository;
        this.policies = (policies != null && !policies.isEmpty()) ? List.copyOf(policies) : defaultPolicies();
    }

    public static ReputationPolicyService withDefaultPolicies(ReputationRepository reputationRepository) {
        return new ReputationPolicyService(reputationRepository, defaultPolicies());
    }

    public static List<ReputationTierPolicy> defaultPolicies() {
        return List.of(
                new ExcellentReputationPolicy(),
                new GoodReputationPolicy(),
                new NormalReputationPolicy(),
                new WarningReputationPolicy(),
                new DangerReputationPolicy(),
                new BannedReputationPolicy()
        );
    }

    public ReputationTierPolicy resolvePolicy(BigDecimal score) {
        int normalized = ReputationTier.normalizeScore(score);
        return policies.stream()
                .filter(policy -> policy.supports(normalized))
                .findFirst()
                .orElseGet(BannedReputationPolicy::new);
    }

    @Transactional(readOnly = true)
    public ReputationTierPolicy resolvePolicyForUser(Long userId) {
        if (userId == null) {
            throw new BusinessRuleViolationException("Thiếu thông tin người dùng để kiểm tra hạn mức uy tín.");
        }
        BigDecimal score = reputationRepository.findByUserId(userId)
                .map(UserReputationScore::getScore)
                .orElse(BigDecimal.valueOf(ReputationTier.INITIAL_SCORE_INT));
        return resolvePolicy(score);
    }

    @Transactional(readOnly = true)
    public void assertCanTransact(Long userId, BigDecimal salePrice, String actionLabel) {
        if (userId == null) {
            throw new BusinessRuleViolationException("Thiếu thông tin người dùng để kiểm tra hạn mức uy tín.");
        }
        if (salePrice == null || salePrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessRuleViolationException("Giá bán vé phải lớn hơn 0.");
        }
        String label = (actionLabel != null && !actionLabel.isBlank()) ? actionLabel : "giao dịch vé";
        ReputationTierPolicy policy = resolvePolicyForUser(userId);
        if (!policy.isAllowed(salePrice)) {
            throw new BusinessRuleViolationException(policy.buildRejectionMessage(salePrice, label));
        }
    }
}
