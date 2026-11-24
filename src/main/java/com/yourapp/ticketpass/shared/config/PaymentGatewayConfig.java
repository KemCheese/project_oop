package com.yourapp.ticketpass.shared.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.payment.manual-transfer")
public class PaymentGatewayConfig {

    private String gatewayName;

    private String accountHolder;

    private String accountNumber;

    private String bankName;

    private String qrImageUrl;

    private String transferNotePrefix;
}
