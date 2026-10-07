package com.yourapp.ticketpass.notification.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.yourapp.ticketpass.notification.application.NotificationService;
import com.yourapp.ticketpass.notification.domain.Notification;
import com.yourapp.ticketpass.notification.domain.NotificationType;
import com.yourapp.ticketpass.notification.web.dto.NotificationListResponse;
import com.yourapp.ticketpass.notification.web.dto.NotificationResponse;
import com.yourapp.ticketpass.user.application.UserQueryService;
import com.yourapp.ticketpass.user.domain.User;
import com.yourapp.ticketpass.user.domain.UserRole;
import com.yourapp.ticketpass.user.domain.UserStatus;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationApiController Unit Tests")
class NotificationApiControllerTest {

    @Mock
    private NotificationService notificationService;

    @Mock
    private UserQueryService userQueryService;

    @Mock
    private Principal principal;

    @InjectMocks
    private NotificationApiController controller;

    private User buildUser(Long id, String email) {
        User u = new User();
        u.setId(id);
        u.setEmail(email);
        u.setFullName("Nguyễn Văn A");
        u.setRole(UserRole.USER);
        u.setStatus(UserStatus.ACTIVE);
        return u;
    }

    @Test
    @DisplayName("getRecent: Trả về 401 nếu chưa đăng nhập")
    void testGetRecentUnauthenticated() {
        ResponseEntity<NotificationListResponse> response = controller.getRecent(10, null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("getRecent: Trả về danh sách thông báo và số lượng chưa đọc khi đã đăng nhập")
    void testGetRecentAuthenticated() {
        when(principal.getName()).thenReturn("user@test.com");
        User user = buildUser(15L, "user@test.com");
        when(userQueryService.findByEmail("user@test.com")).thenReturn(Optional.of(user));

        Notification notif = new Notification(15L, "Vé bán thành công", "Chi tiết vé", NotificationType.ESCROW_PAID, "/tickets/1");
        notif.setId(101L);

        when(notificationService.getUnreadCount(15L)).thenReturn(1L);
        when(notificationService.getTotalCount(15L)).thenReturn(5L);
        when(notificationService.getRecentNotifications(15L, 10)).thenReturn(List.of(notif));

        ResponseEntity<NotificationListResponse> response = controller.getRecent(10, principal);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getUnreadCount()).isEqualTo(1L);
        assertThat(response.getBody().getTotalCount()).isEqualTo(5L);
        assertThat(response.getBody().getNotifications()).hasSize(1);
        assertThat(response.getBody().getNotifications().get(0).getTitle()).isEqualTo("Vé bán thành công");
    }

    @Test
    @DisplayName("getUnreadCount: Trả về số lượng chưa đọc cho badge Polling")
    void testGetUnreadCount() {
        when(principal.getName()).thenReturn("user@test.com");
        User user = buildUser(15L, "user@test.com");
        when(userQueryService.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(notificationService.getUnreadCount(15L)).thenReturn(4L);

        ResponseEntity<Map<String, Object>> response = controller.getUnreadCount(principal);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("unreadCount")).isEqualTo(4L);
    }

    @Test
    @DisplayName("markAsRead: Đánh dấu 1 thông báo đã đọc")
    void testMarkAsRead() {
        when(principal.getName()).thenReturn("user@test.com");
        User user = buildUser(15L, "user@test.com");
        when(userQueryService.findByEmail("user@test.com")).thenReturn(Optional.of(user));

        Notification notif = new Notification(15L, "Vé bán thành công", "Chi tiết vé", NotificationType.ESCROW_PAID, "/tickets/1");
        notif.setId(101L);
        notif.markAsRead();

        when(notificationService.markAsRead(101L, 15L)).thenReturn(notif);

        ResponseEntity<NotificationResponse> response = controller.markAsRead(101L, principal);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isRead()).isTrue();
    }

    @Test
    @DisplayName("markAllAsRead: Đánh dấu tất cả thông báo đã đọc")
    void testMarkAllAsRead() {
        when(principal.getName()).thenReturn("user@test.com");
        User user = buildUser(15L, "user@test.com");
        when(userQueryService.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(notificationService.markAllAsRead(15L)).thenReturn(3);

        ResponseEntity<Map<String, Object>> response = controller.markAllAsRead(principal);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("success")).isEqualTo(true);
        assertThat(response.getBody().get("updatedCount")).isEqualTo(3);
    }
}
