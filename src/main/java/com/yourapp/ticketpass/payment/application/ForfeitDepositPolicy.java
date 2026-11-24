package com.yourapp.ticketpass.payment.application;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.yourapp.ticketpass.shared.exception.BusinessRuleViolationException;

import org.springframework.stereotype.Service;

import com.yourapp.ticketpass.shared.config.CommissionConfig;
import com.yourapp.ticketpass.shared.dto.ForfeitBreakdown;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ForfeitDepositPolicy {

    private final CommissionConfig commissionConfig;

    public ForfeitBreakdown computeForfeit(BigDecimal totalDeposit) {
        if (totalDeposit == null || totalDeposit.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessRuleViolationException(
                    "Tổng tiền cọc phải lớn hơn hoặc bằng 0 để tính tịch thu, nhận được: " + totalDeposit);
        }

        BigDecimal rawCut = totalDeposit
                .multiply(commissionConfig.getFounderForfeitRatePercent())
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);

        BigDecimal minAmount = commissionConfig.getFounderForfeitMinAmount();
        BigDecimal floored = rawCut.max(minAmount);

        BigDecimal founderCut = totalDeposit.min(floored);

        BigDecimal buyerCompensation = totalDeposit.subtract(founderCut);

        if (founderCut.compareTo(BigDecimal.ZERO) < 0
                || buyerCompensation.compareTo(BigDecimal.ZERO) < 0
                || founderCut.add(buyerCompensation).compareTo(totalDeposit) != 0) {
            throw new BusinessRuleViolationException(
                    "Vi phạm bất biến tịch thu cọc: founderCut=" + founderCut
                    + ", buyerCompensation=" + buyerCompensation
                    + ", totalDeposit=" + totalDeposit);
        }

        return new ForfeitBreakdown(totalDeposit, founderCut, buyerCompensation);
    }
}
