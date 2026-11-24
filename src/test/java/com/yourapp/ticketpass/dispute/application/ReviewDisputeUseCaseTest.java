package com.yourapp.ticketpass.dispute.application;

import com.yourapp.ticketpass.dispute.domain.Dispute;
import com.yourapp.ticketpass.dispute.domain.DisputeEvidenceRepository;
import com.yourapp.ticketpass.dispute.domain.DisputeRepository;
import com.yourapp.ticketpass.dispute.domain.DisputeStatus;
import com.yourapp.ticketpass.payment.application.RefundBuyerUseCase;
import com.yourapp.ticketpass.payment.application.ReleaseFundsUseCase;
import com.yourapp.ticketpass.shared.dto.ForfeitBreakdown;
import com.yourapp.ticketpass.shared.event.CompletionReason;
import com.yourapp.ticketpass.shared.event.DisputeOutcome;
import com.yourapp.ticketpass.shared.event.DisputeResolvedEvent;
import com.yourapp.ticketpass.shared.exception.BusinessRuleViolationException;
import com.yourapp.ticketpass.shared.exception.ResourceNotFoundException;
import com.yourapp.ticketpass.ticket.application.TicketStateMachineService;
import com.yourapp.ticketpass.ticket.domain.IllegalTicketTransitionException;
import com.yourapp.ticketpass.ticket.domain.Ticket;
import com.yourapp.ticketpass.ticket.domain.TicketStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewDisputeUseCaseTest {

    @Mock
    private DisputeRepository disputeRepository;

    @Mock
    private DisputeEvidenceRepository disputeEvidenceRepository;

    @Mock
    private RefundBuyerUseCase refundBuyerUseCase;

    @Mock
    private ReleaseFundsUseCase releaseFundsUseCase;

    @Mock
    private TicketStateMachineService ticketStateMachineService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private ReviewDisputeUseCase reviewDisputeUseCase;

    @BeforeEach
    void setUp() {
        reviewDisputeUseCase = new ReviewDisputeUseCase(
                disputeRepository,
                disputeEvidenceRepository,
                refundBuyerUseCase,
                releaseFundsUseCase,
                ticketStateMachineService,
                eventPublisher
        );
    }

    @Test
    @DisplayName("resolveSellerWins: Thành công và không gọi lặp transition trạng thái vé sang RESOLVED_SELLER_WINS")
    void testResolveSellerWins_SuccessWithoutDuplicateTransition() {
        Long disputeId = 1L;
        Long ticketId = 100L;
        Long adminId = 999L;
        String note = "Người bán cung cấp đầy đủ bằng chứng vé hợp lệ";

        Dispute dispute = new Dispute();
        dispute.setId(disputeId);
        dispute.setTicketId(ticketId);
        dispute.setSellerId(20L);
        dispute.setBuyerId(10L);
        dispute.setStatus(DisputeStatus.UNDER_REVIEW);
        dispute.setTotalDeposit(new BigDecimal("100000"));

        when(disputeRepository.findById(disputeId)).thenReturn(Optional.of(dispute));
        when(disputeRepository.save(any(Dispute.class))).thenAnswer(invocation -> invocation.getArgument(0));

        doAnswer(invocation -> {
            ticketStateMachineService.transition(ticketId, TicketStatus.RESOLVED_SELLER_WINS);
            return null;
        }).when(releaseFundsUseCase).release(eq(ticketId), eq(CompletionReason.DISPUTE_RESOLVED_SELLER_WINS));

        when(ticketStateMachineService.transition(ticketId, TicketStatus.RESOLVED_SELLER_WINS))
                .thenReturn(new Ticket())
                .thenThrow(new IllegalTicketTransitionException(TicketStatus.RESOLVED_SELLER_WINS, TicketStatus.RESOLVED_SELLER_WINS));

        Dispute result = assertDoesNotThrow(() ->
                reviewDisputeUseCase.resolveSellerWins(disputeId, adminId, note)
        );

        assertNotNull(result);
        assertEquals(DisputeStatus.RESOLVED_SELLER_WINS, result.getStatus());
        assertEquals(DisputeOutcome.SELLER_WINS, result.getOutcome());
        assertEquals(adminId, result.getReviewedByAdminId());
        assertEquals(note, result.getResolutionNote());

        verify(releaseFundsUseCase, times(1)).release(ticketId, CompletionReason.DISPUTE_RESOLVED_SELLER_WINS);

        verify(ticketStateMachineService, times(1)).transition(ticketId, TicketStatus.RESOLVED_SELLER_WINS);

        ArgumentCaptor<DisputeResolvedEvent> eventCaptor = ArgumentCaptor.forClass(DisputeResolvedEvent.class);
        verify(eventPublisher, times(1)).publishEvent(eventCaptor.capture());
        DisputeResolvedEvent publishedEvent = eventCaptor.getValue();
        assertEquals(disputeId, publishedEvent.getDisputeId());
        assertEquals(DisputeOutcome.SELLER_WINS, publishedEvent.getOutcome());
    }

    @Test
    @DisplayName("resolveBuyerWins: Thành công và chuyển trạng thái vé sang RESOLVED_BUYER_WINS đúng hợp đồng")
    void testResolveBuyerWins_Success() {
        Long disputeId = 2L;
        Long ticketId = 200L;
        Long adminId = 999L;
        String note = "Vé của người bán bị lỗi check-in không hợp lệ";

        Dispute dispute = new Dispute();
        dispute.setId(disputeId);
        dispute.setTicketId(ticketId);
        dispute.setSellerId(20L);
        dispute.setBuyerId(10L);
        dispute.setStatus(DisputeStatus.UNDER_REVIEW);
        dispute.setTotalDeposit(new BigDecimal("100000"));

        when(disputeRepository.findById(disputeId)).thenReturn(Optional.of(dispute));
        when(disputeRepository.save(any(Dispute.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ForfeitBreakdown breakdown = new ForfeitBreakdown(
                new BigDecimal("100000"),
                new BigDecimal("7000"),
                new BigDecimal("93000")
        );
        when(refundBuyerUseCase.refundWithForfeitedDeposit(eq(ticketId), eq(note))).thenReturn(breakdown);
        when(ticketStateMachineService.transition(ticketId, TicketStatus.RESOLVED_BUYER_WINS)).thenReturn(new Ticket());

        Dispute result = reviewDisputeUseCase.resolveBuyerWins(disputeId, adminId, note);

        assertNotNull(result);
        assertEquals(DisputeStatus.RESOLVED_BUYER_WINS, result.getStatus());
        assertEquals(DisputeOutcome.BUYER_WINS, result.getOutcome());
        assertEquals(new BigDecimal("7000"), result.getFounderCut());
        assertEquals(new BigDecimal("93000"), result.getBuyerCompensation());

        verify(refundBuyerUseCase, times(1)).refundWithForfeitedDeposit(ticketId, note);
        verify(ticketStateMachineService, times(1)).transition(ticketId, TicketStatus.RESOLVED_BUYER_WINS);

        ArgumentCaptor<DisputeResolvedEvent> eventCaptor = ArgumentCaptor.forClass(DisputeResolvedEvent.class);
        verify(eventPublisher, times(1)).publishEvent(eventCaptor.capture());
        assertEquals(DisputeOutcome.BUYER_WINS, eventCaptor.getValue().getOutcome());
    }

    @Test
    @DisplayName("resolveSellerWins: Từ chối nếu khiếu nại đã có phán quyết trước đó")
    void testResolveSellerWins_WhenAlreadyResolved_ThrowsException() {
        Long disputeId = 3L;
        Dispute dispute = new Dispute();
        dispute.setId(disputeId);
        dispute.setStatus(DisputeStatus.RESOLVED_SELLER_WINS);

        when(disputeRepository.findById(disputeId)).thenReturn(Optional.of(dispute));

        assertThrows(BusinessRuleViolationException.class, () ->
                reviewDisputeUseCase.resolveSellerWins(disputeId, 999L, "Ghi chú")
        );

        verifyNoInteractions(releaseFundsUseCase);
        verifyNoInteractions(ticketStateMachineService);
    }

    @Test
    @DisplayName("resolveSellerWins: Ném ResourceNotFoundException khi không tìm thấy disputeId")
    void testResolveSellerWins_WhenNotFound_ThrowsException() {
        Long disputeId = 404L;
        when(disputeRepository.findById(disputeId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                reviewDisputeUseCase.resolveSellerWins(disputeId, 999L, "Ghi chú")
        );

        verifyNoInteractions(releaseFundsUseCase);
        verifyNoInteractions(ticketStateMachineService);
    }
}
