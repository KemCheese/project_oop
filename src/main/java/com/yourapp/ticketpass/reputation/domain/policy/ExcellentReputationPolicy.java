package com.yourapp.ticketpass.reputation.domain.policy;

import com.yourapp.ticketpass.reputation.domain.ReputationTier;
import com.yourapp.ticketpass.reputation.domain.ReputationTierPolicy;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

@Component
public class ExcellentReputationPolicy implements ReputationTierPolicy {

    @Override
    public ReputationTier tier() {
        return ReputationTier.EXCELLENT;
    }

    @Override
    public boolean supports(int normalizedScore) {
        return normalizedScore >= 5;
    }

    @Override
    public boolean isAllowed(BigDecimal salePrice) {
        return salePrice != null && salePrice.compareTo(BigDecimal.ZERO) > 0;
    }

    @Override
    public String buildRejectionMessage(BigDecimal attemptedPrice, String actionLabel) {
        return "Giá trị giao dịch không hợp lệ để " + actionLabel + ".";
    }
}
