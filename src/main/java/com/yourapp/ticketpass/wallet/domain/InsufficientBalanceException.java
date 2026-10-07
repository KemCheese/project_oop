package com.yourapp.ticketpass.wallet.domain;

import java.math.BigDecimal;

import com.yourapp.ticketpass.shared.exception.BusinessRuleViolationException;

import lombok.Getter;

/**
 * Ngoại lệ ném ra khi số dư trong ví không đủ để thực hiện giao dịch trừ tiền.
 *
 * <p>Kế thừa {@link BusinessRuleViolationException} để được {@code GlobalExceptionHandler}
 * xử lý tự động thành phản hồi lỗi 400 và hiển thị thông điệp thân thiện.</p>
 *
 * <p>Module: wallet</p>
 * <p>Chủ sở hữu: Người C (Dev C)</p>
 */
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
