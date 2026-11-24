package com.yourapp.ticketpass.user.domain;

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
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(nullable = false, length = 100)
    private String passwordHash;

    @Column(length = 150)
    private String fullName;

    @Column(length = 20)
    private String phoneNumber;

    @Enumerated(EnumType.STRING)
    @Column(length = 40)
    private UserRole role;

    @Enumerated(EnumType.STRING)
    @Column(length = 40)
    private UserStatus status;

    private boolean emailVerified;

    private boolean phoneVerified;

    private boolean identityVerified;

    @Column(length = 500)
    private String avatarStorageKey;

    @Column(length = 100)
    private String bankName;

    @Column(length = 50)
    private String bankAccountNumber;

    @Column(length = 150)
    private String bankAccountHolder;

    private Instant lastLoginAt;

    private Instant createdAt;

    private Instant updatedAt;

    @Column(name = "moderation_reason", length = 500)
    private String moderationReason;

    @Column(name = "moderated_at")
    private Instant moderatedAt;

    @Column(name = "moderated_by_admin_id")
    private Long moderatedByAdminId;

    @Column(name = "permanently_banned", nullable = false)
    private boolean permanentlyBanned;

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
