package com.yourapp.ticketpass.payment.web.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class PaymentInitResponse {

    private boolean success;

    private String payUrl;

    private String referenceId;

    private String message;
}
