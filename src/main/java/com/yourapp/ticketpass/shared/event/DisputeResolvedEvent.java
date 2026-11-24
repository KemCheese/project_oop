package com.yourapp.ticketpass.shared.event;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@AllArgsConstructor
public class DisputeResolvedEvent {

    private final Long disputeId;

    private final Long ticketId;

    private final Long escrowTransactionId;

    private final Long depositId;

    private final Long buyerId;

    private final Long sellerId;

    private final DisputeOutcome outcome;

    private final BigDecimal totalDeposit;

    private final BigDecimal founderCut;

    private final BigDecimal buyerCompensation;

    private final Long resolvedByAdminId;

    private final Instant occurredAt;
}
