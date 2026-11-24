package com.yourapp.ticketpass.dispute.web.dto;

import java.math.BigDecimal;

import com.yourapp.ticketpass.payment.domain.DepositStatus;

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
public class DisputeDepositDto {

    private Long id;

    private BigDecimal depositAmount;

    private DepositStatus status;

    private BigDecimal depositPercent;
}
