package com.yourapp.ticketpass.ticket.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.yourapp.ticketpass.shared.event.TicketExpiredEvent;
import com.yourapp.ticketpass.ticket.domain.Ticket;
import com.yourapp.ticketpass.ticket.domain.TicketRepository;
import com.yourapp.ticketpass.ticket.domain.TicketStatus;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class TicketExpirationSchedulerTest {

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private TicketStateMachineService ticketStateMachineService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private TicketExpirationScheduler scheduler;

    @Test
    @DisplayName("scanExpiredListedTickets: Tự động chuyển CANCELLED và bắn TicketExpiredEvent cho vé LISTED quá hạn")
    void testScanExpiredListedTickets_Success() {
        Ticket ticket = new Ticket();
        ticket.setId(101L);
        ticket.setSellerId(55L);
        ticket.setTitle("Vé Xem Phim DUNE 2");
        ticket.setStatus(TicketStatus.LISTED);
        ticket.setEventStartAt(Instant.now().minusSeconds(600));

        when(ticketRepository.findExpiredListedTickets(any(Instant.class)))
                .thenReturn(List.of(ticket));

        scheduler.scanExpiredListedTickets();

        verify(ticketStateMachineService).transition(101L, TicketStatus.CANCELLED);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue()).isInstanceOf(TicketExpiredEvent.class);
        TicketExpiredEvent event = (TicketExpiredEvent) captor.getValue();
        assertThat(event.getTicketId()).isEqualTo(101L);
        assertThat(event.getSellerId()).isEqualTo(55L);
        assertThat(event.getTicketTitle()).isEqualTo("Vé Xem Phim DUNE 2");
    }

    @Test
    @DisplayName("scanExpiredListedTickets: Danh sách rỗng thì không làm gì")
    void testScanExpiredListedTickets_Empty() {
        when(ticketRepository.findExpiredListedTickets(any(Instant.class)))
                .thenReturn(Collections.emptyList());

        scheduler.scanExpiredListedTickets();

        verifyNoInteractions(ticketStateMachineService);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("scanExpiredListedTickets: Bỏ qua vé không còn ở trạng thái LISTED")
    void testScanExpiredListedTickets_NonListedIgnored() {
        Ticket ticket = new Ticket();
        ticket.setId(102L);
        ticket.setSellerId(55L);
        ticket.setTitle("Vé Concert Blackpink");
        ticket.setStatus(TicketStatus.PAYMENT_ESCROWED);

        when(ticketRepository.findExpiredListedTickets(any(Instant.class)))
                .thenReturn(List.of(ticket));

        scheduler.scanExpiredListedTickets();

        verify(ticketStateMachineService, never()).transition(eq(102L), any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("scanExpiredListedTickets: Bắt ngoại lệ từng vé để không làm gián đoạn batch")
    void testScanExpiredListedTickets_HandlesExceptionGracefully() {
        Ticket ticket1 = new Ticket();
        ticket1.setId(201L);
        ticket1.setSellerId(10L);
        ticket1.setTitle("Vé Lỗi");
        ticket1.setStatus(TicketStatus.LISTED);

        Ticket ticket2 = new Ticket();
        ticket2.setId(202L);
        ticket2.setSellerId(20L);
        ticket2.setTitle("Vé Bình Thường");
        ticket2.setStatus(TicketStatus.LISTED);

        when(ticketRepository.findExpiredListedTickets(any(Instant.class)))
                .thenReturn(List.of(ticket1, ticket2));
        when(ticketStateMachineService.transition(201L, TicketStatus.CANCELLED))
                .thenThrow(new RuntimeException("DB Connection Timeout"));

        scheduler.scanExpiredListedTickets();

        // ticket1 bị lỗi nhưng ticket2 vẫn được xử lý bình thường
        verify(ticketStateMachineService).transition(202L, TicketStatus.CANCELLED);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue()).isInstanceOf(TicketExpiredEvent.class);
        TicketExpiredEvent event = (TicketExpiredEvent) captor.getValue();
        assertThat(event.getTicketId()).isEqualTo(202L);
    }
}
