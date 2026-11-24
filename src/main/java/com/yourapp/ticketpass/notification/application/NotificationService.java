package com.yourapp.ticketpass.notification.application;

import com.yourapp.ticketpass.notification.domain.Notification;
import com.yourapp.ticketpass.notification.domain.NotificationRepository;
import com.yourapp.ticketpass.notification.domain.NotificationType;
import com.yourapp.ticketpass.shared.exception.ResourceNotFoundException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Notification sendNotification(Long userId, String title, String content, NotificationType type, String targetUrl) {
        if (userId == null) {
            log.warn("Bỏ qua tạo thông báo vì userId bị null: title='{}'", title);
            return null;
        }
        Notification notification = new Notification(userId, title, content, type, targetUrl);
        Notification saved = notificationRepository.save(notification);
        log.info("Đã tạo thông báo id={} cho userId={} loại={}", saved.getId(), userId, type);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Notification> getRecentNotifications(Long userId, int limit) {
        return notificationRepository.findRecentByUserId(userId, limit);
    }

    @Transactional(readOnly = true)
    public List<Notification> getAllNotificationsPaged(Long userId, int page, int size) {
        return notificationRepository.findByUserIdPaged(userId, page, size);
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(Long userId) {
        return notificationRepository.countUnreadByUserId(userId);
    }

    @Transactional(readOnly = true)
    public long getTotalCount(Long userId) {
        return notificationRepository.countByUserId(userId);
    }

    @Transactional
    public Notification markAsRead(Long notificationId, Long currentUserId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification", notificationId));

        if (!notification.getUserId().equals(currentUserId)) {
            log.warn("Chống IDOR: User {} cố ý đánh dấu đọc thông báo {} của User {}",
                    currentUserId, notificationId, notification.getUserId());
            throw new AccessDeniedException("Bạn không có quyền truy cập thông báo này.");
        }

        notification.markAsRead();
        return notificationRepository.save(notification);
    }

    @Transactional
    public int markAllAsRead(Long currentUserId) {
        return notificationRepository.markAllAsReadByUserId(currentUserId);
    }
}
