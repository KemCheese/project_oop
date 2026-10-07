package com.yourapp.ticketpass.dispute.adapter;

import com.yourapp.ticketpass.ticket.domain.TicketType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test cho 3 implementation của {@link com.yourapp.ticketpass.dispute.domain.DisputeWindowPolicy}.
 */
class DisputeWindowPolicyTest {

    @Test
    @DisplayName("MovieDisputeWindowPolicy: cửa sổ khiếu nại đóng sau thời gian bắt đầu 1 ngày")
    void testMoviePolicy() {
        MovieDisputeWindowPolicy policy = new MovieDisputeWindowPolicy(2);
        assertEquals(TicketType.MOVIE, policy.supportedType());
        assertEquals(Duration.ofHours(2), policy.windowAfterTransfer());

        Instant transferredAt = Instant.parse("2026-10-01T10:00:00Z");
        Instant eventAt       = Instant.parse("2026-10-01T20:00:00Z");

        // Case 1: Có eventStartAt -> đóng sau eventStartAt 1 ngày (2026-10-02T20:00:00Z)
        Instant close1 = policy.computeWindowClose(transferredAt, eventAt);
        assertEquals(eventAt.plus(Duration.ofDays(1)), close1);
        assertTrue(policy.isWithinWindow(transferredAt, eventAt, Instant.parse("2026-10-02T19:59:59Z")));
        assertFalse(policy.isWithinWindow(transferredAt, eventAt, Instant.parse("2026-10-02T20:00:01Z")));

        // Case 2: Không có eventStartAt -> fallback transferredAt + window (12:00:00Z)
        Instant close2 = policy.computeWindowClose(transferredAt, null);
        assertEquals(transferredAt.plus(Duration.ofHours(2)), close2);
    }

    @Test
    @DisplayName("ConcertDisputeWindowPolicy: cửa sổ khiếu nại đóng sau thời gian bắt đầu 1 ngày")
    void testConcertPolicy() {
        ConcertDisputeWindowPolicy policy = new ConcertDisputeWindowPolicy(24);
        assertEquals(TicketType.CONCERT, policy.supportedType());
        assertEquals(Duration.ofHours(24), policy.windowAfterTransfer());

        Instant transferredAt = Instant.parse("2026-12-10T09:00:00Z");
        Instant eventAt       = Instant.parse("2026-12-20T20:00:00Z");

        // Có eventStartAt -> đóng sau eventStartAt 1 ngày
        assertEquals(eventAt.plus(Duration.ofDays(1)), policy.computeWindowClose(transferredAt, eventAt));

        // Không có eventStartAt -> fallback transferredAt + 24h
        assertEquals(transferredAt.plus(Duration.ofHours(24)), policy.computeWindowClose(transferredAt, null));
    }

    @Test
    @DisplayName("BusDisputeWindowPolicy: cửa sổ khiếu nại đóng sau thời gian bắt đầu 1 ngày")
    void testBusPolicy() {
        BusDisputeWindowPolicy policy = new BusDisputeWindowPolicy(3);
        assertEquals(TicketType.BUS, policy.supportedType());
        assertEquals(Duration.ofHours(3), policy.windowAfterTransfer());

        Instant transferredAt = Instant.parse("2026-10-01T05:00:00Z");
        Instant eventAt       = Instant.parse("2026-10-01T07:00:00Z");

        // Có eventStartAt -> đóng sau xuất bến 1 ngày (02/10 07:00)
        assertEquals(eventAt.plus(Duration.ofDays(1)), policy.computeWindowClose(transferredAt, eventAt));

        // Không có eventStartAt -> fallback transferredAt + 3h
        assertEquals(transferredAt.plus(Duration.ofHours(3)), policy.computeWindowClose(transferredAt, null));
    }
}
