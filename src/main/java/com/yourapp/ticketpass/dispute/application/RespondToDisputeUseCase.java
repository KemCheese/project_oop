package com.yourapp.ticketpass.dispute.application;

import java.time.Instant;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.yourapp.ticketpass.dispute.domain.Dispute;
import com.yourapp.ticketpass.dispute.domain.DisputeEvidenceRepository;
import com.yourapp.ticketpass.dispute.domain.DisputeRepository;
import com.yourapp.ticketpass.dispute.domain.DisputeStatus;
import com.yourapp.ticketpass.shared.event.DisputeOutcome;
import com.yourapp.ticketpass.shared.event.DisputeRespondedEvent;
import com.yourapp.ticketpass.shared.event.DisputeWithdrawnEvent;
import com.yourapp.ticketpass.shared.exception.BusinessRuleViolationException;
import com.yourapp.ticketpass.shared.exception.ResourceNotFoundException;
import com.yourapp.ticketpass.shared.storage.FileStorageService;
import com.yourapp.ticketpass.ticket.application.TicketStateMachineService;
import com.yourapp.ticketpass.ticket.domain.TicketStatus;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class RespondToDisputeUseCase {

    private final DisputeRepository disputeRepository;

    private final DisputeEvidenceRepository disputeEvidenceRepository;

    private final FileStorageService fileStorageService;

    private final TicketStateMachineService ticketStateMachineService;

    @Autowired(required = false)
    private ApplicationEventPublisher eventPublisher;

    @Transactional
    public Dispute submitSellerResponse(Long disputeId, Long sellerId, String response) {
        if (disputeId == null) {
            throw new BusinessRuleViolationException("ID khiếu nại không được để trống.");
        }
        if (sellerId == null) {
            throw new BusinessRuleViolationException("ID người bán không được để trống.");
        }
        if (response == null || response.trim().isEmpty()) {
            throw new BusinessRuleViolationException("Nội dung giải trình của người bán không được để trống.");
        }

        Dispute dispute = disputeRepository.findById(disputeId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy khiếu nại với ID: " + disputeId));

        if (!Objects.equals(dispute.getSellerId(), sellerId)) {
            throw new BusinessRuleViolationException("Người dùng (ID: " + sellerId + ") không phải là người bán của khiếu nại này.");
        }

        DisputeStatus currentStatus = dispute.getStatus();
        if (currentStatus != DisputeStatus.OPEN
                && currentStatus != DisputeStatus.AWAITING_SELLER_RESPONSE
                && currentStatus != DisputeStatus.UNDER_REVIEW) {
            throw new BusinessRuleViolationException("Khiếu nại không ở trạng thái cho phép người bán gửi giải trình (trạng thái hiện tại: " + currentStatus + ").");
        }

        if (dispute.getSellerResponse() != null && !dispute.getSellerResponse().isBlank()) {
            throw new BusinessRuleViolationException("Bạn đã gửi giải trình cho khiếu nại này trước đó.");
        }

        Instant now = Instant.now();
        if (dispute.getSellerResponseDeadline() != null && now.isAfter(dispute.getSellerResponseDeadline())) {
            throw new BusinessRuleViolationException("Đã quá hạn gửi giải trình của người bán (hạn chót: " + dispute.getSellerResponseDeadline() + ").");
        }

        dispute.setSellerResponse(response.trim());
        dispute.setSellerRespondedAt(now);
        dispute.setStatus(DisputeStatus.SELLER_RESPONDED);

        Dispute savedDispute = disputeRepository.save(dispute);
        log.info("Người bán (ID: {}) đã gửi giải trình thành công cho khiếu nại (ID: {})", sellerId, disputeId);

        if (eventPublisher != null) {
            eventPublisher.publishEvent(new DisputeRespondedEvent(
                    disputeId,
                    dispute.getTicketId(),
                    sellerId,
                    dispute.getBuyerId(),
                    "Vé #" + dispute.getTicketId(),
                    now
            ));
        }
        return savedDispute;
    }

    @Transactional
    public void withdrawDispute(Long disputeId, Long buyerId, String reason) {
        if (disputeId == null) {
            throw new BusinessRuleViolationException("ID khiếu nại không được để trống.");
        }
        if (buyerId == null) {
            throw new BusinessRuleViolationException("ID người mua không được để trống.");
        }
        if (reason == null || reason.trim().isEmpty()) {
            throw new BusinessRuleViolationException("Lý do rút khiếu nại không được để trống.");
        }

        Dispute dispute = disputeRepository.findById(disputeId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy khiếu nại với ID: " + disputeId));

        if (!Objects.equals(dispute.getBuyerId(), buyerId)) {
            throw new BusinessRuleViolationException("Người dùng (ID: " + buyerId + ") không phải là người mua đã mở khiếu nại này.");
        }

        DisputeStatus currentStatus = dispute.getStatus();
        if (currentStatus == DisputeStatus.RESOLVED_BUYER_WINS || currentStatus == DisputeStatus.RESOLVED_SELLER_WINS) {
            throw new BusinessRuleViolationException("Không thể rút khiếu nại đã có phán quyết của quản trị viên (trạng thái: " + currentStatus + ").");
        }
        if (currentStatus == DisputeStatus.WITHDRAWN) {
            throw new BusinessRuleViolationException("Khiếu nại này đã được rút trước đó.");
        }

        Instant now = Instant.now();
        dispute.setStatus(DisputeStatus.WITHDRAWN);
        dispute.setOutcome(DisputeOutcome.WITHDRAWN);
        dispute.setResolutionNote(reason.trim());
        dispute.setReviewedAt(now);
        disputeRepository.save(dispute);

        ticketStateMachineService.transition(dispute.getTicketId(), TicketStatus.AWAITING_USE);
        log.info("Người mua (ID: {}) đã rút khiếu nại (ID: {}). Vé (ID: {}) đã được chuyển về AWAITING_USE.",
                buyerId, disputeId, dispute.getTicketId());

        if (eventPublisher != null) {
            eventPublisher.publishEvent(new DisputeWithdrawnEvent(
                    disputeId,
                    dispute.getTicketId(),
                    dispute.getSellerId(),
                    buyerId,
                    "Vé #" + dispute.getTicketId(),
                    now
            ));
        }
    }
}
