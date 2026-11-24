package com.yourapp.ticketpass.ticket.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class PurchaseTicketRequest {

    @NotNull
    private Long ticketId;

    @NotBlank
    private String paymentMethod;

    private String note;
}
