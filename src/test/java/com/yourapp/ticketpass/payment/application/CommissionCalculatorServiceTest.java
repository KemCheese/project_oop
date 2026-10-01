package com.yourapp.ticketpass.payment.application;

import com.yourapp.ticketpass.shared.config.CommissionConfig;
import com.yourapp.ticketpass.shared.dto.CommissionBreakdown;
import com.yourapp.ticketpass.shared.exception.BusinessRuleViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test cho {@link CommissionCalculatorService}.
 *
 * <p>Không cần Spring context — khởi tạo {@link CommissionConfig} thủ công với
 * đúng cấu hình đã chốt trong {@code application.properties} để test nhanh, độc lập.</p>
 *
 * <p>Module: payment (test).</p>
 * <p>Chủ sở hữu theo phân công: Người B.</p>
 */
class CommissionCalculatorServiceTest {

    private CommissionCalculatorService service;

    /**
     * Dựng CommissionConfig khớp với application.properties đã chốt:
     * <pre>
     * UNDER_1M  : 7%, upper=1_000_000, max=null
     * UNDER_5M  : 5%, upper=5_000_000, max=null
     * UNDER_10M : 3%, upper=10_000_000, max=null
     * FROM_10M  : 2%, upper=null,       max=400_000
     * min-fee   : 5_000
     * buyer/seller ratio : 0.5 / 0.5
     * </pre>
     */
    @BeforeEach
    void setUp() {
        CommissionConfig config = new CommissionConfig();
        config.setMinFee(new BigDecimal("5000"));
        config.setBuyerShareRatio(new BigDecimal("0.5"));
        config.setSellerShareRatio(new BigDecimal("0.5"));

        List<CommissionConfig.TierProperties> tiers = new ArrayList<>();

        CommissionConfig.TierProperties t1 = new CommissionConfig.TierProperties();
        t1.setCode("UNDER_1M");
        t1.setExclusiveUpperBound(new BigDecimal("1000000"));
        t1.setRatePercent(new BigDecimal("7"));
        t1.setMaxCommission(null);
        tiers.add(t1);

        CommissionConfig.TierProperties t2 = new CommissionConfig.TierProperties();
        t2.setCode("UNDER_5M");
        t2.setExclusiveUpperBound(new BigDecimal("5000000"));
        t2.setRatePercent(new BigDecimal("5"));
        t2.setMaxCommission(null);
        tiers.add(t2);

        CommissionConfig.TierProperties t3 = new CommissionConfig.TierProperties();
        t3.setCode("UNDER_10M");
        t3.setExclusiveUpperBound(new BigDecimal("10000000"));
        t3.setRatePercent(new BigDecimal("3"));
        t3.setMaxCommission(null);
        tiers.add(t3);

        CommissionConfig.TierProperties t4 = new CommissionConfig.TierProperties();
        t4.setCode("FROM_10M");
        t4.setExclusiveUpperBound(null);   // bậc cao nhất
        t4.setRatePercent(new BigDecimal("2"));
        t4.setMaxCommission(new BigDecimal("400000"));
        tiers.add(t4);

        config.setTiers(tiers);
        service = new CommissionCalculatorService(config);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // resolveTier
    // ─────────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("resolveTier — tra bảng bậc hoa hồng")
    class ResolveTierTests {

        @Test
        @DisplayName("Giá 500_000đ → bậc UNDER_1M (7%)")
        void price500k_shouldBeUnder1M() {
            CommissionConfig.TierProperties tier =
                    service.resolveTier(new BigDecimal("500000"));
            assertEquals("UNDER_1M", tier.getCode());
            assertEquals(0, tier.getRatePercent().compareTo(new BigDecimal("7")));
        }

        @Test
        @DisplayName("Giá đúng bằng 1_000_000đ (exclusive boundary) → bậc UNDER_5M (5%)")
        void priceExactly1M_shouldBeUnder5M() {
            // biên trên là exclusive: 1_000_000 không thuộc UNDER_1M nữa
            CommissionConfig.TierProperties tier =
                    service.resolveTier(new BigDecimal("1000000"));
            assertEquals("UNDER_5M", tier.getCode());
        }

        @Test
        @DisplayName("Giá 2_000_000đ → bậc UNDER_5M (5%)")
        void price2M_shouldBeUnder5M() {
            CommissionConfig.TierProperties tier =
                    service.resolveTier(new BigDecimal("2000000"));
            assertEquals("UNDER_5M", tier.getCode());
        }

        @Test
        @DisplayName("Giá 6_000_000đ → bậc UNDER_10M (3%)")
        void price6M_shouldBeUnder10M() {
            CommissionConfig.TierProperties tier =
                    service.resolveTier(new BigDecimal("6000000"));
            assertEquals("UNDER_10M", tier.getCode());
        }

        @Test
        @DisplayName("Giá đúng bằng 10_000_000đ → bậc FROM_10M (2%)")
        void priceExactly10M_shouldBeFrom10M() {
            CommissionConfig.TierProperties tier =
                    service.resolveTier(new BigDecimal("10000000"));
            assertEquals("FROM_10M", tier.getCode());
        }

        @Test
        @DisplayName("Giá 50_000_000đ → bậc FROM_10M (2%, bậc cao nhất)")
        void price50M_shouldBeFrom10M() {
            CommissionConfig.TierProperties tier =
                    service.resolveTier(new BigDecimal("50000000"));
            assertEquals("FROM_10M", tier.getCode());
        }

        @Test
        @DisplayName("Giá null → BusinessRuleViolationException")
        void nullPrice_shouldThrow() {
            assertThrows(BusinessRuleViolationException.class,
                    () -> service.resolveTier(null));
        }

        @Test
        @DisplayName("Giá = 0 → BusinessRuleViolationException")
        void zeroPrice_shouldThrow() {
            assertThrows(BusinessRuleViolationException.class,
                    () -> service.resolveTier(BigDecimal.ZERO));
        }

        @Test
        @DisplayName("Giá âm → BusinessRuleViolationException")
        void negativePrice_shouldThrow() {
            assertThrows(BusinessRuleViolationException.class,
                    () -> service.resolveTier(new BigDecimal("-1")));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // calculate — bảng phí bình thường
    // ─────────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("calculate — hoa hồng bình thường (không sàn, không trần)")
    class CalculateNormalTests {

        @Test
        @DisplayName("Vé 500_000đ (UNDER_1M, 7%) → rawCommission = 35_000đ, không áp sàn")
        void price500k_normal() {
            CommissionBreakdown bd = service.calculate(new BigDecimal("500000"));

            assertEquals("UNDER_1M", bd.getTierCode());
            assertEquals(0, bd.getRawCommission().compareTo(new BigDecimal("35000.00")));
            assertEquals(0, bd.getTotalCommission().compareTo(new BigDecimal("35000.00")));
            assertFalse(bd.isMinFeeApplied());
            assertFalse(bd.isCapApplied());
        }

        @Test
        @DisplayName("Vé 2_000_000đ (UNDER_5M, 5%) → totalCommission = 100_000đ")
        void price2M_normal() {
            CommissionBreakdown bd = service.calculate(new BigDecimal("2000000"));

            assertEquals("UNDER_5M", bd.getTierCode());
            assertEquals(0, bd.getTotalCommission().compareTo(new BigDecimal("100000.00")));
            assertFalse(bd.isMinFeeApplied());
            assertFalse(bd.isCapApplied());
        }

        @Test
        @DisplayName("Vé 6_000_000đ (UNDER_10M, 3%) → totalCommission = 180_000đ — KHÔNG LŨY TIẾN")
        void price6M_notProgressive() {
            CommissionBreakdown bd = service.calculate(new BigDecimal("6000000"));

            assertEquals("UNDER_10M", bd.getTierCode());
            // 3% * 6_000_000 = 180_000 (KHÔNG phải 7%*1M + 5%*4M + 3%*1M)
            assertEquals(0, bd.getTotalCommission().compareTo(new BigDecimal("180000.00")));
            assertFalse(bd.isMinFeeApplied());
            assertFalse(bd.isCapApplied());
        }

        @Test
        @DisplayName("Vé 10_000_000đ (FROM_10M, 2%) → totalCommission = 200_000đ, chưa chạm trần")
        void price10M_belowCap() {
            CommissionBreakdown bd = service.calculate(new BigDecimal("10000000"));

            assertEquals("FROM_10M", bd.getTierCode());
            assertEquals(0, bd.getTotalCommission().compareTo(new BigDecimal("200000.00")));
            assertFalse(bd.isMinFeeApplied());
            assertFalse(bd.isCapApplied());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // calculate — áp sàn tối thiểu
    // ─────────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("calculate — áp sàn tối thiểu 5_000đ")
    class CalculateMinFeeTests {

        @Test
        @DisplayName("Vé 50_000đ (7% → 3_500đ < 5_000đ) → áp sàn, totalCommission = 5_000đ")
        void cheapTicket_minFeeApplied() {
            CommissionBreakdown bd = service.calculate(new BigDecimal("50000"));

            assertEquals(0, bd.getRawCommission().compareTo(new BigDecimal("3500.00")));
            assertEquals(0, bd.getTotalCommission().compareTo(new BigDecimal("5000")));
            assertTrue(bd.isMinFeeApplied());
            assertFalse(bd.isCapApplied());
        }

        @Test
        @DisplayName("Vé 71_429đ (7% → ~5_000.03đ ≥ 5_000đ) → không áp sàn")
        void ticketJustAboveMinFeeThreshold_noMinFee() {
            // 71_429 * 7% = 4_999.03 → < 5_000 → phải áp sàn
            // Nhưng với 71_429đ: 7% = 5_000.03đ → không áp sàn
            // Thực ra: 71_428 * 7% = 4_999.96 < 5_000 → áp sàn
            //          71_429 * 7% = 5_000.03 ≥ 5_000 → không áp sàn
            CommissionBreakdown bd = service.calculate(new BigDecimal("71429"));
            assertFalse(bd.isMinFeeApplied(),
                    "71429 * 7% = 5000.03 ≥ 5000 nên không phải áp sàn");
        }

        @Test
        @DisplayName("Vé 71_428đ (7% = 4_999.96đ < 5_000đ) → áp sàn")
        void ticketJustBelowMinFeeThreshold_minFeeApplied() {
            CommissionBreakdown bd = service.calculate(new BigDecimal("71428"));
            assertTrue(bd.isMinFeeApplied(),
                    "71428 * 7% = 4999.96 < 5000 nên phải áp sàn");
            assertEquals(0, bd.getTotalCommission().compareTo(new BigDecimal("5000")));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // calculate — chặn trần FROM_10M
    // ─────────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("calculate — chặn trần 400_000đ (bậc FROM_10M)")
    class CalculateCapTests {

        @Test
        @DisplayName("Vé 20_000_000đ (2% → 400_000đ) — đúng bằng trần, không cờ cap")
        void priceAtCapBoundary_noCap() {
            CommissionBreakdown bd = service.calculate(new BigDecimal("20000000"));

            // 2% * 20_000_000 = 400_000 = max → không "vượt" trần nên capApplied = false
            assertEquals(0, bd.getTotalCommission().compareTo(new BigDecimal("400000.00")));
            assertFalse(bd.isCapApplied(),
                    "Đúng bằng trần thì không được đặt capApplied = true");
        }

        @Test
        @DisplayName("Vé 20_000_001đ (2% → 400_000.02đ > 400_000đ) → chặn trần")
        void priceJustAboveCap_capApplied() {
            CommissionBreakdown bd = service.calculate(new BigDecimal("20000001"));

            assertEquals(0, bd.getTotalCommission().compareTo(new BigDecimal("400000")));
            assertTrue(bd.isCapApplied());
            assertFalse(bd.isMinFeeApplied());
        }

        @Test
        @DisplayName("Vé 100_000_000đ (2% → 2_000_000đ >> trần) → chặn trần 400_000đ")
        void veryExpensiveTicket_capApplied() {
            CommissionBreakdown bd = service.calculate(new BigDecimal("100000000"));

            assertEquals(0, bd.getTotalCommission().compareTo(new BigDecimal("400000")));
            assertTrue(bd.isCapApplied());
        }

        @Test
        @DisplayName("Bậc UNDER_1M/5M/10M không có trần → không capApplied dù hoa hồng cao")
        void otherTiersHaveNoCap() {
            // 999_999đ * 7% = 69_999.93đ → không có trần cho bậc UNDER_1M
            CommissionBreakdown bd = service.calculate(new BigDecimal("999999"));
            assertFalse(bd.isCapApplied());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // calculate — bất biến buyerShare + sellerShare == totalCommission
    // ─────────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("calculate — bất biến buyerShare + sellerShare == totalCommission")
    class ShareInvariantTests {

        @Test
        @DisplayName("Chia đôi chẵn: 100_000đ → buyer = seller = 50_000đ")
        void evenSplit() {
            CommissionBreakdown bd = service.calculate(new BigDecimal("2000000")); // 100_000đ

            assertEquals(0, bd.getBuyerShare().compareTo(new BigDecimal("50000.00")));
            assertEquals(0, bd.getSellerShare().compareTo(new BigDecimal("50000.00")));
            assertEquals(0, bd.getBuyerShare().add(bd.getSellerShare())
                    .compareTo(bd.getTotalCommission()));
        }

        @Test
        @DisplayName("Chia đôi số lẻ: sàn 5_000đ → buyer = 2_500đ, seller = 2_500đ")
        void splitMinFee5000() {
            CommissionBreakdown bd = service.calculate(new BigDecimal("50000")); // raw 3_500 → áp sàn 5_000

            // 5_000 * 0.5 = 2_500.00 (tròn)
            assertEquals(0, bd.getBuyerShare().compareTo(new BigDecimal("2500.00")));
            assertEquals(0, bd.getSellerShare().compareTo(new BigDecimal("2500.00")));
            // bất biến tuyệt đối
            assertEquals(0, bd.getBuyerShare().add(bd.getSellerShare())
                    .compareTo(bd.getTotalCommission()),
                    "buyerShare + sellerShare phải luôn bằng đúng totalCommission");
        }

        @Test
        @DisplayName("Bất biến với nhiều giá bán khác nhau")
        void invariantHoldsForVariousPrices() {
            String[] prices = {"1", "99999", "500000", "1000000", "5000000",
                    "9999999", "10000000", "20000001", "100000000"};
            for (String p : prices) {
                CommissionBreakdown bd = service.calculate(new BigDecimal(p));
                BigDecimal sumShares = bd.getBuyerShare().add(bd.getSellerShare());
                assertEquals(0, sumShares.compareTo(bd.getTotalCommission()),
                        "Bất biến thất bại với giá bán = " + p
                                + ". buyer=" + bd.getBuyerShare()
                                + ", seller=" + bd.getSellerShare()
                                + ", total=" + bd.getTotalCommission());
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // calculate — tierCode và tierRatePercent trong breakdown
    // ─────────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("calculate — tierCode và salePrice được lưu vào breakdown")
    class BreakdownMetadataTests {

        @Test
        @DisplayName("salePrice được lưu đúng vào breakdown")
        void salePriceStoredCorrectly() {
            BigDecimal price = new BigDecimal("3000000");
            CommissionBreakdown bd = service.calculate(price);
            assertEquals(0, bd.getSalePrice().compareTo(price));
        }

        @Test
        @DisplayName("tierCode lưu đúng string code từ config")
        void tierCodeFromConfig() {
            CommissionBreakdown bd = service.calculate(new BigDecimal("500000"));
            assertEquals("UNDER_1M", bd.getTierCode());

            CommissionBreakdown bd2 = service.calculate(new BigDecimal("15000000"));
            assertEquals("FROM_10M", bd2.getTierCode());
        }

        @Test
        @DisplayName("tierRatePercent khớp với bậc được chọn")
        void tierRatePercentMatchesTier() {
            CommissionBreakdown bd = service.calculate(new BigDecimal("6000000")); // UNDER_10M, 3%
            assertEquals(0, bd.getTierRatePercent().compareTo(new BigDecimal("3")));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // buyerShareOf / sellerShareOf — null guard
    // ─────────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("buyerShareOf / sellerShareOf — null guard")
    class ShareHelperTests {

        @Test
        @DisplayName("buyerShareOf(null) → BusinessRuleViolationException")
        void buyerShareOf_null_throws() {
            assertThrows(BusinessRuleViolationException.class,
                    () -> service.buyerShareOf(null));
        }

        @Test
        @DisplayName("sellerShareOf(null) → BusinessRuleViolationException")
        void sellerShareOf_null_throws() {
            assertThrows(BusinessRuleViolationException.class,
                    () -> service.sellerShareOf(null));
        }

        @Test
        @DisplayName("buyerShareOf trả về đúng buyerShare từ breakdown")
        void buyerShareOf_returnsCorrectValue() {
            CommissionBreakdown bd = service.calculate(new BigDecimal("2000000"));
            assertEquals(0, service.buyerShareOf(bd).compareTo(bd.getBuyerShare()));
        }

        @Test
        @DisplayName("sellerShareOf trả về đúng sellerShare từ breakdown")
        void sellerShareOf_returnsCorrectValue() {
            CommissionBreakdown bd = service.calculate(new BigDecimal("2000000"));
            assertEquals(0, service.sellerShareOf(bd).compareTo(bd.getSellerShare()));
        }
    }
}
