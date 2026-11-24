package com.yourapp.ticketpass.payment.application;

import com.yourapp.ticketpass.reputation.application.UpdateReputationUseCase;
import com.yourapp.ticketpass.ticket.application.TicketStateMachineService;
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

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DepositTimeoutSchedulerTest {

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private RefundBuyerUseCase refundBuyerUseCase;

    @Mock
    private TicketStateMachineService ticketStateMachineService;

    @Mock
    private UpdateReputationUseCase updateReputationUseCase;

    @InjectMocks
    private DepositTimeoutScheduler depositTimeoutScheduler;

    @Test
    @DisplayName("scanDepositTimeouts: Hoàn tiền 100%, huỷ vé CANCELLED và phạt Seller -1đ uy tín kèm ticketId (UC-04b)")
    void testScanDepositTimeoutsTriggersRefundCancelAndPenalty() {
        Ticket ticket = new Ticket();
        ticket.setId(101L);
        ticket.setSellerId(202L);
        ticket.setStatus(TicketStatus.PAYMENT_ESCROWED);
        ticket.setEscrowedAt(Instant.now().minusSeconds(1900));

        when(ticketRepository.findOverdueDepositTickets(any(Instant.class)))
                .thenReturn(List.of(ticket));

        depositTimeoutScheduler.scanDepositTimeouts();

        verify(refundBuyerUseCase).refundFull(eq(101L), contains("Quá hạn nạp cọc"));

        verify(ticketStateMachineService).transition(eq(101L), eq(TicketStatus.CANCELLED));

        verify(updateReputationUseCase).recordDepositTimeout(eq(202L), eq(101L));
    }

    @Test
    @DisplayName("scanDepositTimeouts: Không có vé quá hạn thì không làm gì")
    void testScanDepositTimeoutsNoOverdueTickets() {
        when(ticketRepository.findOverdueDepositTickets(any(Instant.class)))
                .thenReturn(Collections.emptyList());

        depositTimeoutScheduler.scanDepositTimeouts();

        verifyNoInteractions(refundBuyerUseCase);
        verifyNoInteractions(ticketStateMachineService);
        verifyNoInteractions(updateReputationUseCase);
    }
}
