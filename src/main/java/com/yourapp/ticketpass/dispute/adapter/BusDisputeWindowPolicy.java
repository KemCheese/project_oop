package com.yourapp.ticketpass.dispute.adapter;

import java.time.Duration;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.yourapp.ticketpass.dispute.domain.DisputeWindowPolicy;
import com.yourapp.ticketpass.ticket.domain.TicketType;

@Component
public class BusDisputeWindowPolicy implements DisputeWindowPolicy {

    private final Duration window;
    private static final Duration SELLER_RESPONSE_WINDOW = Duration.ofHours(8);

    public BusDisputeWindowPolicy(@Value("${app.dispute.window.bus-hours:3}") long busHours) {
        this.window = Duration.ofHours(busHours);
    }

    public BusDisputeWindowPolicy() {
        this(3);
    }

    @Override
    public TicketType supportedType() {
        return TicketType.BUS;
    }

    @Override
    public Duration windowAfterTransfer() {
        return window;
    }

    public static final Duration GATE_DISPUTE_BUFFER = Duration.ofMinutes(30);

    @Override
    public Instant computeWindowClose(Instant transferredAt, Instant eventStartAt) {
        if (eventStartAt != null) {
            return eventStartAt.plus(GATE_DISPUTE_BUFFER);
        }
        return transferredAt != null ? transferredAt.plus(window) : Instant.now().plus(window);
    }

    @Override
    public boolean isWithinWindow(Instant transferredAt, Instant eventStartAt, Instant now) {
        if (transferredAt == null || now == null) {
            return false;
        }
        return !now.isAfter(computeWindowClose(transferredAt, eventStartAt));
    }

    @Override
    public Duration sellerResponseWindow() {
        return SELLER_RESPONSE_WINDOW;
    }
}
