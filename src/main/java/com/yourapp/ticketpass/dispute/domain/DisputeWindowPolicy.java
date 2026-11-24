package com.yourapp.ticketpass.dispute.domain;

import java.time.Duration;
import java.time.Instant;

import com.yourapp.ticketpass.ticket.domain.TicketType;

public interface DisputeWindowPolicy {

    TicketType supportedType();

    Duration windowAfterTransfer();

    Instant computeWindowClose(Instant transferredAt, Instant eventStartAt);

    boolean isWithinWindow(Instant transferredAt, Instant eventStartAt, Instant now);

    Duration sellerResponseWindow();
}
