package com.yourapp.ticketpass.payment.web.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class InitDepositRequest {

    @NotNull
    private Long ticketId;
}
