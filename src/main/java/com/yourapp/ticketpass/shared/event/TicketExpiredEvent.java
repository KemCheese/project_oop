package com.yourapp.ticketpass.shared.event;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class TicketExpiredEvent {
    private final Long ticketId;
    private final Long sellerId;
    private final String ticketTitle;
    private final Instant expiredAt;
}
