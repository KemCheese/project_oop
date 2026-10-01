package com.yourapp.ticketpass.payment.application;

import com.yourapp.ticketpass.shared.config.CommissionConfig;
import com.yourapp.ticketpass.shared.dto.ForfeitBreakdown;
import com.yourapp.ticketpass.shared.exception.BusinessRuleViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test cho {@link ForfeitDepositPolicy}.
 */
class ForfeitDepositPolicyTest {

    private ForfeitDepositPolicy policy;

    @BeforeEach
    void setUp() {
        CommissionConfig config = new CommissionConfig();
        // Cấu hình tỷ lệ 7%, sàn 5.000đ như spec
        config.setFounderForfeitRatePercent(new BigDecimal("7"));
        config.setFounderForfeitMinAmount(new BigDecimal("5000"));
        policy = new ForfeitDepositPolicy(config);
    }

    @Test
    @DisplayName("Normal case: Cọc lớn hơn sàn (100.000đ)")
    void testComputeForfeit_NormalCase() {
        // Cọc 100.000đ -> 7% = 7.000đ (> 5.000đ) -> founderCut = 7.000đ, buyer = 93.000đ
        ForfeitBreakdown result = policy.computeForfeit(new BigDecimal("100000"));
        
        assertEquals(new BigDecimal("100000"), result.getTotalDeposit());
        assertEquals(new BigDecimal("7000.00"), result.getFounderCut());
        assertEquals(new BigDecimal("93000.00"), result.getBuyerCompensation());
    }

    @Test
    @DisplayName("Edge case: Cọc nhỏ hơn cả mức sàn (3.000đ)")
    void testComputeForfeit_EdgeCase_UnderMinAmount() {
        // Cọc 3.000đ -> min(3000, 5000) = 3.000đ -> founderCut = 3.000đ, buyer = 0đ
        ForfeitBreakdown result = policy.computeForfeit(new BigDecimal("3000"));
        
        assertEquals(new BigDecimal("3000"), result.getTotalDeposit());
        assertEquals(new BigDecimal("3000"), result.getFounderCut());
        assertEquals(new BigDecimal("0"), result.getBuyerCompensation());
    }

    @Test
    @DisplayName("Edge case: Tính ra đúng bằng mức sàn (50.000đ)")
    void testComputeForfeit_EdgeCase_ExactlyMinAmount() {
        // Cọc 50.000đ -> 7% = 3.500đ (< 5.000đ) -> nâng lên sàn 5.000đ -> buyer = 45.000đ
        ForfeitBreakdown result = policy.computeForfeit(new BigDecimal("50000"));
        
        assertEquals(new BigDecimal("50000"), result.getTotalDeposit());
        assertEquals(new BigDecimal("5000"), result.getFounderCut());
        assertEquals(new BigDecimal("45000"), result.getBuyerCompensation());
    }

    @Test
    @DisplayName("Exception: Cọc âm sẽ báo lỗi")
    void testComputeForfeit_NegativeDeposit() {
        BusinessRuleViolationException ex = assertThrows(BusinessRuleViolationException.class, () -> {
            policy.computeForfeit(new BigDecimal("-1000"));
        });
        assertTrue(ex.getMessage().contains("lớn hơn hoặc bằng 0"));
    }
}
