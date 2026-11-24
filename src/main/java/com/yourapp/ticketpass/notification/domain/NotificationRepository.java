package com.yourapp.ticketpass.notification.domain;

import java.util.List;
import java.util.Optional;

public interface NotificationRepository {

    Notification save(Notification notification);

    Optional<Notification> findById(Long id);

    List<Notification> findRecentByUserId(Long userId, int limit);

    List<Notification> findByUserIdPaged(Long userId, int page, int size);

    long countByUserId(Long userId);

    long countUnreadByUserId(Long userId);

    int markAllAsReadByUserId(Long userId);
}
