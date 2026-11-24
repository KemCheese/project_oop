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

@Entity
@Table(name = "user_reputation_scores")
@Getter
@Setter
@NoArgsConstructor
public class UserReputationScore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long userId;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal score;

    private Integer totalTransactions;

    private Integer successfulTransactions;

    private Integer cancelledListings;

    private Integer disputesOpenedAgainst;

    private Integer disputesLost;

    private Integer disputesWon;

    @Column(precision = 15, scale = 2)
    private BigDecimal totalGmv;

    private Instant lastTransactionAt;

    private Instant lastUpdatedAt;

    private Instant createdAt;

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
