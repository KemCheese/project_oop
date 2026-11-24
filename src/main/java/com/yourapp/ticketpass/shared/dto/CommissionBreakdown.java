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
public class CommissionBreakdown {

    private BigDecimal salePrice;

    private String tierCode;

    private BigDecimal tierRatePercent;

    private BigDecimal rawCommission;

    private BigDecimal totalCommission;

    private BigDecimal buyerShare;

    private BigDecimal sellerShare;

    private boolean minFeeApplied;

    private boolean capApplied;
}
