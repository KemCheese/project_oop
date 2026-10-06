package com.yourapp.ticketpass.notification.web.dto;

import com.yourapp.ticketpass.notification.domain.Notification;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DTO trả về thông tin thông báo cho client.
 *
 * <p>Module: notification</p>
 * <p>Chủ sở hữu: Người C (Dev C)</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationResponse {

    private Long id;
    private String title;
    private String content;
    private String type;
    private String targetUrl;
    private boolean isRead;
    private Instant createdAt;
    private Instant readAt;
    private String timeAgo;

    public static NotificationResponse fromEntity(Notification entity) {
        if (entity == null) return null;
        return NotificationResponse.builder()
                .id(entity.getId())
                .title(entity.getTitle())
                .content(entity.getContent())
                .type(entity.getType() != null ? entity.getType().name() : "SYSTEM")
                .targetUrl(entity.getTargetUrl())
                .isRead(entity.isRead())
                .createdAt(entity.getCreatedAt())
                .readAt(entity.getReadAt())
                .timeAgo(calculateTimeAgo(entity.getCreatedAt()))
                .build();
    }

    public static String calculateTimeAgo(Instant instant) {
        if (instant == null) return "";
        long seconds = Math.max(0, Duration.between(instant, Instant.now()).getSeconds());
        if (seconds < 60) {
            return "Vừa xong";
        }
        long minutes = seconds / 60;
        if (minutes < 60) {
            return minutes + " phút trước";
        }
        long hours = minutes / 60;
        if (hours < 24) {
            return hours + " giờ trước";
        }
        long days = hours / 24;
        if (days < 30) {
            return days + " ngày trước";
        }
        return LocalDateTime.ofInstant(instant, ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
    }
}
