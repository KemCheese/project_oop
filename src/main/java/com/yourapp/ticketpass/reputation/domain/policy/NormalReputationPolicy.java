package com.yourapp.ticketpass.reputation.domain.policy;

import com.yourapp.ticketpass.reputation.domain.ReputationTier;
import com.yourapp.ticketpass.reputation.domain.ReputationTierPolicy;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

@Component
public class NormalReputationPolicy implements ReputationTierPolicy {

    private static final BigDecimal EXCLUSIVE_MAX = new BigDecimal("6000000");

    @Override
    public ReputationTier tier() {
        return ReputationTier.NORMAL;
    }

    @Override
    public boolean supports(int normalizedScore) {
        return normalizedScore == 3;
    }

    @Override
    public boolean isAllowed(BigDecimal salePrice) {
        return salePrice != null
                && salePrice.compareTo(BigDecimal.ZERO) > 0
                && salePrice.compareTo(EXCLUSIVE_MAX) < 0;
    }

    @Override
    public String buildRejectionMessage(BigDecimal attemptedPrice, String actionLabel) {
        return String.format(
                "Điểm uy tín hiện tại của bạn là 3 (Bình thường), chỉ được phép %s có giá dưới 6.000.000 đ "
                        + "(giá vé hiện tại: %,.0f đ). Hoàn tất giao dịch thành công để nâng hạn mức.",
                actionLabel, attemptedPrice != null ? attemptedPrice : BigDecimal.ZERO);
    }
}
