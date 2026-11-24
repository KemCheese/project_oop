package com.yourapp.ticketpass.shared.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.tax")
public class TaxConfig {

    private boolean enabled;

    private boolean mock;

    private BigDecimal ratePercent;

    private BigDecimal taxableThreshold;
}
