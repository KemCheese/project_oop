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

@Entity
@Table(name = "deposits")
@Getter
@Setter
@NoArgsConstructor
public class Deposit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticket_id", nullable = false)
    private Long ticketId;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 40)
    private DepositStatus status;

    @Column(name = "deposit_percent", precision = 5, scale = 2)
    private BigDecimal depositPercent;

    @Column(name = "amount", precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "founder_cut", precision = 15, scale = 2)
    private BigDecimal founderCut;

    @Column(name = "buyer_compensation", precision = 15, scale = 2)
    private BigDecimal buyerCompensation;

    @Column(name = "gateway_name", length = 60)
    private String gatewayName;

    @Column(name = "gateway_transaction_id", length = 120)
    private String gatewayTransactionId;

    @Column(name = "forfeit_reason", length = 500)
    private String forfeitReason;

    @Column(name = "locked_at")
    private Instant lockedAt;

    @Column(name = "refunded_at")
    private Instant refundedAt;

    @Column(name = "forfeited_at")
    private Instant forfeitedAt;

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
