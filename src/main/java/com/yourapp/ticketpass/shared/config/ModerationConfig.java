package com.yourapp.ticketpass.shared.config;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app.moderation")
public class ModerationConfig {

    @DecimalMin(value = "0.00", message = "Ngưỡng cảnh báo uy tín tối thiểu phải >= 0.00")
    private BigDecimal lowReputationThreshold = new BigDecimal("2.00");

    @Min(value = 1, message = "Ngưỡng cảnh báo số lần thua tranh chấp tối thiểu phải >= 1")
    private int maxDisputesLostWarning = 2;
}
