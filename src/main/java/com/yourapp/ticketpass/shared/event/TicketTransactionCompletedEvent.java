package com.yourapp.ticketpass.shared.event;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@AllArgsConstructor
public class TicketTransactionCompletedEvent {

    private final Long ticketId;

    private final Long escrowTransactionId;

    private final Long depositId;

    private final Long sellerId;

    private final Long buyerId;

    private final BigDecimal salePrice;

    private final BigDecimal buyerCommission;

    private final BigDecimal sellerCommission;

    private final BigDecimal taxWithheld;

    private final BigDecimal sellerNetPayout;

    private final BigDecimal depositRefunded;

    private final CompletionReason completionReason;

    private final Instant occurredAt;

    public CompletionReason getReason() {
        return completionReason;
    }
}
