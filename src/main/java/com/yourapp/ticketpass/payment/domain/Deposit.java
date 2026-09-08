package com.yourapp.ticketpass.payment.domain;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Bản ghi khoản tiền cọc của người bán — khoản bảo đảm 50% giá bán bị khoá cho tới khi
 * giao dịch kết thúc.
 *
 * <p>Cọc được hoàn đủ cho người bán khi giao dịch thành công, và bị tịch thu khi người bán
 * thua tranh chấp. Khi bị tịch thu, hai trường {@code founderCut} và {@code buyerCompensation}
 * lưu snapshot kết quả tính của {@code ForfeitDepositPolicy}:</p>
 * <pre>
 * founder_cut         = min(tong_coc, max(5.000d, 7% * tong_coc))
 * buyer_compensation  = tong_coc - founder_cut
 * </pre>
 *
 * <p><b>Nguyên tắc kiến trúc số 1:</b> không dùng bất kỳ quan hệ JPA nào
 * ({@code @ManyToOne}, {@code @OneToMany}, {@code @OneToOne}, {@code @JoinColumn}).
 * Tham chiếu tới vé và người bán chỉ là ID thuần {@code Long}.</p>
 *
 * <p>Entity chỉ chứa field và getter/setter, không chứa logic nghiệp vụ.</p>
 *
 * <p>Khoá lạc quan {@code @Version} là bắt buộc để không thể vừa hoàn cọc vừa tịch thu cọc
 * do hai giao dịch chạy đồng thời.</p>
 *
 * <p>Module: payment</p>
 *
 * <p>Chủ sở hữu: Người B</p>
 */
@Entity
@Table(name = "deposits")
@Getter
@Setter
@NoArgsConstructor
public class Deposit {

    /** Khoá chính, sinh tự động bởi cơ sở dữ liệu. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** ID của vé mà khoản cọc này bảo đảm (ID thuần, không phải quan hệ JPA). */
    @Column(name = "ticket_id", nullable = false)
    private Long ticketId;

    /** ID của người bán đã khoá cọc. */
    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    /** Trạng thái hiện tại của khoản cọc. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 40)
    private DepositStatus status;

    /** Tỷ lệ cọc so với giá bán, tính theo phần trăm; mặc định 50 (đọc từ {@code app.deposit.percent}). */
    @Column(name = "deposit_percent", precision = 5, scale = 2)
    private BigDecimal depositPercent;

    /** Số tiền cọc thực tế bị khoá. */
    @Column(name = "amount", precision = 15, scale = 2)
    private BigDecimal amount;

    /** Phần cọc sàn giữ lại khi tịch thu; snapshot của {@code ForfeitBreakdown.founderCut}. */
    @Column(name = "founder_cut", precision = 15, scale = 2)
    private BigDecimal founderCut;

    /** Phần cọc bồi thường cho người mua khi tịch thu; snapshot của {@code ForfeitBreakdown.buyerCompensation}. */
    @Column(name = "buyer_compensation", precision = 15, scale = 2)
    private BigDecimal buyerCompensation;

    /** Tên cổng thanh toán đã xử lý việc khoá cọc, ví dụ {@code "MOMO_TEST"}. */
    @Column(name = "gateway_name", length = 60)
    private String gatewayName;

    /** Mã giao dịch do cổng thanh toán cấp; dùng để hoàn cọc và đối soát. */
    @Column(name = "gateway_transaction_id", length = 120)
    private String gatewayTransactionId;

    /** Lý do tịch thu cọc, thường là ghi chú phán quyết tranh chấp của admin. */
    @Column(name = "forfeit_reason", length = 500)
    private String forfeitReason;

    /** Thời điểm cọc được khoá thành công. */
    @Column(name = "locked_at")
    private Instant lockedAt;

    /** Thời điểm cọc được hoàn lại cho người bán. */
    @Column(name = "refunded_at")
    private Instant refundedAt;

    /** Thời điểm cọc bị tịch thu. */
    @Column(name = "forfeited_at")
    private Instant forfeitedAt;

    /** Thời điểm bản ghi được tạo, gán tự động trong {@link #onCreate()}. */
    @Column(name = "created_at")
    private Instant createdAt;

    /** Thời điểm bản ghi được cập nhật lần cuối, gán tự động trong {@link #onUpdate()}. */
    @Column(name = "updated_at")
    private Instant updatedAt;

    /** Khoá lạc quan chống cập nhật đồng thời trên cùng một khoản cọc. */
    @Version
    private Long version;

    /** Gán {@code createdAt} và {@code updatedAt} ngay trước khi bản ghi được chèn mới. */
    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** Cập nhật {@code updatedAt} ngay trước mỗi lần bản ghi được ghi đè. */
    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
