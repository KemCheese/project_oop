package com.yourapp.ticketpass.reputation.web.dto;

import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Dữ liệu uy tín công khai của một người dùng, dùng để hiển thị trên giao diện.
 *
 * <p>DTO này thay thế entity {@code UserReputationScore} khi ra khỏi module: nó không mang
 * theo {@code version} hay các bộ đếm nội bộ không cần công khai, và cho phép module khác
 * (ví dụ tầng {@code web} của module {@code user} khi dựng hồ sơ) tiêu thụ dữ liệu uy tín
 * mà không phụ thuộc vào entity.</p>
 *
 * <p>Module: reputation</p>
 *
 * Chủ sở hữu: Người C
 */
@Getter
@Setter
@NoArgsConstructor
public class ReputationResponse {

    private Long userId;

    /** Điểm uy tín hiện tại, thang 0.00 - 100.00. */
    private BigDecimal score;

    private Integer totalTransactions;

    private Integer successfulTransactions;

    private Integer disputesLost;

    private Integer disputesWon;

    private Instant lastTransactionAt;
}
