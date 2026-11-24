package com.yourapp.ticketpass.reputation.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ReputationTier {

    EXCELLENT(
            5,
            "Xuất Sắc",
            null,
            "Không giới hạn giá trị giao dịch (được phép >= 10.000.000 đ)",
            "success"
    ),
    GOOD(
            4,
            "Tốt",
            new BigDecimal("10000000"),
            "Giao dịch dưới 10.000.000 đ",
            "info"
    ),
    NORMAL(
            3,
            "Bình thường",
            new BigDecimal("6000000"),
            "Giao dịch dưới 6.000.000 đ",
            "primary"
    ),
    WARNING(
            2,
            "Cảnh Báo",
            new BigDecimal("1000000"),
            "Giao dịch dưới 1.000.000 đ",
            "warning"
    ),
    DANGER(
            1,
            "Nguy Hiểm",
            new BigDecimal("100000"),
            "Giao dịch dưới 100.000 đ",
            "danger"
    ),
    BANNED(
            0,
            "Bị khóa vĩnh viễn",
            BigDecimal.ZERO,
            "Tài khoản đã bị khóa vĩnh viễn, cấm mọi giao dịch",
            "dark"
    );

    public static final int MIN_SCORE_INT = 0;
    public static final int MAX_SCORE_INT = 5;
    public static final int INITIAL_SCORE_INT = 3;

    private final int scoreLevel;
    private final String displayName;
    private final BigDecimal exclusiveMaxSalePrice;
    private final String limitDescription;
    private final String badgeColor;

    public static int normalizeScore(BigDecimal score) {
        if (score == null) {
            return INITIAL_SCORE_INT;
        }
        int rounded = score.setScale(0, RoundingMode.HALF_UP).intValue();
        return Math.max(MIN_SCORE_INT, Math.min(MAX_SCORE_INT, rounded));
    }

    public static ReputationTier fromScoreInt(int score) {
        int clamped = Math.max(MIN_SCORE_INT, Math.min(MAX_SCORE_INT, score));
        for (ReputationTier tier : values()) {
            if (tier.scoreLevel == clamped) {
                return tier;
            }
        }
        return NORMAL;
    }

    public static ReputationTier fromScore(BigDecimal score) {
        return fromScoreInt(normalizeScore(score));
    }
}
