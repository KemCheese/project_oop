package com.yourapp.ticketpass.ticket.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yourapp.ticketpass.ticket.domain.Ticket;
import com.yourapp.ticketpass.ticket.domain.TicketRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@DisplayName("TicketReminderScheduler Test - Batch limit & Fault isolation")
@ExtendWith(MockitoExtension.class)
class TicketReminderSchedulerTest {

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private TicketReminderService ticketReminderService;

    @Test
    @DisplayName("Quét theo batch giới hạn 100 và tiếp tục xử lý khi 1 vé ném lỗi")
    void testScanRespectsBatchLimitAndIsolatesFailures() {
        Instant now = Instant.parse("2026-11-15T00:14:00Z");
        Clock fixedClock = Clock.fixed(now, ZoneOffset.UTC);
        TicketReminderScheduler scheduler =
                new TicketReminderScheduler(ticketRepository, ticketReminderService, fixedClock);
        scheduler.setLeadHours(24L);

        Ticket t1 = new Ticket();
        t1.setId(1L);
        Ticket t2 = new Ticket();
        t2.setId(2L);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        when(ticketRepository.findTicketsDueFor24hReminder(
                eq(TicketReminderScheduler.REMINDABLE_STATUSES),
                eq(now),
                eq(Instant.parse("2026-11-16T00:14:00Z")),
                pageableCaptor.capture()
        )).thenReturn(List.of(t1, t2));

        doThrow(new RuntimeException("Simulated DB timeout")).when(ticketReminderService).triggerReminderIfDue(t1, false);

        scheduler.scanAndSendUpcomingReminders();

        assertThat(pageableCaptor.getValue()).isEqualTo(PageRequest.of(0, TicketReminderScheduler.REMINDER_BATCH_SIZE));
        verify(ticketReminderService).triggerReminderIfDue(t1, false);
        verify(ticketReminderService).triggerReminderIfDue(t2, false);
    }
}
