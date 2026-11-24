package com.yourapp.ticketpass.notification.infrastructure;

import com.yourapp.ticketpass.notification.domain.Notification;
import com.yourapp.ticketpass.notification.domain.NotificationRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class NotificationRepositoryImpl implements NotificationRepository {

    private final NotificationJpaRepository jpaRepository;

    @Override
    public Notification save(Notification notification) {
        return jpaRepository.save(notification);
    }

    @Override
    public Optional<Notification> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public List<Notification> findRecentByUserId(Long userId, int limit) {
        return jpaRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, Math.max(1, limit)));
    }

    @Override
    public List<Notification> findByUserIdPaged(Long userId, int page, int size) {
        return jpaRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(Math.max(0, page), Math.max(1, size)));
    }

    @Override
    public long countByUserId(Long userId) {
        return jpaRepository.countByUserId(userId);
    }

    @Override
    public long countUnreadByUserId(Long userId) {
        return jpaRepository.countUnreadByUserId(userId);
    }

    @Override
    public int markAllAsReadByUserId(Long userId) {
        return jpaRepository.markAllAsReadByUserId(userId, Instant.now());
    }
}
