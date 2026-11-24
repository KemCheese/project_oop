package com.yourapp.ticketpass.ticket.application;

import com.yourapp.ticketpass.ticket.domain.IllegalTicketTransitionException;
import com.yourapp.ticketpass.ticket.domain.Ticket;
import com.yourapp.ticketpass.ticket.domain.TicketAdapter;
import com.yourapp.ticketpass.ticket.domain.TicketRepository;
import com.yourapp.ticketpass.ticket.domain.TicketStatus;
import com.yourapp.ticketpass.ticket.domain.TicketType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TicketStateMachineServiceTest {

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private TicketAdapter movieAdapter;

    private TicketStateMachineService stateMachineService;

    @BeforeEach
    void setUp() {
        lenient().when(movieAdapter.supportedType()).thenReturn(TicketType.MOVIE);
        lenient().when(movieAdapter.disputeWindowAfterTransfer()).thenReturn(Duration.ofHours(2));
        stateMachineService = new TicketStateMachineService(ticketRepository, java.util.List.of(movieAdapter));
        stateMachineService.initAdapterMap();
        lenient().when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("allowedNextStates: PAYMENT_ESCROWED cho phép DEPOSIT_LOCKED hoặc CANCELLED (UC-04b)")
    void testAllowedNextStatesFromPaymentEscrowed() {
        Set<TicketStatus> nextStates = stateMachineService.allowedNextStates(TicketStatus.PAYMENT_ESCROWED);
        assertTrue(nextStates.contains(TicketStatus.DEPOSIT_LOCKED));
        assertTrue(nextStates.contains(TicketStatus.CANCELLED));
        assertFalse(nextStates.contains(TicketStatus.LISTED));
    }

    @Test
    @DisplayName("allowedNextStates: DEPOSIT_LOCKED chỉ cho phép đúng {AWAITING_USE, CANCELLED}")
    void testAllowedNextStatesFromDepositLocked() {
        Set<TicketStatus> nextStates = stateMachineService.allowedNextStates(TicketStatus.DEPOSIT_LOCKED);
        assertEquals(Set.of(TicketStatus.AWAITING_USE, TicketStatus.CANCELLED), nextStates);
    }

    @Test
    @DisplayName("transition: LISTED -> PAYMENT_ESCROWED cập nhật escrowedAt")
    void testTransitionToPaymentEscrowedSetsEscrowedAt() {
        Ticket ticket = new Ticket();
        ticket.setId(1L);
        ticket.setStatus(TicketStatus.LISTED);

        when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));

        Ticket result = stateMachineService.transition(1L, TicketStatus.PAYMENT_ESCROWED);

        assertEquals(TicketStatus.PAYMENT_ESCROWED, result.getStatus());
        assertNotNull(result.getEscrowedAt());
    }

    @Test
    @DisplayName("transition: PAYMENT_ESCROWED -> CANCELLED (UC-04b timeout huỷ vé)")
    void testTransitionPaymentEscrowedToCancelled() {
        Ticket ticket = new Ticket();
        ticket.setId(2L);
        ticket.setStatus(TicketStatus.PAYMENT_ESCROWED);

        when(ticketRepository.findById(2L)).thenReturn(Optional.of(ticket));

        Ticket result = stateMachineService.transition(2L, TicketStatus.CANCELLED);

        assertEquals(TicketStatus.CANCELLED, result.getStatus());
    }

    @Test
    @DisplayName("allowedNextStates: DEPOSIT_LOCKED cho phép CANCELLED và AWAITING_USE không cho phép CANCELLED khi điểm uy tín = 0")
    void testAllowedNextStatesSupportsAutoCancelBeforeQrOpened() {
        assertTrue(stateMachineService.allowedNextStates(TicketStatus.DEPOSIT_LOCKED).contains(TicketStatus.CANCELLED));
        assertFalse(stateMachineService.allowedNextStates(TicketStatus.AWAITING_USE).contains(TicketStatus.CANCELLED));
    }

    @Test
    @DisplayName("transition: DEPOSIT_LOCKED -> AWAITING_USE cập nhật transferredAt, receivedAt và disputeWindowClosesAt = eventStartAt + 30p (BR-09 1B)")
    void testTransitionDepositLockedToAwaitingUseSetsTimestampsAndWindow() {
        Ticket ticket = new Ticket();
        ticket.setId(4L);
        ticket.setTicketType(TicketType.MOVIE);
        ticket.setStatus(TicketStatus.DEPOSIT_LOCKED);
        ticket.setEventStartAt(Instant.now().plus(Duration.ofHours(5)));

        when(ticketRepository.findById(4L)).thenReturn(Optional.of(ticket));

        Ticket result = stateMachineService.transition(4L, TicketStatus.AWAITING_USE);

        assertEquals(TicketStatus.AWAITING_USE, result.getStatus());
        assertNotNull(result.getTransferredAt());
        assertNotNull(result.getReceivedAt());
        assertNotNull(result.getDisputeWindowClosesAt());
        assertEquals(ticket.getEventStartAt().plus(Duration.ofMinutes(30)), result.getDisputeWindowClosesAt());
    }

    @Test
    @DisplayName("transition: DEPOSIT_LOCKED -> AWAITING_USE khi eventStartAt null thì fallback receivedAt + window")
    void testTransitionDepositLockedToAwaitingUseFallbackWhenEventStartNull() {
        Ticket ticket = new Ticket();
        ticket.setId(6L);
        ticket.setTicketType(TicketType.MOVIE);
        ticket.setStatus(TicketStatus.DEPOSIT_LOCKED);
        ticket.setEventStartAt(null);

        when(ticketRepository.findById(6L)).thenReturn(Optional.of(ticket));

        Ticket result = stateMachineService.transition(6L, TicketStatus.AWAITING_USE);

        assertEquals(TicketStatus.AWAITING_USE, result.getStatus());
        assertNotNull(result.getReceivedAt());
        assertEquals(result.getReceivedAt().plus(Duration.ofHours(2)), result.getDisputeWindowClosesAt());
    }

    @Test
    @DisplayName("transition: DISPUTE_UNDER_REVIEW -> AWAITING_USE giữ nguyên receivedAt và disputeWindowClosesAt gốc")
    void testTransitionDisputeWithdrawnToAwaitingUsePreservesOriginalTimestamps() {
        Instant originalReceived = Instant.now().minus(Duration.ofHours(3));
        Instant originalClose = Instant.now().plus(Duration.ofHours(5));
        Ticket ticket = new Ticket();
        ticket.setId(7L);
        ticket.setTicketType(TicketType.MOVIE);
        ticket.setStatus(TicketStatus.DISPUTE_UNDER_REVIEW);
        ticket.setTransferredAt(originalReceived);
        ticket.setReceivedAt(originalReceived);
        ticket.setDisputeWindowClosesAt(originalClose);

        when(ticketRepository.findById(7L)).thenReturn(Optional.of(ticket));

        Ticket result = stateMachineService.transition(7L, TicketStatus.AWAITING_USE);

        assertEquals(TicketStatus.AWAITING_USE, result.getStatus());
        assertEquals(originalReceived, result.getReceivedAt());
        assertEquals(originalClose, result.getDisputeWindowClosesAt());
    }

    @Test
    @DisplayName("transition: Chuyển trái phép từ PAYMENT_ESCROWED quay lại LISTED phải ném IllegalTicketTransitionException (AC-01.3)")
    void testIllegalTransitionPaymentEscrowedToListed() {
        Ticket ticket = new Ticket();
        ticket.setId(5L);
        ticket.setStatus(TicketStatus.PAYMENT_ESCROWED);

        when(ticketRepository.findById(5L)).thenReturn(Optional.of(ticket));

        assertThrows(IllegalTicketTransitionException.class, () ->
                stateMachineService.transition(5L, TicketStatus.LISTED));
    }
}
