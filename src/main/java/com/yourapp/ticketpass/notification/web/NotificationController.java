package com.yourapp.ticketpass.notification.web;

import com.yourapp.ticketpass.notification.application.NotificationService;
import com.yourapp.ticketpass.notification.domain.Notification;
import com.yourapp.ticketpass.notification.web.dto.NotificationResponse;
import com.yourapp.ticketpass.shared.exception.ResourceNotFoundException;
import com.yourapp.ticketpass.user.application.UserQueryService;
import com.yourapp.ticketpass.user.domain.User;
import java.security.Principal;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final UserQueryService userQueryService;

    @GetMapping
    public String listNotifications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Principal principal,
            Model model) {
        if (principal == null) {
            return "redirect:/login";
        }

        User user = userQueryService.findByEmail(principal.getName())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng: " + principal.getName()));

        List<Notification> entities = notificationService.getAllNotificationsPaged(user.getId(), page, size);
        List<NotificationResponse> notifications = entities.stream()
                .map(NotificationResponse::fromEntity)
                .collect(Collectors.toList());

        long totalCount = notificationService.getTotalCount(user.getId());
        long unreadCount = notificationService.getUnreadCount(user.getId());
        int totalPages = totalCount > 0 ? (int) Math.ceil((double) totalCount / size) : 1;

        model.addAttribute("notifications", notifications);
        model.addAttribute("totalCount", totalCount);
        model.addAttribute("unreadCount", unreadCount);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", totalPages);

        return "notification/list";
    }
}
