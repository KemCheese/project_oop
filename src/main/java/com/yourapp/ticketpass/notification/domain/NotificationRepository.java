package com.yourapp.ticketpass.notification.domain;

import java.util.List;
import java.util.Optional;

/**
 * Port (Interface) truy xuất dữ liệu thông báo ở tầng Domain.
 *
 * <p>Tuân thủ Repository Pattern: tách biệt hoàn toàn khỏi Spring Data JPA.
 * Tầng Application chỉ phụ thuộc vào interface này.</p>
 *
 * <p>Module: notification</p>
 * <p>Chủ sở hữu: Người C (Dev C)</p>
 */
public interface NotificationRepository {

    Notification save(Notification notification);

    Optional<Notification> findById(Long id);

    List<Notification> findRecentByUserId(Long userId, int limit);

    List<Notification> findByUserIdPaged(Long userId, int page, int size);

    long countByUserId(Long userId);

    long countUnreadByUserId(Long userId);

    int markAllAsReadByUserId(Long userId);
}
