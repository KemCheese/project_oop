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

/**
 * Entity trung tam cua san pass ve: mot ve dang duoc rao ban hoac dang trong mot giao dich.
 *
 * <p>Ve thuoc so huu cua module {@code ticket}. Theo nguyen tac kien truc so 1,
 * moi tham chieu sang ban ghi khac deu la ID thuan kieu {@code Long}, khong dung quan he JPA:
 * {@code sellerId} va {@code buyerId} tro sang module {@code user},
 * {@code escrowTransactionId} va {@code depositId} tro sang module {@code payment}.
 * Nho vay cac module co the tien hoa doc lap va khong keo theo lazy-loading xuyen module.</p>
 *
 * <p>Entity nay chi chua field va getter/setter, khong chua business logic.
 * Viec doi {@code status} chi duoc thuc hien qua
 * {@code com.yourapp.ticketpass.ticket.application.TicketStateMachineService}.
 * Truong {@code version} phuc vu optimistic locking vi mot ve co the bi nhieu nguoi mua
 * tranh nhau dat cung luc.</p>
 *
 * <p>Module: ticket</p>
 *
 * Chu so huu: Nguoi A
 */
@Entity
@Table(name = "tickets")
@Getter
@Setter
@NoArgsConstructor
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** ID thuan cua nguoi ban (module user). */
    @Column(nullable = false)
    private Long sellerId;

    /** ID thuan cua nguoi mua (module user); null khi ve chua duoc mua. */
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

    /** Thoi diem su kien bat dau; moi cua so khieu nai deu phai dong truoc moc nay. */
    private Instant eventStartAt;

    @Column(precision = 15, scale = 2)
    private BigDecimal originalPrice;

    @Column(precision = 15, scale = 2)
    private BigDecimal salePrice;

    /** Ten don vi phat hanh ve, vi du "CGV", "Ticketbox", "Duong sat Viet Nam". */
    @Column(length = 100)
    private String providerName;

    /** Ma don hang tai don vi phat hanh, dung de doi chieu voi email forward. */
    @Column(length = 120)
    private String providerOrderCode;

    /** Khoa file anh ve trong FileStorageService. */
    @Column(length = 500)
    private String ticketImageStorageKey;

    /** Khoa file anh ma QR trong FileStorageService. */
    @Column(length = 500)
    private String qrImageStorageKey;

    /** SHA-256 hex cua payload QR, khoa chong ban trung ve. */
    @Column(length = 64)
    private String qrPayloadHash;

    /** Huong dan chuyen ve da duoc sinh theo loai ve, luu duoi dang text dai. */
    @Column(columnDefinition = "TEXT")
    private String transferInstruction;

    /** ID thuan sang module payment, KHONG dung quan he JPA. */
    @Column
    private Long escrowTransactionId;

    /** ID thuan sang module payment, KHONG dung quan he JPA. */
    @Column
    private Long depositId;

    private Instant listedAt;

    private Instant transferredAt;

    private Instant disputeWindowClosesAt;

    private Instant completedAt;

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
