package com.yourapp.ticketpass.notification.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.yourapp.ticketpass.notification.domain.Notification;
import com.yourapp.ticketpass.notification.domain.NotificationRepository;
import com.yourapp.ticketpass.notification.domain.NotificationType;
import com.yourapp.ticketpass.shared.exception.ResourceNotFoundException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @InjectMocks
    private NotificationService notificationService;

    private Notification testNotification;

    @BeforeEach
    void setUp() {
        testNotification = new Notification(10L, "Tiêu đề test", "Nội dung test", NotificationType.ESCROW_PAID, "/tickets/1");
        testNotification.setId(100L);
    }

    @Test
    @DisplayName("sendNotification: Tạo thông báo thành công")
    void testSendNotificationSuccess() {
        when(notificationRepository.save(any(Notification.class))).thenReturn(testNotification);

        Notification result = notificationService.sendNotification(10L, "Tiêu đề", "Nội dung", NotificationType.ESCROW_PAID, "/tickets/1");

        assertNotNull(result);
        assertEquals(10L, result.getUserId());
        assertEquals("Tiêu đề test", result.getTitle());
        verify(notificationRepository).save(any(Notification.class));
    }

    @Test
    @DisplayName("sendNotification: Bỏ qua nếu userId null")
    void testSendNotificationNullUserId() {
        Notification result = notificationService.sendNotification(null, "Tiêu đề", "Nội dung", NotificationType.ESCROW_PAID, "/tickets/1");
        assertNull(result);
        verify(notificationRepository, never()).save(any(Notification.class));
    }

    @Test
    @DisplayName("getRecentNotifications: Trả về danh sách thông báo")
    void testGetRecentNotifications() {
        when(notificationRepository.findRecentByUserId(10L, 5)).thenReturn(List.of(testNotification));

        List<Notification> list = notificationService.getRecentNotifications(10L, 5);

        assertEquals(1, list.size());
        verify(notificationRepository).findRecentByUserId(10L, 5);
    }

    @Test
    @DisplayName("getUnreadCount: Đếm số lượng chưa đọc")
    void testGetUnreadCount() {
        when(notificationRepository.countUnreadByUserId(10L)).thenReturn(3L);

        long count = notificationService.getUnreadCount(10L);

        assertEquals(3L, count);
        verify(notificationRepository).countUnreadByUserId(10L);
    }

    @Test
    @DisplayName("markAsRead: Đánh dấu đã đọc thành công cho chính chủ")
    void testMarkAsReadSuccess() {
        when(notificationRepository.findById(100L)).thenReturn(Optional.of(testNotification));
        when(notificationRepository.save(testNotification)).thenReturn(testNotification);

        Notification updated = notificationService.markAsRead(100L, 10L);

        assertTrue(updated.isRead());
        assertNotNull(updated.getReadAt());
        verify(notificationRepository).save(testNotification);
    }

    @Test
    @DisplayName("markAsRead: Ném AccessDeniedException khi User khác cố đọc (Chống IDOR)")
    void testMarkAsReadIdorProtection() {
        when(notificationRepository.findById(100L)).thenReturn(Optional.of(testNotification));

        // User 99L cố tình đọc trộm/đánh dấu thông báo của User 10L
        assertThrows(AccessDeniedException.class, () -> {
            notificationService.markAsRead(100L, 99L);
        });

        verify(notificationRepository, never()).save(any(Notification.class));
    }

    @Test
    @DisplayName("markAsRead: Ném ResourceNotFoundException nếu không tìm thấy id")
    void testMarkAsReadNotFound() {
        when(notificationRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> {
            notificationService.markAsRead(999L, 10L);
        });
    }

    @Test
    @DisplayName("markAllAsRead: Đánh dấu tất cả đã đọc")
    void testMarkAllAsRead() {
        when(notificationRepository.markAllAsReadByUserId(10L)).thenReturn(5);

        int updated = notificationService.markAllAsRead(10L);

        assertEquals(5, updated);
        verify(notificationRepository).markAllAsReadByUserId(10L);
    }
}
