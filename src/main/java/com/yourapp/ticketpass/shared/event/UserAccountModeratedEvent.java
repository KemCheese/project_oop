package com.yourapp.ticketpass.shared.event;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class UserAccountModeratedEvent {

    private final Long userId;
    private final String userEmail;
    private final Long adminId;
    private final String targetStatusName;
    private final String reason;
    private final Instant occurredAt;
}
