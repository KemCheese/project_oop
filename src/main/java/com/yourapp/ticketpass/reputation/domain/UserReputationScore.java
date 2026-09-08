package com.yourapp.ticketpass.reputation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Bản ghi điểm uy tín tích luỹ của một người dùng.
 *
 * <p>Mỗi người dùng có đúng một bản ghi ({@code userId} là khoá duy nhất). Điểm khởi tạo là
 * {@code 100.00}, thang điểm chạy trong khoảng 0-100: cộng điểm khi giao dịch hoàn tất, trừ
 * điểm khi thua tranh chấp hoặc huỷ listing sau khi người mua đã thanh toán.</p>
 *
 * <p>Theo nguyên tắc kiến trúc số 1, {@code userId} là ID thuần sang module {@code user},
 * không dùng quan hệ JPA. {@code RiskTierResolver} của module {@code verification} cần điểm
 * này để quyết định mức đặt cọc, nhưng nhận vào dưới dạng {@code BigDecimal} chứ
 * <b>không</b> import entity này.</p>
 *
 * <p>Trường {@code version} phục vụ optimistic locking: bản ghi điểm bị nhiều luồng sự kiện
 * cập nhật đồng thời (hoàn tất giao dịch, xử lý tranh chấp, huỷ listing), nên nếu hai
 * transaction cùng ghi thì transaction đến sau sẽ nhận
 * {@code OptimisticLockingFailureException} và được thử lại thay vì ghi đè mất dữ liệu.</p>
 *
 * <p>Entity chỉ chứa field và getter/setter, không chứa business logic; mọi quy tắc cộng trừ
 * điểm nằm trong {@code reputation.application.UpdateReputationUseCase}.</p>
 *
 * <p>Module: reputation</p>
 *
 * Chủ sở hữu: Người C
 */
@Entity
@Table(name = "user_reputation_scores")
@Getter
@Setter
@NoArgsConstructor
public class UserReputationScore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** ID thuần của người dùng (module user); mỗi người dùng chỉ có một bản ghi điểm. */
    @Column(nullable = false, unique = true)
    private Long userId;

    /** Điểm uy tín hiện tại, thang 0.00 - 100.00, khởi tạo 100.00. */
    @Column(precision = 5, scale = 2)
    private BigDecimal score;

    /** Tổng số giao dịch đã tham gia với vai trò người bán hoặc người mua. */
    private Integer totalTransactions;

    /** Số giao dịch kết thúc thành công (không tranh chấp hoặc tranh chấp nghiêng về mình). */
    private Integer successfulTransactions;

    /** Số lần huỷ vé đang rao bán sau khi người mua đã thanh toán. */
    private Integer cancelledListings;

    /** Số lần bị người khác mở khiếu nại nhắm vào mình. */
    private Integer disputesOpenedAgainst;

    /** Số tranh chấp bị xử thua. */
    private Integer disputesLost;

    /** Số tranh chấp được xử thắng. */
    private Integer disputesWon;

    /** Tổng giá trị giao dịch tích luỹ (gross merchandise value). */
    @Column(precision = 15, scale = 2)
    private BigDecimal totalGmv;

    private Instant lastTransactionAt;

    private Instant lastUpdatedAt;

    private Instant createdAt;

    /** Optimistic locking: bản ghi bị nhiều event cập nhật đồng thời. */
    @Version
    private Long version;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.lastUpdatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.lastUpdatedAt = Instant.now();
    }
}
