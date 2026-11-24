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
public class TaxWithholding {

    private BigDecimal grossAmount;

    private BigDecimal taxRatePercent;

    private BigDecimal taxAmount;

    private BigDecimal netAmount;

    private boolean mock;
}
