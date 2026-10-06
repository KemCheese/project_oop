package com.yourapp.ticketpass.notification.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yourapp.ticketpass.notification.application.NotificationService;
import com.yourapp.ticketpass.notification.domain.Notification;
import com.yourapp.ticketpass.notification.domain.NotificationType;
import com.yourapp.ticketpass.user.application.UserQueryService;
import com.yourapp.ticketpass.user.domain.User;
import com.yourapp.ticketpass.user.domain.UserRole;
import com.yourapp.ticketpass.user.domain.UserStatus;
import java.security.Principal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationController Unit Tests")
class NotificationControllerTest {

    @Mock
    private NotificationService notificationService;

    @Mock
    private UserQueryService userQueryService;

    @Mock
    private Principal principal;

    @InjectMocks
    private NotificationController controller;

    private User buildUser(Long id, String email) {
        User u = new User();
        u.setId(id);
        u.setEmail(email);
        u.setFullName("Nguyễn Văn B");
        u.setRole(UserRole.USER);
        u.setStatus(UserStatus.ACTIVE);
        return u;
    }

    @Test
    @DisplayName("listNotifications: Chuyển hướng /login nếu chưa đăng nhập")
    void testListNotificationsUnauthenticated() {
        Model model = new ConcurrentModel();
        String view = controller.listNotifications(0, 20, null, model);
        assertThat(view).isEqualTo("redirect:/login");
    }

    @Test
    @DisplayName("listNotifications: Trả về view notification/list và nạp model đầy đủ khi đã đăng nhập")
    void testListNotificationsAuthenticated() {
        when(principal.getName()).thenReturn("buyer@test.com");
        User user = buildUser(20L, "buyer@test.com");
        when(userQueryService.findByEmail("buyer@test.com")).thenReturn(Optional.of(user));

        Notification notif = new Notification(20L, "Mã vé đã mở", "Vào nhận vé", NotificationType.DEPOSIT_LOCKED, "/tickets/2");
        notif.setId(202L);

        when(notificationService.getAllNotificationsPaged(20L, 0, 20)).thenReturn(List.of(notif));
        when(notificationService.getTotalCount(20L)).thenReturn(1L);
        when(notificationService.getUnreadCount(20L)).thenReturn(1L);

        Model model = new ConcurrentModel();
        String view = controller.listNotifications(0, 20, principal, model);

        assertThat(view).isEqualTo("notification/list");
        assertThat(model.getAttribute("notifications")).isNotNull();
        assertThat(model.getAttribute("totalCount")).isEqualTo(1L);
        assertThat(model.getAttribute("unreadCount")).isEqualTo(1L);
        assertThat(model.getAttribute("currentPage")).isEqualTo(0);
        assertThat(model.getAttribute("totalPages")).isEqualTo(1);
    }
}
