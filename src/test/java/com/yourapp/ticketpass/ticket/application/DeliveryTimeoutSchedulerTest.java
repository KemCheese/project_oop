package com.yourapp.ticketpass.ticket.application;

import com.yourapp.ticketpass.ticket.domain.Ticket;
import com.yourapp.ticketpass.ticket.domain.TicketRepository;
import com.yourapp.ticketpass.ticket.domain.TicketStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeliveryTimeoutSchedulerTest {

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private TransferTicketService transferTicketService;

    @InjectMocks
    private DeliveryTimeoutScheduler deliveryTimeoutScheduler;

    @Test
    @DisplayName("scanDeliveryTimeouts: Tự động chuyển AWAITING_USE khi quá 30 phút không có thao tác (AC-02.2)")
    void testScanDeliveryTimeoutsAutoConfirms() {
        Ticket ticket = new Ticket();
        ticket.setId(301L);
        ticket.setStatus(TicketStatus.TRANSFERRED);
        ticket.setTransferredAt(Instant.now().minusSeconds(1900)); // 31 phút trước

        when(ticketRepository.findOverdueDeliveryTickets(any(Instant.class)))
                .thenReturn(List.of(ticket));

        deliveryTimeoutScheduler.scanDeliveryTimeouts();

        verify(transferTicketService).autoConfirmReceipt(301L);
    }

    @Test
    @DisplayName("scanDeliveryTimeouts: Không có vé quá hạn thì không làm gì")
    void testScanDeliveryTimeoutsEmpty() {
        when(ticketRepository.findOverdueDeliveryTickets(any(Instant.class)))
                .thenReturn(Collections.emptyList());

        deliveryTimeoutScheduler.scanDeliveryTimeouts();

        verifyNoInteractions(transferTicketService);
    }
}
