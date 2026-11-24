package com.yourapp.ticketpass.payment.application;

import java.math.BigDecimal;

import org.springframework.stereotype.Service;

import com.yourapp.ticketpass.shared.config.TaxConfig;
import com.yourapp.ticketpass.shared.dto.TaxWithholding;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TaxWithholdingService {

    private final TaxConfig taxConfig;

    public TaxWithholding withhold(BigDecimal sellerGrossAmount, Long sellerId) {
        if (!isTaxable(sellerGrossAmount)) {
            return new TaxWithholding(
                    sellerGrossAmount,
                    taxConfig.getRatePercent(),
                    BigDecimal.ZERO,
                    sellerGrossAmount,
                    taxConfig.isMock());
        }

        BigDecimal taxAmount = sellerGrossAmount
                .multiply(taxConfig.getRatePercent())
                .divide(new BigDecimal("100"), 2, java.math.RoundingMode.HALF_UP);
        BigDecimal netAmount = sellerGrossAmount.subtract(taxAmount);

        return new TaxWithholding(
                sellerGrossAmount,
                taxConfig.getRatePercent(),
                taxAmount,
                netAmount,
                taxConfig.isMock());
    }

    public boolean isTaxable(BigDecimal sellerGrossAmount) {
        return taxConfig.isEnabled()
                && sellerGrossAmount != null
                && sellerGrossAmount.compareTo(taxConfig.getTaxableThreshold()) >= 0;
    }
}
