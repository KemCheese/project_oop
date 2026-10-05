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
    @DisplayName("MovieDisputeWindowPolicy: cửa sổ 2 giờ, cắt tại eventStartAt nếu chiếu sớm hơn")
    void testMoviePolicy() {
        MovieDisputeWindowPolicy policy = new MovieDisputeWindowPolicy(2);
        assertEquals(TicketType.MOVIE, policy.supportedType());
        assertEquals(Duration.ofHours(2), policy.windowAfterTransfer());

        Instant transferredAt = Instant.parse("2026-10-01T10:00:00Z");
        Instant lateEvent     = Instant.parse("2026-10-01T20:00:00Z"); // 10 tiếng sau
        Instant earlyEvent    = Instant.parse("2026-10-01T11:00:00Z"); // 1 tiếng sau (trước khi hết 2h)

        // Case 1: Cửa sổ 2h đến trước giờ chiếu -> đóng lúc 12:00
        Instant close1 = policy.computeWindowClose(transferredAt, lateEvent);
        assertEquals(Instant.parse("2026-10-01T12:00:00Z"), close1);
        assertTrue(policy.isWithinWindow(transferredAt, lateEvent, Instant.parse("2026-10-01T11:59:59Z")));
        assertFalse(policy.isWithinWindow(transferredAt, lateEvent, Instant.parse("2026-10-01T12:00:00Z")));

        // Case 2: Giờ chiếu đến trước (11:00) -> cắt tại 11:30 (eventStartAt + 30m) theo BR-09 Revised
        Instant close2 = policy.computeWindowClose(transferredAt, earlyEvent);
        assertEquals(earlyEvent.plus(Duration.ofMinutes(30)), close2);
        assertTrue(policy.isWithinWindow(transferredAt, earlyEvent, Instant.parse("2026-10-01T11:29:59Z")));
        assertFalse(policy.isWithinWindow(transferredAt, earlyEvent, Instant.parse("2026-10-01T11:30:01Z")));
    }

    @Test
    @DisplayName("ConcertDisputeWindowPolicy: cửa sổ 24 giờ, cắt tại eventStartAt + 30m nếu diễn sớm hơn")
    void testConcertPolicy() {
        ConcertDisputeWindowPolicy policy = new ConcertDisputeWindowPolicy(24);
        assertEquals(TicketType.CONCERT, policy.supportedType());
        assertEquals(Duration.ofHours(24), policy.windowAfterTransfer());

        Instant transferredAt = Instant.parse("2026-12-10T09:00:00Z");
        Instant lateEvent     = Instant.parse("2026-12-20T20:00:00Z");
        Instant earlyEvent    = Instant.parse("2026-12-10T12:00:00Z"); // chuyển vé 3h trước show

        // Case 1: Đóng sau 24h (11/12 09:00)
        assertEquals(Instant.parse("2026-12-11T09:00:00Z"), policy.computeWindowClose(transferredAt, lateEvent));

        // Case 2: Cắt tại giờ diễn + 30 phút (10/12 12:30)
        assertEquals(earlyEvent.plus(Duration.ofMinutes(30)), policy.computeWindowClose(transferredAt, earlyEvent));
    }

    @Test
    @DisplayName("BusDisputeWindowPolicy: cửa sổ 3 giờ, cắt tại eventStartAt + 30m nếu sớm hơn")
    void testBusPolicy() {
        BusDisputeWindowPolicy policy = new BusDisputeWindowPolicy(3);
        assertEquals(TicketType.BUS, policy.supportedType());
        assertEquals(Duration.ofHours(3), policy.windowAfterTransfer());

        Instant transferredAt = Instant.parse("2026-10-01T05:00:00Z");
        Instant eventAt       = Instant.parse("2026-10-01T07:00:00Z"); // 2h sau

        // Giờ khởi hành 07:00 + 30m = 07:30 < 08:00 -> đóng tại 07:30
        assertEquals(eventAt.plus(Duration.ofMinutes(30)), policy.computeWindowClose(transferredAt, eventAt));
    }
}
