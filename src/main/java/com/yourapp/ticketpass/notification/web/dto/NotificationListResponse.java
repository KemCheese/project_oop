package com.yourapp.ticketpass.notification.web.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationListResponse {
    private long unreadCount;
    private long totalCount;
    private List<NotificationResponse> notifications;
}
