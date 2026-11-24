package com.yourapp.ticketpass.shared.event;

import java.math.BigDecimal;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ReputationDepletedEvent {

    private final Long userId;
    private final BigDecimal scoreBefore;
    private final BigDecimal scoreAfter;
    private final String reasonCode;
    private final String description;
    private final Long ticketId;
    private final Long disputeId;
    private final Instant occurredAt;
}
