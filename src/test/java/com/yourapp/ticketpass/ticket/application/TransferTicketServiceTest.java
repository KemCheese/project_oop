package com.yourapp.ticketpass.ticket.application;

import com.yourapp.ticketpass.shared.exception.BusinessRuleViolationException;
import com.yourapp.ticketpass.shared.storage.FileStorageService;
import com.yourapp.ticketpass.ticket.domain.Ticket;
import com.yourapp.ticketpass.ticket.domain.TicketRepository;
import com.yourapp.ticketpass.ticket.domain.TicketStatus;
import org.springframework.context.ApplicationEventPublisher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransferTicketServiceTest {

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private TicketStateMachineService stateMachineService;

    @Mock
    private ValidateTicketService validateTicketService;

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private TicketReminderService ticketReminderService;

    @Mock
    private Clock clock;

    @InjectMocks
    private TransferTicketService transferTicketService;

    @Test
    @DisplayName("autoConfirmReceipt: Chuyển vé TRANSFERRED sang AWAITING_USE thành công (AC-02.2)")
    void testAutoConfirmReceiptSuccess() {
        Ticket ticket = new Ticket();
        ticket.setId(401L);
        ticket.setStatus(TicketStatus.TRANSFERRED);

        when(ticketRepository.findById(401L)).thenReturn(Optional.of(ticket));

        transferTicketService.autoConfirmReceipt(401L);

        verify(stateMachineService).transition(401L, TicketStatus.AWAITING_USE);
    }

    @Test
    @DisplayName("autoConfirmReceipt: Bỏ qua nếu vé không còn ở trạng thái TRANSFERRED (AWAITING_USE / CANCELLED)")
    void testAutoConfirmReceiptIgnoresNonTransferredTicket() {
        Ticket awaitingTicket = new Ticket();
        awaitingTicket.setId(402L);
        awaitingTicket.setStatus(TicketStatus.AWAITING_USE);
        when(ticketRepository.findById(402L)).thenReturn(Optional.of(awaitingTicket));

        Ticket cancelledTicket = new Ticket();
        cancelledTicket.setId(403L);
        cancelledTicket.setStatus(TicketStatus.CANCELLED);
        when(ticketRepository.findById(403L)).thenReturn(Optional.of(cancelledTicket));

        transferTicketService.autoConfirmReceipt(402L);
        transferTicketService.autoConfirmReceipt(403L);

        verify(stateMachineService, never()).transition(anyLong(), any());
    }

    @Test
    @DisplayName("markDepositLocked: Tự động chuyển tiếp từ DEPOSIT_LOCKED sang TRANSFERRED để mở vé cho buyer")
    void testMarkDepositLockedAutoTransitionsToTransferred() {
        Ticket ticket = new Ticket();
        ticket.setId(406L);
        ticket.setStatus(TicketStatus.PAYMENT_ESCROWED);

        when(ticketRepository.findById(406L)).thenReturn(Optional.of(ticket));

        transferTicketService.markDepositLocked(406L, 50L);

        verify(stateMachineService).transition(406L, TicketStatus.DEPOSIT_LOCKED);
        verify(stateMachineService).transition(406L, TicketStatus.TRANSFERRED);
        verify(ticketReminderService).triggerEarlyReminderIfWithinLeadWindow(ticket);
    }

    @Test
    @DisplayName("markTransferred: Idempotent khi vé đã ở TRANSFERRED thì không báo lỗi")
    void testMarkTransferredIdempotentWhenAlreadyTransferred() {
        Ticket ticket = new Ticket();
        ticket.setId(407L);
        ticket.setSellerId(100L);
        ticket.setStatus(TicketStatus.TRANSFERRED);

        when(ticketRepository.findById(407L)).thenReturn(Optional.of(ticket));

        transferTicketService.markTransferred(407L, 100L);

        verify(stateMachineService, never()).transition(anyLong(), any());
    }

    @Test
    @DisplayName("createListing: Map đầy đủ eventEndAt sang entity Ticket và trả về trong TicketResponse")
    void testCreateListingMapsEventEndAt() {
        java.time.LocalDateTime startLdt = java.time.LocalDateTime.of(2026, 11, 18, 13, 0);
        java.time.LocalDateTime endLdt = java.time.LocalDateTime.of(2026, 11, 18, 15, 0);

        com.yourapp.ticketpass.ticket.web.dto.CreateTicketRequest req =
                new com.yourapp.ticketpass.ticket.web.dto.CreateTicketRequest();
        req.setTicketType(com.yourapp.ticketpass.ticket.domain.TicketType.MOVIE);
        req.setTitle("Vé Dune 13h");
        req.setEventName("Dune Part 3");
        req.setSeatInfo("H10");
        req.setEventStartAt(startLdt);
        req.setEventEndAt(endLdt);
        req.setSalePrice(new java.math.BigDecimal("150000"));
        req.setOriginalPrice(new java.math.BigDecimal("200000"));

        when(validateTicketService.validateForListing(any(Ticket.class)))
                .thenReturn(com.yourapp.ticketpass.ticket.domain.ValidationResult.ok());
        com.yourapp.ticketpass.ticket.domain.TicketAdapter adapter = mock(com.yourapp.ticketpass.ticket.domain.TicketAdapter.class);
        when(adapter.buildTransferInstruction(any(Ticket.class)))
                .thenReturn(new com.yourapp.ticketpass.ticket.domain.TransferInstruction());
        when(stateMachineService.resolveAdapter(com.yourapp.ticketpass.ticket.domain.TicketType.MOVIE))
                .thenReturn(adapter);
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));

        var res = transferTicketService.createListing(req, 10L);

        org.junit.jupiter.api.Assertions.assertEquals(
                endLdt.atZone(java.time.ZoneId.systemDefault()).toInstant(),
                res.getEventEndAt());
    }
}
