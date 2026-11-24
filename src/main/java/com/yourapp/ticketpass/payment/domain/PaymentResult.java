package com.yourapp.ticketpass.payment.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResult {

    private boolean success;

    private String gatewayTransactionId;

    private String gatewayStatus;

    private String payUrl;

    private String message;

    private String rawPayload;
}
