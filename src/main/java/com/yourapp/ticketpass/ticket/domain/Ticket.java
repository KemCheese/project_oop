package com.yourapp.ticketpass.ticket.domain;

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
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "tickets")
@Getter
@Setter
@NoArgsConstructor
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long sellerId;

    @Column
    private Long buyerId;

    @Enumerated(EnumType.STRING)
    @Column(length = 40)
    private TicketType ticketType;

    @Enumerated(EnumType.STRING)
    @Column(length = 40)
    private TicketStatus status;

    @Column(length = 200)
    private String title;

    @Column(length = 200)
    private String eventName;

    @Column(length = 255)
    private String venue;

    @Column(length = 120)
    private String seatInfo;

    private Instant eventStartAt;

    @Column(name = "event_end_at")
    private Instant eventEndAt;

    @Column(precision = 15, scale = 2)
    private BigDecimal originalPrice;

    @Column(precision = 15, scale = 2)
    private BigDecimal salePrice;

    @Column(length = 100)
    private String providerName;

    @Column(length = 120)
    private String providerOrderCode;

    @Column(length = 500)
    private String ticketImageStorageKey;

    @Column(length = 500)
    private String qrImageStorageKey;

    @Column(columnDefinition = "TEXT")
    private String transferInstruction;

    @Column
    private Long escrowTransactionId;

    @Column
    private Long depositId;

    private Instant listedAt;

    private Instant escrowedAt;

    private Instant transferredAt;

    private Instant receivedAt;

    private Instant disputeWindowClosesAt;

    private Instant completedAt;

    @Column(name = "reminded_24h_at")
    private Instant reminded24hAt;

    private Instant createdAt;

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
