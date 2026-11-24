package com.yourapp.ticketpass.shared.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ForfeitBreakdown {

    private BigDecimal totalDeposit;

    private BigDecimal founderCut;

    private BigDecimal buyerCompensation;
}
