package com.yourapp.ticketpass.user.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Bản ghi đánh dấu sao (Bookmark / Watchlist) một vé của người dùng.
 *
 * <p>Theo Nguyên tắc Scalability số 1:
 * - {@code userId} là ID thuần sang bảng {@code users}.
 * - {@code ticketId} là ID thuần sang bảng {@code tickets} (module {@code ticket}).
 * Tuyệt đối không dùng quan hệ JPA xuyên module (không dùng @ManyToOne Ticket).</p>
 *
 * <p>Module: user</p>
 * <p>Chủ sở hữu: Người C</p>
 */
@Entity
@Table(name = "user_starred_tickets")
@Getter
@Setter
@NoArgsConstructor
public class StarredTicket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** ID thuần của người dùng sở hữu bookmark này. */
    @Column(nullable = false)
    private Long userId;

    /** ID thuần của vé được đánh dấu sao. */
    @Column(nullable = false)
    private Long ticketId;

    /** Thời điểm người dùng thực hiện đánh dấu sao. */
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public StarredTicket(Long userId, Long ticketId) {
        this.userId = userId;
        this.ticketId = ticketId;
    }

    @PrePersist
    void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }
}
