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

@Entity
@Table(name = "disputes")
@Getter
@Setter
@NoArgsConstructor
public class Dispute {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticket_id", nullable = false)
    private Long ticketId;

    @Column(name = "buyer_id", nullable = false)
    private Long buyerId;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Column(name = "escrow_transaction_id")
    private Long escrowTransactionId;

    @Column(name = "deposit_id")
    private Long depositId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 40)
    private DisputeStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", length = 40)
    private DisputeReason reason;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "filed_at")
    private Instant filedAt;

    @Column(name = "window_closes_at")
    private Instant windowClosesAt;

    @Column(name = "seller_response_deadline")
    private Instant sellerResponseDeadline;

    @Column(name = "seller_responded_at")
    private Instant sellerRespondedAt;

    @Column(name = "seller_response", columnDefinition = "TEXT")
    private String sellerResponse;

    @Column(name = "reviewed_by_admin_id")
    private Long reviewedByAdminId;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "resolution_note", columnDefinition = "TEXT")
    private String resolutionNote;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", length = 40)
    private DisputeOutcome outcome;

    @Column(name = "total_deposit", precision = 15, scale = 2)
    private BigDecimal totalDeposit;

    @Column(name = "founder_cut", precision = 15, scale = 2)
    private BigDecimal founderCut;

    @Column(name = "buyer_compensation", precision = 15, scale = 2)
    private BigDecimal buyerCompensation;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Version
    private Long version;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
