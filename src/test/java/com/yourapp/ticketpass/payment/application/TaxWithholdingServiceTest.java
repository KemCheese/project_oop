package com.yourapp.ticketpass.payment.application;

import com.yourapp.ticketpass.shared.config.TaxConfig;
import com.yourapp.ticketpass.shared.dto.TaxWithholding;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class TaxWithholdingServiceTest {

    private TaxConfig taxConfig;
    private TaxWithholdingService service;

    @BeforeEach
    void setUp() {
        taxConfig = new TaxConfig();
        taxConfig.setEnabled(true);
        taxConfig.setMock(true);
        taxConfig.setRatePercent(new BigDecimal("1.5"));
        taxConfig.setTaxableThreshold(new BigDecimal("5000000"));

        service = new TaxWithholdingService(taxConfig);
    }

    @Test
    @DisplayName("Dưới ngưỡng chịu thuế (3.000.000đ) -> không khấu thuế")
    void testWithhold_BelowThreshold() {
        BigDecimal gross = new BigDecimal("3000000");
        TaxWithholding result = service.withhold(gross, 1L);

        assertFalse(service.isTaxable(gross));
        assertEquals(gross, result.getGrossAmount());
        assertEquals(new BigDecimal("0"), result.getTaxAmount());
        assertEquals(gross, result.getNetAmount());
        assertTrue(result.isMock());
    }

    @Test
    @DisplayName("Đúng bằng ngưỡng chịu thuế (5.000.000đ) -> khấu thuế 1.5% = 75.000đ theo BR-08")
    void testWithhold_ExactThreshold() {
        BigDecimal gross = new BigDecimal("5000000");
        TaxWithholding result = service.withhold(gross, 1L);

        assertTrue(service.isTaxable(gross));
        assertEquals(gross, result.getGrossAmount());
        assertEquals(new BigDecimal("75000.00"), result.getTaxAmount());
        assertEquals(new BigDecimal("4925000.00"), result.getNetAmount());
        assertTrue(result.isMock());
    }

    @Test
    @DisplayName("Trên ngưỡng chịu thuế (8.000.000đ) -> khấu thuế 1.5% = 120.000đ theo BR-08")
    void testWithhold_AboveThreshold() {
        BigDecimal gross = new BigDecimal("8000000");
        TaxWithholding result = service.withhold(gross, 2L);

        assertTrue(service.isTaxable(gross));
        assertEquals(gross, result.getGrossAmount());
        assertEquals(new BigDecimal("120000.00"), result.getTaxAmount());
        assertEquals(new BigDecimal("7880000.00"), result.getNetAmount());
    }

    @Test
    @DisplayName("Khi app.tax.enabled = false -> không khấu thuế dù giá cao")
    void testWithhold_WhenDisabled() {
        taxConfig.setEnabled(false);

        BigDecimal gross = new BigDecimal("25000000");
        TaxWithholding result = service.withhold(gross, 3L);

        assertFalse(service.isTaxable(gross));
        assertEquals(new BigDecimal("0"), result.getTaxAmount());
        assertEquals(gross, result.getNetAmount());
    }

    @Test
    @DisplayName("Làm tròn HALF_UP khi tính thuế")
    void testWithhold_RoundingHalfUp() {
        BigDecimal gross = new BigDecimal("5000001");
        TaxWithholding result = service.withhold(gross, 4L);

        assertEquals(new BigDecimal("75000.02"), result.getTaxAmount());
        assertEquals(new BigDecimal("4925000.98"), result.getNetAmount());
    }
}
