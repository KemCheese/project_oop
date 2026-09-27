package com.yourapp.ticketpass.verification.domain;

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
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * [DEPRECATED] Module verification da bi loai bo khoi pham vi du an.
 * Du an chuyen huong sang mo hinh marketplace. Xem 00-tong-quan-du-an.md.
 *
 * Ho so xac thuc cua mot ve: ket qua OCR, kiem tra trung ma QR, va bang chung email forward.
 *
 * <p>Theo nguyen tac kien truc so 1, entity nay khong dung quan he JPA: {@code ticketId} va
 * {@code duplicateOfTicketId} la ID thuan tro sang module {@code ticket}, con
 * {@code verifiedByAdminId} la ID thuan tro sang module {@code user}.</p>
 *
 * <p>Entity chi chua field va getter/setter, khong chua business logic; moi quyet dinh duyet hay
 * tu choi deu nam o tang {@code application}.</p>
 *
 * <p>Module: verification</p>
 *
 * Chu so huu: Nguoi A
 */
@Entity
@Table(name = "ticket_verifications")
@Getter
@Setter
@NoArgsConstructor
public class TicketVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** ID thuan sang module ticket, KHONG dung quan he JPA. */
    @Column(nullable = false)
    private Long ticketId;

    @Enumerated(EnumType.STRING)
    @Column(length = 40)
    private VerificationStatus status;

    @Enumerated(EnumType.STRING)
    @Column(length = 40)
    private RiskTier riskTier;

    /** Do tin cay cua OCR, thang diem phan tram. */
    @Column(precision = 5, scale = 2)
    private BigDecimal ocrConfidence;

    /** Toan bo van ban OCR doc duoc tu anh ve, giu lai de doi soat khi co tranh chap. */
    @Column(columnDefinition = "TEXT")
    private String ocrExtractedText;

    /** SHA-256 hex cua payload QR, dung de phat hien ban trung ve. */
    @Column(length = 64)
    private String qrPayloadHash;

    /** Perceptual hash cua anh ve, bat truong hop anh bi crop hoac chinh nhe. */
    @Column(length = 64)
    private String imagePerceptualHash;

    /** ID thuan cua ve bi trung neu phat hien ban trung; null neu khong trung. */
    @Column
    private Long duplicateOfTicketId;

    private Boolean emailForwardVerified;

    @Column(length = 255)
    private String emailForwardFrom;

    @Column(length = 255)
    private String emailForwardSubject;

    private Instant emailForwardReceivedAt;

    /** Khoa file bang chung bo sung trong FileStorageService. */
    @Column(length = 500)
    private String evidenceStorageKey;

    @Column(length = 500)
    private String rejectionReason;

    /** ID thuan sang module user: admin da duyet ho so nay. */
    @Column
    private Long verifiedByAdminId;

    private Instant createdAt;

    private Instant updatedAt;

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
