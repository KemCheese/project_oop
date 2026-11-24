package com.yourapp.ticketpass.wallet.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "account_liquidations")
@Getter
@Setter
@NoArgsConstructor
public class AccountLiquidation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "trigger_key", nullable = false, unique = true, length = 100)
    private String triggerKey;

    @Column(name = "gross_balance", nullable = false, precision = 15, scale = 2)
    private BigDecimal grossBalance;

    @Column(name = "fee_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal feeAmount;

    @Column(name = "net_payout_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal netPayoutAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private LiquidationStatus status;

    @Column(name = "bank_name", length = 100)
    private String bankName;

    @Column(name = "bank_account_number_masked", length = 60)
    private String bankAccountNumberMasked;

    @Column(name = "bank_account_holder", length = 150)
    private String bankAccountHolder;

    @Column(name = "cancelled_ticket_count", nullable = false)
    private int cancelledTicketCount;

    @Column(name = "reason", nullable = false, length = 500)
    private String reason;

    @Column(name = "liquidated_at", nullable = false, updatable = false)
    private Instant liquidatedAt;

    @PrePersist
    void onCreate() {
        if (this.liquidatedAt == null) {
            this.liquidatedAt = Instant.now();
        }
    }
}
