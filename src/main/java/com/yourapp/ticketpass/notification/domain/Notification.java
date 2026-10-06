package com.yourapp.ticketpass.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * Bản ghi thông báo giao dịch gửi tới người dùng.
 *
 * <p>Tuân thủ Nguyên tắc Scalability số 1:
 * - {@code userId} là ID thuần trỏ sang bảng {@code users}, không dùng JPA @ManyToOne.
 * - Hoàn toàn Decoupled khỏi module khác.</p>
 *
 * <p>Module: notification</p>
 * <p>Chủ sở hữu: Người C (Dev C)</p>
 */
@Entity
@Table(name = "notifications")
@Getter
@Setter
@NoArgsConstructor
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** ID thuần của người dùng nhận thông báo. */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** Tiêu đề ngắn gọn của thông báo. */
    @Column(nullable = false)
    private String title;

    /** Nội dung chi tiết của thông báo. */
    @Column(nullable = false, length = 1000)
    private String content;

    /** Phân loại nghiệp vụ của thông báo. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private NotificationType type;

    /** Đường dẫn điều hướng khi người dùng bấm vào thông báo (ví dụ /tickets/10 hay /disputes/3). */
    @Column(name = "target_url")
    private String targetUrl;

    /** Trạng thái đã đọc hay chưa. */
    @Column(name = "is_read", nullable = false)
    private boolean isRead = false;

    /** Thời điểm thông báo được tạo. */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Thời điểm người dùng bấm xem / đánh dấu đã đọc. */
    @Column(name = "read_at")
    private Instant readAt;

    public Notification(Long userId, String title, String content, NotificationType type, String targetUrl) {
        this.userId = userId;
        this.title = title;
        this.content = content;
        this.type = type;
        this.targetUrl = targetUrl;
        this.isRead = false;
    }

    /**
     * Nghiệp vụ domain: đánh dấu thông báo đã đọc.
     */
    public void markAsRead() {
        if (!this.isRead) {
            this.isRead = true;
            this.readAt = Instant.now();
        }
    }

    @PrePersist
    void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }
}
