package com.yourapp.ticketpass.dispute.adapter;

import com.yourapp.ticketpass.ticket.domain.TicketType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class DisputeWindowPolicyTest {

    @Test
    @DisplayName("MovieDisputeWindowPolicy: đóng sau eventStartAt + 30 phút (BR-09 1B), fallback transferredAt + 2h")
    void testMoviePolicy() {
        MovieDisputeWindowPolicy policy = new MovieDisputeWindowPolicy(2);
        assertEquals(TicketType.MOVIE, policy.supportedType());
        assertEquals(Duration.ofHours(2), policy.windowAfterTransfer());

        Instant transferredAt = Instant.parse("2026-10-01T10:00:00Z");
        Instant eventAt = Instant.parse("2026-10-01T20:00:00Z");

        Instant close1 = policy.computeWindowClose(transferredAt, eventAt);
        assertEquals(eventAt.plus(Duration.ofMinutes(30)), close1);
        assertTrue(policy.isWithinWindow(transferredAt, eventAt, Instant.parse("2026-10-01T20:29:59Z")));
        assertTrue(policy.isWithinWindow(transferredAt, eventAt, Instant.parse("2026-10-01T20:30:00Z")));
        assertFalse(policy.isWithinWindow(transferredAt, eventAt, Instant.parse("2026-10-01T20:30:01Z")));

        Instant close2 = policy.computeWindowClose(transferredAt, null);
        assertEquals(transferredAt.plus(Duration.ofHours(2)), close2);
    }

    @Test
    @DisplayName("ConcertDisputeWindowPolicy: đóng sau eventStartAt + 30 phút (BR-09 1B), fallback transferredAt + 24h")
    void testConcertPolicy() {
        ConcertDisputeWindowPolicy policy = new ConcertDisputeWindowPolicy(24);
        assertEquals(TicketType.CONCERT, policy.supportedType());
        assertEquals(Duration.ofHours(24), policy.windowAfterTransfer());

        Instant transferredAt = Instant.parse("2026-12-10T09:00:00Z");
        Instant eventAt = Instant.parse("2026-12-20T20:00:00Z");

        assertEquals(eventAt.plus(Duration.ofMinutes(30)), policy.computeWindowClose(transferredAt, eventAt));
        assertTrue(policy.isWithinWindow(transferredAt, eventAt, Instant.parse("2026-12-20T20:29:59Z")));
        assertFalse(policy.isWithinWindow(transferredAt, eventAt, Instant.parse("2026-12-20T20:30:01Z")));

        assertEquals(transferredAt.plus(Duration.ofHours(24)), policy.computeWindowClose(transferredAt, null));
    }

    @Test
    @DisplayName("BusDisputeWindowPolicy: đóng sau eventStartAt + 30 phút (BR-09 1B), fallback transferredAt + 3h")
    void testBusPolicy() {
        BusDisputeWindowPolicy policy = new BusDisputeWindowPolicy(3);
        assertEquals(TicketType.BUS, policy.supportedType());
        assertEquals(Duration.ofHours(3), policy.windowAfterTransfer());

        Instant transferredAt = Instant.parse("2026-10-01T05:00:00Z");
        Instant eventAt = Instant.parse("2026-10-01T07:00:00Z");

        assertEquals(eventAt.plus(Duration.ofMinutes(30)), policy.computeWindowClose(transferredAt, eventAt));
        assertTrue(policy.isWithinWindow(transferredAt, eventAt, Instant.parse("2026-10-01T07:29:59Z")));
        assertFalse(policy.isWithinWindow(transferredAt, eventAt, Instant.parse("2026-10-01T07:30:01Z")));

        assertEquals(transferredAt.plus(Duration.ofHours(3)), policy.computeWindowClose(transferredAt, null));
    }
}
