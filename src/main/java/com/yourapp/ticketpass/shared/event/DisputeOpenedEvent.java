package com.yourapp.ticketpass.shared.event;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class DisputeOpenedEvent {
    private final Long disputeId;
    private final Long ticketId;
    private final Long sellerId;
    private final Long buyerId;
    private final String ticketTitle;
    private final String reason;
    private final Instant occurredAt;
}
