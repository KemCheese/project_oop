package com.yourapp.ticketpass.ticket.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.yourapp.ticketpass.shared.event.TicketUpcomingReminderEvent;
import com.yourapp.ticketpass.ticket.adapter.MovieTicketAdapter;
import com.yourapp.ticketpass.ticket.domain.Ticket;
import com.yourapp.ticketpass.ticket.domain.TicketRepository;
import com.yourapp.ticketpass.ticket.domain.TicketStatus;
import com.yourapp.ticketpass.ticket.domain.TicketType;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@DisplayName("TicketReminderService Test - Atomic claim idempotent, time-travel Clock & mua sát giờ < 24h")
@ExtendWith(MockitoExtension.class)
class TicketReminderServiceTest {

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private TicketScheduleService scheduleService;

    @BeforeEach
    void setUp() {
        scheduleService = new TicketScheduleService(ticketRepository, List.of(new MovieTicketAdapter()));
    }

    private Ticket buildTicket(Instant startAt, Instant remindedAt) {
        Ticket t = new Ticket();
        t.setId(500L);
        t.setSellerId(10L);
        t.setBuyerId(20L);
        t.setTicketType(TicketType.MOVIE);
        t.setStatus(TicketStatus.TRANSFERRED);
        t.setTitle("Vé Phim 00:14");
        t.setVenue("CGV Landmark");
        t.setEventStartAt(startAt);
        t.setEventEndAt(startAt.plusSeconds(7200));
        t.setReminded24hAt(remindedAt);
        return t;
    }

    @Test
    @DisplayName("Mốc 24h00p (00:14 15/11 cho vé 00:14 16/11): Claim thành công và phát event đúng 1 lần")
    void testTriggersAtExact24HourMark() {
        Instant eventStart = Instant.parse("2026-11-16T00:14:00+07:00");
        Instant nowExact24h = Instant.parse("2026-11-15T00:14:00+07:00");
        Clock clock = Clock.fixed(nowExact24h, ZoneOffset.UTC);
        TicketReminderService service = new TicketReminderService(ticketRepository, scheduleService, eventPublisher, clock);

        Ticket ticket = buildTicket(eventStart, null);
        when(ticketRepository.markReminded24hIfUnset(500L, nowExact24h)).thenReturn(1);

        boolean triggered = service.triggerReminderIfDue(ticket, false);

        assertThat(triggered).isTrue();
        ArgumentCaptor<TicketUpcomingReminderEvent> captor = ArgumentCaptor.forClass(TicketUpcomingReminderEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().getTicketId()).isEqualTo(500L);
        assertThat(captor.getValue().getBuyerId()).isEqualTo(20L);
        assertThat(captor.getValue().isLastMinutePurchase()).isFalse();
    }

    @Test
    @DisplayName("Trước 24h01p -> chưa đến cửa sổ nhắc; Sự kiện đã qua (<= now) -> bỏ qua")
    void testSkipsBeforeWindowAndPastEvents() {
        Instant eventStart = Instant.parse("2026-11-16T00:14:00+07:00");
        Instant now24h01mBefore = Instant.parse("2026-11-15T00:13:00+07:00");
        Clock earlyClock = Clock.fixed(now24h01mBefore, ZoneOffset.UTC);
        TicketReminderService earlyService =
                new TicketReminderService(ticketRepository, scheduleService, eventPublisher, earlyClock);

        assertThat(earlyService.triggerReminderIfDue(buildTicket(eventStart, null), false)).isFalse();

        Clock pastClock = Clock.fixed(eventStart, ZoneOffset.UTC);
        TicketReminderService pastService =
                new TicketReminderService(ticketRepository, scheduleService, eventPublisher, pastClock);
        assertThat(pastService.triggerReminderIfDue(buildTicket(eventStart, null), true)).isFalse();

        verifyNoInteractions(eventPublisher);
        verify(ticketRepository, never()).markReminded24hIfUnset(any(), any());
    }

    @Test
    @DisplayName("Idempotent: Nếu markReminded24hIfUnset trả 0 (luồng khác đã claim) -> KHÔNG phát event trùng")
    void testIdempotentWhenAtomicClaimReturnsZero() {
        Instant eventStart = Instant.parse("2026-11-16T00:14:00+07:00");
        Instant nowInside = Instant.parse("2026-11-15T12:00:00+07:00");
        Clock clock = Clock.fixed(nowInside, ZoneOffset.UTC);
        TicketReminderService service = new TicketReminderService(ticketRepository, scheduleService, eventPublisher, clock);

        Ticket ticket = buildTicket(eventStart, null);
        when(ticketRepository.markReminded24hIfUnset(500L, nowInside)).thenReturn(0);

        boolean triggered = service.triggerReminderIfDue(ticket, true);

        assertThat(triggered).isFalse();
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("Mua sát giờ (<= 24h): triggerEarlyReminderIfWithinLeadWindow đánh dấu lastMinutePurchase=true")
    void testEarlyReminderForLastMinutePurchase() {
        Instant eventStart = Instant.parse("2026-11-15T20:00:00+07:00");
        Instant now = Instant.parse("2026-11-15T10:00:00+07:00");
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        TicketReminderService service = new TicketReminderService(ticketRepository, scheduleService, eventPublisher, clock);

        Ticket ticket = buildTicket(eventStart, null);
        when(ticketRepository.markReminded24hIfUnset(500L, now)).thenReturn(1);

        boolean triggered = service.triggerEarlyReminderIfWithinLeadWindow(ticket);

        assertThat(triggered).isTrue();
        ArgumentCaptor<TicketUpcomingReminderEvent> captor = ArgumentCaptor.forClass(TicketUpcomingReminderEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().isLastMinutePurchase()).isTrue();
    }
}
