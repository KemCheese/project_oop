package com.yourapp.ticketpass.shared.event;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class TicketAutoCancelledDueToBanEvent {

    private final Long ticketId;
    private final Long bannedUserId;
    private final Long counterpartyUserId;
    private final String counterpartyRole;
    private final String ticketTitle;
    private final boolean escrowRefunded;
    private final boolean depositRefunded;
    private final Instant occurredAt;
}
