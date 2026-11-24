package com.yourapp.ticketpass.payment.application;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.stereotype.Service;

import com.yourapp.ticketpass.shared.config.CommissionConfig;
import com.yourapp.ticketpass.shared.dto.CommissionBreakdown;
import com.yourapp.ticketpass.shared.exception.BusinessRuleViolationException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CommissionCalculatorService {

    private final CommissionConfig commissionConfig;

    public CommissionConfig.TierProperties resolveTier(BigDecimal salePrice) {
        if (salePrice == null || salePrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessRuleViolationException(
                    "Giá bán phải lớn hơn 0 để tra bảng hoa hồng, nhận được: " + salePrice);
        }
        for (CommissionConfig.TierProperties tier : commissionConfig.getTiers()) {
            if (tier.getExclusiveUpperBound() == null
                    || salePrice.compareTo(tier.getExclusiveUpperBound()) < 0) {
                return tier;
            }
        }
        throw new BusinessRuleViolationException(
                "Không tìm được bậc hoa hồng cho giá bán: " + salePrice
                + ". Kiểm tra app.commission.tiers có bậc cao nhất với exclusive-upper-bound = null.");
    }

    public CommissionBreakdown calculate(BigDecimal salePrice) {
        CommissionConfig.TierProperties tier = resolveTier(salePrice);

        BigDecimal rawCommission = salePrice
                .multiply(tier.getRatePercent())
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);

        boolean minFeeApplied = false;
        BigDecimal totalCommission = rawCommission;
        if (rawCommission.compareTo(commissionConfig.getMinFee()) < 0) {
            totalCommission = commissionConfig.getMinFee();
            minFeeApplied = true;
        }

        boolean capApplied = false;
        if (tier.getMaxCommission() != null
                && totalCommission.compareTo(tier.getMaxCommission()) > 0) {
            totalCommission = tier.getMaxCommission();
            capApplied = true;
        }

        BigDecimal buyerShare = totalCommission
                .multiply(commissionConfig.getBuyerShareRatio())
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal sellerShare = totalCommission.subtract(buyerShare);

        return new CommissionBreakdown(
                salePrice,
                tier.getCode(),
                tier.getRatePercent(),
                rawCommission,
                totalCommission,
                buyerShare,
                sellerShare,
                minFeeApplied,
                capApplied);
    }

    public BigDecimal buyerShareOf(CommissionBreakdown breakdown) {
        if (breakdown == null) {
            throw new BusinessRuleViolationException(
                    "CommissionBreakdown không được null khi lấy phần hoa hồng người mua.");
        }
        return breakdown.getBuyerShare();
    }

    public BigDecimal sellerShareOf(CommissionBreakdown breakdown) {
        if (breakdown == null) {
            throw new BusinessRuleViolationException(
                    "CommissionBreakdown không được null khi lấy phần hoa hồng người bán.");
        }
        return breakdown.getSellerShare();
    }
}
