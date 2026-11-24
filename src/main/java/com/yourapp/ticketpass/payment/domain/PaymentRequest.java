package com.yourapp.ticketpass.payment.domain;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PaymentRequest {

    private String referenceId;

    private Long payerUserId;

    private BigDecimal amount;

    private String orderInfo;

    private String returnUrl;

    private String ipnUrl;

    private String extraData;
}
