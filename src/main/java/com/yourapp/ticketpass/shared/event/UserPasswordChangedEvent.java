package com.yourapp.ticketpass.shared.event;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class UserPasswordChangedEvent {

    private final Long userId;
    private final String userEmail;
    private final String keepSessionId;
    private final Instant occurredAt;
}
