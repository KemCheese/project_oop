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
 * Bản ghi một giao dịch ký quỹ: người mua nộp 100% giá bán, sàn giữ hộ tới khi
 * giao dịch hoàn tất hoặc tranh chấp được xử lý.
 *
 * <p>Entity này lưu đầy đủ ảnh chụp (snapshot) phần tiền tại thời điểm giao dịch:
 * giá bán, hoa hồng phía người mua, hoa hồng phía người bán, tổng hoa hồng, thuế đã khấu
 * và số thực nhận của người bán. Lưu snapshot thay vì tính lại giúp số liệu lịch sử không
 * bị thay đổi khi bảng phí trong {@code CommissionConfig} được điều chỉnh về sau.</p>
 *
 * <p><b>Nguyên tắc kiến trúc số 1:</b> không dùng bất kỳ quan hệ JPA nào
 * ({@code @ManyToOne}, {@code @OneToMany}, {@code @OneToOne}, {@code @JoinColumn}).
 * Mọi tham chiếu tới vé và người dùng chỉ là ID thuần {@code Long}.</p>
 *
 * <p>Entity chỉ chứa field và getter/setter, không chứa logic nghiệp vụ.
 * Toàn bộ tính toán nằm ở lớp {@code payment.application}.</p>
 *
 * <p>Khoá lạc quan {@code @Version} là bắt buộc: đây là luồng tiền, phải chống
 * hai giao dịch đồng thời cùng giải ngân hoặc cùng hoàn tiền một escrow.</p>
 *
 * <p>Module: payment</p>
 *
 * <p>Chủ sở hữu: Người B</p>
 */
@Entity
@Table(name = "escrow_transactions")
@Getter
@Setter
@NoArgsConstructor
public class EscrowTransaction {

    /** Khoá chính, sinh tự động bởi cơ sở dữ liệu. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** ID của vé được giao dịch (ID thuần, không phải quan hệ JPA). */
    @Column(name = "ticket_id", nullable = false)
    private Long ticketId;

    /** ID của người mua đã nộp tiền vào escrow. */
    @Column(name = "buyer_id", nullable = false)
    private Long buyerId;

    /** ID của người bán sẽ nhận tiền khi giao dịch hoàn tất. */
    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    /** Trạng thái hiện tại của giao dịch ký quỹ. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 40)
    private EscrowStatus status;

    /** Giá bán vé — số tiền người mua phải nộp vào escrow (100% giá bán). */
    @Column(name = "sale_price", precision = 15, scale = 2)
    private BigDecimal salePrice;

    /** Phần hoa hồng do người mua chịu (một nửa tổng hoa hồng). */
    @Column(name = "buyer_commission", precision = 15, scale = 2)
    private BigDecimal buyerCommission;

    /** Phần hoa hồng do người bán chịu (một nửa tổng hoa hồng). */
    @Column(name = "seller_commission", precision = 15, scale = 2)
    private BigDecimal sellerCommission;

    /** Tổng hoa hồng sàn thu được của giao dịch này. */
    @Column(name = "total_commission", precision = 15, scale = 2)
    private BigDecimal totalCommission;

    /** Số thuế đã khấu trừ của người bán (giá trị mô phỏng, xem {@code TaxWithholdingService}). */
    @Column(name = "tax_withheld", precision = 15, scale = 2)
    private BigDecimal taxWithheld;

    /** Số tiền người bán thực nhận sau khi trừ hoa hồng và thuế. */
    @Column(name = "seller_net_payout", precision = 15, scale = 2)
    private BigDecimal sellerNetPayout;

    /** Số tiền sàn đang thực giữ tại thời điểm hiện tại. */
    @Column(name = "amount_held", precision = 15, scale = 2)
    private BigDecimal amountHeld;

    /** Số tiền đã hoàn lại cho người mua (dùng cho cả hoàn toàn phần và hoàn một phần). */
    @Column(name = "amount_refunded", precision = 15, scale = 2)
    private BigDecimal amountRefunded;

    /** Mã bậc phí đã áp dụng, lấy từ tên hằng số của {@link CommissionTier}. */
    @Column(name = "tier_code", length = 40)
    private String tierCode;

    /** Tên cổng thanh toán đã xử lý giao dịch, ví dụ {@code "MANUAL_TRANSFER"}. */
    @Column(name = "gateway_name", length = 60)
    private String gatewayName;

    /** Mã giao dịch do cổng thanh toán cấp; dùng để đối soát, hoàn tiền và xác thực IPN. */
    @Column(name = "gateway_transaction_id", length = 120)
    private String gatewayTransactionId;

    /** Lý do thất bại do cổng thanh toán trả về, chỉ có giá trị khi trạng thái là {@code FAILED}. */
    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    /** Thời điểm cổng thanh toán xác nhận đã thu tiền và sàn bắt đầu giữ tiền. */
    @Column(name = "held_at")
    private Instant heldAt;

    /** Thời điểm tiền được giải ngân cho người bán. */
    @Column(name = "released_at")
    private Instant releasedAt;

    /** Thời điểm tiền được hoàn lại cho người mua. */
    @Column(name = "refunded_at")
    private Instant refundedAt;

    /** Thời điểm bản ghi được tạo, gán tự động trong {@link #onCreate()}. */
    @Column(name = "created_at")
    private Instant createdAt;

    /** Thời điểm bản ghi được cập nhật lần cuối, gán tự động trong {@link #onUpdate()}. */
    @Column(name = "updated_at")
    private Instant updatedAt;

    /** Khoá lạc quan chống cập nhật đồng thời trên cùng một giao dịch tiền. */
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
