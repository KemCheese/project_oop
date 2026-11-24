package com.yourapp.ticketpass.shared.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.commission")
public class CommissionConfig {

    private BigDecimal minFee;

    private BigDecimal buyerShareRatio;

    private BigDecimal sellerShareRatio;

    private BigDecimal founderForfeitRatePercent;

    private BigDecimal founderForfeitMinAmount;

    private List<TierProperties> tiers = new ArrayList<>();

    @Getter
    @Setter
    public static class TierProperties {

        private String code;

        private BigDecimal exclusiveUpperBound;

        private BigDecimal ratePercent;

        private BigDecimal maxCommission;
    }
}
