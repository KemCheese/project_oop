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
@Table(name = "escrow_transactions")
@Getter
@Setter
@NoArgsConstructor
public class EscrowTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticket_id", nullable = false)
    private Long ticketId;

    @Column(name = "buyer_id", nullable = false)
    private Long buyerId;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 40)
    private EscrowStatus status;

    @Column(name = "sale_price", precision = 15, scale = 2)
    private BigDecimal salePrice;

    @Column(name = "buyer_commission", precision = 15, scale = 2)
    private BigDecimal buyerCommission;

    @Column(name = "seller_commission", precision = 15, scale = 2)
    private BigDecimal sellerCommission;

    @Column(name = "total_commission", precision = 15, scale = 2)
    private BigDecimal totalCommission;

    @Column(name = "tax_withheld", precision = 15, scale = 2)
    private BigDecimal taxWithheld;

    @Column(name = "seller_net_payout", precision = 15, scale = 2)
    private BigDecimal sellerNetPayout;

    @Column(name = "amount_held", precision = 15, scale = 2)
    private BigDecimal amountHeld;

    @Column(name = "amount_refunded", precision = 15, scale = 2)
    private BigDecimal amountRefunded;

    @Column(name = "tier_code", length = 40)
    private String tierCode;

    @Column(name = "gateway_name", length = 60)
    private String gatewayName;

    @Column(name = "gateway_transaction_id", length = 120)
    private String gatewayTransactionId;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "held_at")
    private Instant heldAt;

    @Column(name = "released_at")
    private Instant releasedAt;

    @Column(name = "refunded_at")
    private Instant refundedAt;

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
