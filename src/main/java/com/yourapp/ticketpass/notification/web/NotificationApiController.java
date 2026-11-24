package com.yourapp.ticketpass.notification.web;

import com.yourapp.ticketpass.notification.application.NotificationService;
import com.yourapp.ticketpass.notification.domain.Notification;
import com.yourapp.ticketpass.notification.web.dto.NotificationListResponse;
import com.yourapp.ticketpass.notification.web.dto.NotificationResponse;
import com.yourapp.ticketpass.shared.exception.ResourceNotFoundException;
import com.yourapp.ticketpass.user.application.UserQueryService;
import com.yourapp.ticketpass.user.domain.User;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationApiController {

    private final NotificationService notificationService;
    private final UserQueryService userQueryService;

    @GetMapping("/recent")
    public ResponseEntity<NotificationListResponse> getRecent(
            @RequestParam(defaultValue = "10") int limit,
            Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        User user = getAuthenticatedUser(principal);
        long unreadCount = notificationService.getUnreadCount(user.getId());
        long totalCount = notificationService.getTotalCount(user.getId());
        List<Notification> entities = notificationService.getRecentNotifications(user.getId(), Math.max(1, limit));

        List<NotificationResponse> dtos = entities.stream()
                .map(NotificationResponse::fromEntity)
                .collect(Collectors.toList());

        return ResponseEntity.ok(NotificationListResponse.builder()
                .unreadCount(unreadCount)
                .totalCount(totalCount)
                .notifications(dtos)
                .build());
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Object>> getUnreadCount(Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        User user = getAuthenticatedUser(principal);
        long unreadCount = notificationService.getUnreadCount(user.getId());
        return ResponseEntity.ok(Map.of("unreadCount", unreadCount));
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<NotificationResponse> markAsRead(
            @PathVariable Long id,
            Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        User user = getAuthenticatedUser(principal);
        Notification updated = notificationService.markAsRead(id, user.getId());
        return ResponseEntity.ok(NotificationResponse.fromEntity(updated));
    }

    @PostMapping("/read-all")
    public ResponseEntity<Map<String, Object>> markAllAsRead(Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        User user = getAuthenticatedUser(principal);
        int updatedCount = notificationService.markAllAsRead(user.getId());
        return ResponseEntity.ok(Map.of("success", true, "updatedCount", updatedCount));
    }

    private User getAuthenticatedUser(Principal principal) {
        return userQueryService.findByEmail(principal.getName())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng: " + principal.getName()));
    }
}
