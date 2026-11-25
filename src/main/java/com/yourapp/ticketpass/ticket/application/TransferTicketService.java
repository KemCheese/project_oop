package com.yourapp.ticketpass.ticket.application;

import com.yourapp.ticketpass.notification.application.NotificationService;
import com.yourapp.ticketpass.notification.domain.NotificationType;
import com.yourapp.ticketpass.reputation.application.ReputationPolicyService;
import com.yourapp.ticketpass.shared.event.TicketDepositLockedEvent;
import com.yourapp.ticketpass.shared.event.TicketEscrowPaidEvent;
import com.yourapp.ticketpass.shared.exception.BusinessRuleViolationException;
import com.yourapp.ticketpass.shared.exception.ResourceNotFoundException;
import com.yourapp.ticketpass.shared.storage.FileStorageService;
import com.yourapp.ticketpass.ticket.domain.Ticket;
import com.yourapp.ticketpass.ticket.domain.TicketAdapter;
import com.yourapp.ticketpass.ticket.domain.TicketRepository;
import com.yourapp.ticketpass.ticket.domain.TicketStatus;
import com.yourapp.ticketpass.ticket.domain.TransferInstruction;
import com.yourapp.ticketpass.ticket.domain.ValidationResult;
import com.yourapp.ticketpass.ticket.web.dto.CreateTicketRequest;
import com.yourapp.ticketpass.ticket.web.dto.TicketResponse;
import com.yourapp.ticketpass.user.domain.StarredTicketRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TransferTicketService {

    private final TicketRepository ticketRepository;

    private final TicketStateMachineService stateMachineService;

    private final ValidateTicketService validateTicketService;

    private final FileStorageService fileStorageService;

    private final ApplicationEventPublisher eventPublisher;

    private final TicketReminderService ticketReminderService;

    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private ReputationPolicyService reputationPolicyService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private StarredTicketRepository starredTicketRepository;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private NotificationService notificationService;

    @Transactional
    public TicketResponse createListing(CreateTicketRequest request, Long sellerId) {
        if (reputationPolicyService != null && request != null && request.getSalePrice() != null) {
            reputationPolicyService.assertCanTransact(sellerId, request.getSalePrice(), "đăng bán vé");
        }

        Ticket ticket = new Ticket();
        ticket.setSellerId(sellerId);
        ticket.setTicketType(request.getTicketType());
        ticket.setTitle(request.getTitle());
        ticket.setEventName(request.getEventName());
        ticket.setVenue(request.getVenue());
        ticket.setSeatInfo(request.getSeatInfo());
        ZoneId zoneId = resolveZoneId();
        ticket.setEventStartAt(request.getEventStartAt() != null
                ? request.getEventStartAt().atZone(zoneId).toInstant()
                : null);
        ticket.setEventEndAt(request.getEventEndAt() != null
                ? request.getEventEndAt().atZone(zoneId).toInstant()
                : null);
        ticket.setOriginalPrice(request.getOriginalPrice());
        ticket.setSalePrice(request.getSalePrice());
        ticket.setProviderName(request.getProviderName());
        ticket.setProviderOrderCode(request.getProviderOrderCode());
        ticket.setTicketImageStorageKey(request.getTicketImageStorageKey());
        ticket.setQrImageStorageKey(request.getQrImageStorageKey());
        ticket.setStatus(TicketStatus.LISTED);
        ticket.setListedAt(resolveNow());

        ValidationResult result = validateTicketService.validateForListing(ticket);
        if (!result.isValid()) {
            String errorMessage = result.getErrors().stream()
                    .collect(Collectors.joining("; "));
            throw new BusinessRuleViolationException(errorMessage);
        }

        TicketAdapter adapter = stateMachineService.resolveAdapter(ticket.getTicketType());
        TransferInstruction instruction = adapter.buildTransferInstruction(ticket);
        ticket.setTransferInstruction(formatTransferInstruction(instruction));

        Ticket saved = ticketRepository.save(ticket);

        return toResponse(saved);
    }

    @Transactional
    public void markPaymentEscrowed(Long ticketId, Long buyerId, Long escrowTransactionId) {
        ValidationResult result = validateTicketService.validateForPurchase(ticketId, buyerId);
        if (!result.isValid()) {
            String errorMessage = result.getErrors().stream()
                    .collect(Collectors.joining("; "));
            throw new BusinessRuleViolationException(errorMessage);
        }

        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket", ticketId));
        ticket.setBuyerId(buyerId);
        ticket.setEscrowTransactionId(escrowTransactionId);
        ticketRepository.save(ticket);

        stateMachineService.transition(ticketId, TicketStatus.PAYMENT_ESCROWED);

        if (eventPublisher != null) {
            eventPublisher.publishEvent(new TicketEscrowPaidEvent(
                    ticket.getId(),
                    ticket.getSellerId(),
                    ticket.getBuyerId(),
                    ticket.getTitle(),
                    ticket.getSalePrice(),
                    Instant.now()
            ));
        }
    }

    @Transactional
    public void markDepositLocked(Long ticketId, Long depositId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket", ticketId));
        ticket.setDepositId(depositId);
        ticketRepository.save(ticket);

        stateMachineService.transition(ticketId, TicketStatus.DEPOSIT_LOCKED);

        Ticket unlockedTicket = stateMachineService.transition(ticketId, TicketStatus.AWAITING_USE);
        Ticket targetTicket = unlockedTicket != null ? unlockedTicket : ticket;

        if (eventPublisher != null) {
            eventPublisher.publishEvent(new TicketDepositLockedEvent(
                    targetTicket.getId(),
                    targetTicket.getSellerId(),
                    targetTicket.getBuyerId(),
                    targetTicket.getTitle(),
                    targetTicket.getSalePrice(),
                    resolveNow()
            ));
        }

        if (ticketReminderService != null) {
            ticketReminderService.triggerEarlyReminderIfWithinLeadWindow(targetTicket);
        }
    }

    @Transactional
    public void cancelListing(Long ticketId, Long sellerId, String reason) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket", ticketId));

        if (!ticket.getSellerId().equals(sellerId)) {
            throw new BusinessRuleViolationException(
                    "Ban khong phai la chu so huu cua ve nay.");
        }

        if (ticket.getStatus() != TicketStatus.LISTED) {
            throw new BusinessRuleViolationException(
                    "Khong the huy ve o trang thai " + ticket.getStatus()
                            + ". Chi co the huy khi ve dang o trang thai LISTED.");
        }

        stateMachineService.transition(ticketId, TicketStatus.CANCELLED);

        if (starredTicketRepository != null && notificationService != null) {
            try {
                List<Long> watchers = starredTicketRepository.findUserIdsByTicketId(ticketId);
                if (watchers != null) {
                    for (Long watcherId : watchers) {
                        if (watcherId != null && !watcherId.equals(sellerId)) {
                            notificationService.sendNotification(
                                    watcherId,
                                    "Vé theo dõi đã bị gỡ khỏi sàn",
                                    String.format("Vé \"%s\" mà bạn đang theo dõi đã được người bán gỡ khỏi sàn.",
                                            ticket.getTitle()),
                                    NotificationType.SYSTEM,
                                    "/tickets/" + ticketId);
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }
    }

    private String formatTransferInstruction(TransferInstruction instruction) {
        if (instruction == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(instruction.getTicketType()).append("] ");
        sb.append("Kenh: ").append(instruction.getChannel()).append("\n");

        if (instruction.getSteps() != null) {
            for (int i = 0; i < instruction.getSteps().size(); i++) {
                sb.append("  ").append(i + 1).append(". ").append(instruction.getSteps().get(i)).append("\n");
            }
        }

        if (instruction.getNote() != null && !instruction.getNote().isBlank()) {
            sb.append("Luu y: ").append(instruction.getNote());
        }
        return sb.toString();
    }

    private TicketResponse toResponse(Ticket ticket) {
        TicketResponse response = new TicketResponse();
        response.setId(ticket.getId());
        response.setSellerId(ticket.getSellerId());
        response.setBuyerId(ticket.getBuyerId());
        response.setTicketType(ticket.getTicketType());
        response.setStatus(ticket.getStatus());
        response.setTitle(ticket.getTitle());
        response.setEventName(ticket.getEventName());
        response.setVenue(ticket.getVenue());
        response.setSeatInfo(ticket.getSeatInfo());
        response.setEventStartAt(ticket.getEventStartAt());
        response.setEventEndAt(ticket.getEventEndAt());
        response.setOriginalPrice(ticket.getOriginalPrice());
        response.setSalePrice(ticket.getSalePrice());
        response.setTransferInstruction(ticket.getTransferInstruction());
        response.setDisputeWindowClosesAt(ticket.getDisputeWindowClosesAt());
        response.setCreatedAt(ticket.getCreatedAt());
        response.setReceivedAt(ticket.getReceivedAt());

        Instant now = resolveNow();
        if (ticket.getDisputeWindowClosesAt() != null) {
            response.setQrExpired(now.isAfter(ticket.getDisputeWindowClosesAt()));
        } else if (ticket.getEventStartAt() != null) {
            response.setQrExpired(now.isAfter(ticket.getEventStartAt().plus(java.time.Duration.ofMinutes(30))));
        } else {
            response.setQrExpired(false);
        }

        if (ticket.getTicketImageStorageKey() != null && !ticket.getTicketImageStorageKey().isBlank()) {
            response.setTicketImageUrl(fileStorageService.buildPublicUrl(ticket.getTicketImageStorageKey()));
        }
        if (ticket.getQrImageStorageKey() != null && !ticket.getQrImageStorageKey().isBlank()) {
            response.setQrImageUrl(fileStorageService.buildPublicUrl(ticket.getQrImageStorageKey()));
        }

        return response;
    }

    private Instant resolveNow() {
        Instant instant = clock != null ? clock.instant() : null;
        return instant != null ? instant : Instant.now();
    }

    private ZoneId resolveZoneId() {
        ZoneId zone = clock != null ? clock.getZone() : null;
        return zone != null ? zone : ZoneId.systemDefault();
    }
}
