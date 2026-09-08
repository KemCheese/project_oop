package com.yourapp.ticketpass.dispute.domain;

import java.math.BigDecimal;
import java.time.Instant;

import com.yourapp.ticketpass.shared.event.DisputeOutcome;

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
 * Hồ sơ một khiếu nại: người mua tố cáo vé có vấn đề, sàn giữ tiền lại và admin phân xử.
 *
 * <p><b>Nguyên tắc kiến trúc số 1:</b> không dùng bất kỳ quan hệ JPA nào
 * ({@code @ManyToOne}, {@code @OneToMany}, {@code @OneToOne}, {@code @JoinColumn}). Tham chiếu
 * tới vé, giao dịch ký quỹ, khoản cọc và người dùng đều chỉ là ID thuần {@code Long}.
 * Bằng chứng của khiếu nại được lấy qua {@code DisputeEvidenceRepository.findByDisputeId(...)}
 * chứ không qua tập hợp {@code @OneToMany}.</p>
 *
 * <p><b>Ba mốc thời gian và ý nghĩa của chúng:</b></p>
 * <ul>
 *   <li>{@code filedAt} — lúc người mua nộp khiếu nại.</li>
 *   <li>{@code windowClosesAt} — hạn cuối để người mua được mở khiếu nại, tính bởi
 *       {@link DisputeWindowPolicy} theo loại vé và luôn không muộn hơn giờ diễn ra sự kiện.</li>
 *   <li>{@code sellerResponseDeadline} — hạn cuối để người bán giải trình; quá hạn thì hồ sơ
 *       chuyển sang {@code EXPIRED} bởi {@code AutoCompleteScheduler}.</li>
 * </ul>
 *
 * <p>Ba trường tiền {@code totalDeposit}, {@code founderCut}, {@code buyerCompensation} lưu
 * snapshot kết quả của {@code ForfeitDepositPolicy} tại thời điểm phán quyết, chỉ có giá trị
 * khi người mua thắng và cọc bị tịch thu.</p>
 *
 * <p>Entity chỉ chứa field và getter/setter, không chứa logic nghiệp vụ. Khoá lạc quan
 * {@code @Version} chống việc hai admin cùng phán quyết một khiếu nại.</p>
 *
 * <p>Module: dispute</p>
 *
 * <p>Chủ sở hữu: Người B</p>
 */
@Entity
@Table(name = "disputes")
@Getter
@Setter
@NoArgsConstructor
public class Dispute {

    /** Khoá chính, sinh tự động bởi cơ sở dữ liệu. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** ID của vé bị khiếu nại (ID thuần, không phải quan hệ JPA). */
    @Column(name = "ticket_id", nullable = false)
    private Long ticketId;

    /** ID của người mua đứng ra khiếu nại. */
    @Column(name = "buyer_id", nullable = false)
    private Long buyerId;

    /** ID của người bán bị khiếu nại. */
    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    /** ID giao dịch ký quỹ liên quan, để biết khoản tiền nào đang bị giữ lại. */
    @Column(name = "escrow_transaction_id")
    private Long escrowTransactionId;

    /** ID khoản cọc liên quan, để biết khoản cọc nào sẽ bị tịch thu nếu người bán thua. */
    @Column(name = "deposit_id")
    private Long depositId;

    /** Trạng thái hiện tại của khiếu nại. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 40)
    private DisputeStatus status;

    /** Lý do khiếu nại do người mua chọn. */
    @Enumerated(EnumType.STRING)
    @Column(name = "reason", length = 40)
    private DisputeReason reason;

    /** Mô tả chi tiết vấn đề do người mua trình bày. */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /** Thời điểm người mua nộp khiếu nại. */
    @Column(name = "filed_at")
    private Instant filedAt;

    /** Hạn cuối được mở khiếu nại, tính bởi {@link DisputeWindowPolicy} theo loại vé. */
    @Column(name = "window_closes_at")
    private Instant windowClosesAt;

    /** Hạn cuối để người bán gửi giải trình. */
    @Column(name = "seller_response_deadline")
    private Instant sellerResponseDeadline;

    /** Thời điểm người bán thực sự gửi giải trình. */
    @Column(name = "seller_responded_at")
    private Instant sellerRespondedAt;

    /** Nội dung giải trình của người bán. */
    @Column(name = "seller_response", columnDefinition = "TEXT")
    private String sellerResponse;

    /** ID admin đã xem xét và phán quyết khiếu nại. */
    @Column(name = "reviewed_by_admin_id")
    private Long reviewedByAdminId;

    /** Thời điểm admin ra phán quyết. */
    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    /** Ghi chú phán quyết của admin, giải thích căn cứ để hai bên đọc được. */
    @Column(name = "resolution_note", columnDefinition = "TEXT")
    private String resolutionNote;

    /** Kết quả phán quyết cuối cùng của khiếu nại. */
    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", length = 40)
    private DisputeOutcome outcome;

    /** Tổng tiền cọc của người bán tại thời điểm phán quyết. */
    @Column(name = "total_deposit", precision = 15, scale = 2)
    private BigDecimal totalDeposit;

    /** Phần cọc sàn giữ lại khi tịch thu; snapshot của {@code ForfeitBreakdown.founderCut}. */
    @Column(name = "founder_cut", precision = 15, scale = 2)
    private BigDecimal founderCut;

    /** Phần cọc bồi thường cho người mua; snapshot của {@code ForfeitBreakdown.buyerCompensation}. */
    @Column(name = "buyer_compensation", precision = 15, scale = 2)
    private BigDecimal buyerCompensation;

    /** Thời điểm bản ghi được tạo, gán tự động trong {@link #onCreate()}. */
    @Column(name = "created_at")
    private Instant createdAt;

    /** Thời điểm bản ghi được cập nhật lần cuối, gán tự động trong {@link #onUpdate()}. */
    @Column(name = "updated_at")
    private Instant updatedAt;

    /** Khoá lạc quan chống hai admin cùng phán quyết một khiếu nại. */
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
