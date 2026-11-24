package com.yourapp.ticketpass.wallet.domain;

import java.math.BigDecimal;

import com.yourapp.ticketpass.shared.exception.BusinessRuleViolationException;

import lombok.Getter;

@Getter
public class InsufficientBalanceException extends BusinessRuleViolationException {

    private final BigDecimal currentBalance;
    private final BigDecimal requiredAmount;

    public InsufficientBalanceException(BigDecimal currentBalance, BigDecimal requiredAmount) {
        super(String.format("Số dư ví không đủ để thực hiện giao dịch (Số dư hiện tại: %,.0f VNĐ, Yêu cầu: %,.0f VNĐ). Vui lòng nạp thêm tiền vào ví.",
                currentBalance != null ? currentBalance : BigDecimal.ZERO,
                requiredAmount != null ? requiredAmount : BigDecimal.ZERO));
        this.currentBalance = currentBalance;
        this.requiredAmount = requiredAmount;
    }
}
