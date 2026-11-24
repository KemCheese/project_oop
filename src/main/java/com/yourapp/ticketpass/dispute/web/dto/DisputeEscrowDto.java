package com.yourapp.ticketpass.dispute.web.dto;

import java.math.BigDecimal;

import com.yourapp.ticketpass.payment.domain.EscrowStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DisputeEscrowDto {

    private Long id;

    private BigDecimal amount;

    private BigDecimal salePrice;

    private EscrowStatus status;

    private String gatewayName;
}
