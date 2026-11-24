package com.yourapp.ticketpass.payment.application;

import com.yourapp.ticketpass.shared.config.CommissionConfig;
import com.yourapp.ticketpass.shared.dto.ForfeitBreakdown;
import com.yourapp.ticketpass.shared.exception.BusinessRuleViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class ForfeitDepositPolicyTest {

    private ForfeitDepositPolicy policy;

    @BeforeEach
    void setUp() {
        CommissionConfig config = new CommissionConfig();
        config.setFounderForfeitRatePercent(new BigDecimal("7"));
        config.setFounderForfeitMinAmount(new BigDecimal("5000"));
        policy = new ForfeitDepositPolicy(config);
    }

    @Test
    @DisplayName("Normal case: Cọc lớn hơn sàn (100.000đ)")
    void testComputeForfeit_NormalCase() {
        ForfeitBreakdown result = policy.computeForfeit(new BigDecimal("100000"));
        
        assertEquals(new BigDecimal("100000"), result.getTotalDeposit());
        assertEquals(new BigDecimal("7000.00"), result.getFounderCut());
        assertEquals(new BigDecimal("93000.00"), result.getBuyerCompensation());
    }

    @Test
    @DisplayName("Edge case: Cọc nhỏ hơn cả mức sàn (3.000đ)")
    void testComputeForfeit_EdgeCase_UnderMinAmount() {
        ForfeitBreakdown result = policy.computeForfeit(new BigDecimal("3000"));
        
        assertEquals(new BigDecimal("3000"), result.getTotalDeposit());
        assertEquals(new BigDecimal("3000"), result.getFounderCut());
        assertEquals(new BigDecimal("0"), result.getBuyerCompensation());
    }

    @Test
    @DisplayName("Edge case: Tính ra đúng bằng mức sàn (50.000đ)")
    void testComputeForfeit_EdgeCase_ExactlyMinAmount() {
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
