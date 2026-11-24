package com.yourapp.ticketpass.reputation.domain.policy;

import com.yourapp.ticketpass.reputation.domain.ReputationTier;
import com.yourapp.ticketpass.reputation.domain.ReputationTierPolicy;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

@Component
public class BannedReputationPolicy implements ReputationTierPolicy {

    @Override
    public ReputationTier tier() {
        return ReputationTier.BANNED;
    }

    @Override
    public boolean supports(int normalizedScore) {
        return normalizedScore <= 0;
    }

    @Override
    public boolean isAllowed(BigDecimal salePrice) {
        return false;
    }

    @Override
    public String buildRejectionMessage(BigDecimal attemptedPrice, String actionLabel) {
        return "Điểm uy tín của bạn đã giảm về 0 — tài khoản [" + tier().getDisplayName()
                + "] và không thể " + actionLabel + ".";
    }
}
