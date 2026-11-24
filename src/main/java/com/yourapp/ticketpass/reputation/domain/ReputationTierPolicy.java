package com.yourapp.ticketpass.reputation.domain;

import java.math.BigDecimal;

public interface ReputationTierPolicy {

    ReputationTier tier();

    boolean supports(int normalizedScore);

    boolean isAllowed(BigDecimal salePrice);

    default BigDecimal exclusiveMaxSalePrice() {
        return tier().getExclusiveMaxSalePrice();
    }

    default String limitDescription() {
        return tier().getLimitDescription();
    }

    String buildRejectionMessage(BigDecimal attemptedPrice, String actionLabel);
}
