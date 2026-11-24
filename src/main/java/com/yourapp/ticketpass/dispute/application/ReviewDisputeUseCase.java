package com.yourapp.ticketpass.dispute.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
import com.yourapp.ticketpass.ticket.domain.TicketStatus;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewDisputeUseCase {

    private final DisputeRepository disputeRepository;

    private final DisputeEvidenceRepository disputeEvidenceRepository;

    private final RefundBuyerUseCase refundBuyerUseCase;

    private final ReleaseFundsUseCase releaseFundsUseCase;

    private final TicketStateMachineService ticketStateMachineService;

    private final ApplicationEventPublisher eventPublisher;

    public List<Dispute> listPendingReview() {
        List<DisputeStatus> pendingStatuses = List.of(
                DisputeStatus.OPEN,
                DisputeStatus.AWAITING_SELLER_RESPONSE,
                DisputeStatus.SELLER_RESPONDED,
                DisputeStatus.UNDER_REVIEW,
                DisputeStatus.EXPIRED
        );

        List<Dispute> result = new ArrayList<>();
        for (DisputeStatus status : pendingStatuses) {
            List<Dispute> disputes = disputeRepository.findByStatus(status);
            if (disputes != null) {
                result.addAll(disputes);
            }
        }

        result.sort(Comparator.comparing(Dispute::getFiledAt, Comparator.nullsLast(Comparator.naturalOrder())));
        return result;
    }

    @Transactional
    public Dispute resolveBuyerWins(Long disputeId, Long adminId, String resolutionNote) {
        if (disputeId == null) {
            throw new BusinessRuleViolationException("ID khiếu nại không được để trống.");
        }
        if (adminId == null) {
            throw new BusinessRuleViolationException("ID quản trị viên không được để trống.");
        }
        if (resolutionNote == null || resolutionNote.trim().isEmpty()) {
            throw new BusinessRuleViolationException("Ghi chú phán quyết của quản trị viên không được để trống.");
        }

        Dispute dispute = disputeRepository.findById(disputeId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy khiếu nại với ID: " + disputeId));

        DisputeStatus currentStatus = dispute.getStatus();
        if (currentStatus == DisputeStatus.RESOLVED_BUYER_WINS || currentStatus == DisputeStatus.RESOLVED_SELLER_WINS) {
            throw new BusinessRuleViolationException("Khiếu nại #" + disputeId + " đã có phán quyết trước đó: " + currentStatus);
        }
        if (currentStatus == DisputeStatus.WITHDRAWN) {
            throw new BusinessRuleViolationException("Khiếu nại #" + disputeId + " đã được người mua rút đơn.");
        }

        ForfeitBreakdown breakdown = refundBuyerUseCase.refundWithForfeitedDeposit(
                dispute.getTicketId(), resolutionNote.trim());

        Instant now = Instant.now();
        dispute.setTotalDeposit(breakdown != null ? breakdown.getTotalDeposit() : BigDecimal.ZERO);
        dispute.setFounderCut(breakdown != null ? breakdown.getFounderCut() : BigDecimal.ZERO);
        dispute.setBuyerCompensation(breakdown != null ? breakdown.getBuyerCompensation() : BigDecimal.ZERO);
        dispute.setStatus(DisputeStatus.RESOLVED_BUYER_WINS);
        dispute.setOutcome(DisputeOutcome.BUYER_WINS);
        dispute.setReviewedByAdminId(adminId);
        dispute.setReviewedAt(now);
        dispute.setResolutionNote(resolutionNote.trim());

        Dispute savedDispute = disputeRepository.save(dispute);

        ticketStateMachineService.transition(dispute.getTicketId(), TicketStatus.RESOLVED_BUYER_WINS);

        DisputeResolvedEvent event = new DisputeResolvedEvent(
                savedDispute.getId(),
                savedDispute.getTicketId(),
                savedDispute.getEscrowTransactionId(),
                savedDispute.getDepositId(),
                savedDispute.getBuyerId(),
                savedDispute.getSellerId(),
                DisputeOutcome.BUYER_WINS,
                savedDispute.getTotalDeposit(),
                savedDispute.getFounderCut(),
                savedDispute.getBuyerCompensation(),
                adminId,
                now
        );
        eventPublisher.publishEvent(event);
        log.info("[ReviewDispute] Admin #{} phán quyết BUYER_WINS cho khiếu nại #{}", adminId, disputeId);

        return savedDispute;
    }

    @Transactional
    public Dispute resolveSellerWins(Long disputeId, Long adminId, String resolutionNote) {
        if (disputeId == null) {
            throw new BusinessRuleViolationException("ID khiếu nại không được để trống.");
        }
        if (adminId == null) {
            throw new BusinessRuleViolationException("ID quản trị viên không được để trống.");
        }
        if (resolutionNote == null || resolutionNote.trim().isEmpty()) {
            throw new BusinessRuleViolationException("Ghi chú phán quyết của quản trị viên không được để trống.");
        }

        Dispute dispute = disputeRepository.findById(disputeId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy khiếu nại với ID: " + disputeId));

        DisputeStatus currentStatus = dispute.getStatus();
        if (currentStatus == DisputeStatus.RESOLVED_BUYER_WINS || currentStatus == DisputeStatus.RESOLVED_SELLER_WINS) {
            throw new BusinessRuleViolationException("Khiếu nại #" + disputeId + " đã có phán quyết trước đó: " + currentStatus);
        }
        if (currentStatus == DisputeStatus.WITHDRAWN) {
            throw new BusinessRuleViolationException("Khiếu nại #" + disputeId + " đã được người mua rút đơn.");
        }

        releaseFundsUseCase.release(dispute.getTicketId(), CompletionReason.DISPUTE_RESOLVED_SELLER_WINS);

        Instant now = Instant.now();
        dispute.setStatus(DisputeStatus.RESOLVED_SELLER_WINS);
        dispute.setOutcome(DisputeOutcome.SELLER_WINS);
        dispute.setReviewedByAdminId(adminId);
        dispute.setReviewedAt(now);
        dispute.setResolutionNote(resolutionNote.trim());
        dispute.setFounderCut(BigDecimal.ZERO);
        dispute.setBuyerCompensation(BigDecimal.ZERO);
        if (dispute.getTotalDeposit() == null) {
            dispute.setTotalDeposit(BigDecimal.ZERO);
        }

        Dispute savedDispute = disputeRepository.save(dispute);

        DisputeResolvedEvent event = new DisputeResolvedEvent(
                savedDispute.getId(),
                savedDispute.getTicketId(),
                savedDispute.getEscrowTransactionId(),
                savedDispute.getDepositId(),
                savedDispute.getBuyerId(),
                savedDispute.getSellerId(),
                DisputeOutcome.SELLER_WINS,
                savedDispute.getTotalDeposit(),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                adminId,
                now
        );
        eventPublisher.publishEvent(event);
        log.info("[ReviewDispute] Admin #{} phán quyết SELLER_WINS cho khiếu nại #{}", adminId, disputeId);

        return savedDispute;
    }
}
