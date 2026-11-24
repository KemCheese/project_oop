package com.yourapp.ticketpass.payment.domain;

import java.math.BigDecimal;

import lombok.Getter;

@Getter
public enum CommissionTier {

    UNDER_1M(new BigDecimal("1000000"), new BigDecimal("7"), null),

    UNDER_5M(new BigDecimal("5000000"), new BigDecimal("5"), null),

    UNDER_10M(new BigDecimal("10000000"), new BigDecimal("3"), null),

    FROM_10M(null, new BigDecimal("2"), new BigDecimal("400000"));

    private final BigDecimal exclusiveUpperBound;

    private final BigDecimal ratePercent;

    private final BigDecimal maxCommission;

    CommissionTier(BigDecimal exclusiveUpperBound, BigDecimal ratePercent, BigDecimal maxCommission) {
        this.exclusiveUpperBound = exclusiveUpperBound;
        this.ratePercent = ratePercent;
        this.maxCommission = maxCommission;
    }
}
