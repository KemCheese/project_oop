package com.yourapp.ticketpass.payment.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class InitEscrowRequest {

    @NotNull
    private Long ticketId;

    @NotBlank
    private String paymentMethod;

    private boolean confirmOverlap;
}
