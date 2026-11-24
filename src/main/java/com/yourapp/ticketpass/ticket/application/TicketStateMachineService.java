package com.yourapp.ticketpass.ticket.application;

import com.yourapp.ticketpass.shared.exception.BusinessRuleViolationException;
import com.yourapp.ticketpass.shared.exception.ResourceNotFoundException;
import com.yourapp.ticketpass.ticket.domain.IllegalTicketTransitionException;
import com.yourapp.ticketpass.ticket.domain.Ticket;
import com.yourapp.ticketpass.ticket.domain.TicketAdapter;
import com.yourapp.ticketpass.ticket.domain.TicketRepository;
import com.yourapp.ticketpass.ticket.domain.TicketStatus;
import com.yourapp.ticketpass.ticket.domain.TicketType;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TicketStateMachineService {

    private static final Map<TicketStatus, Set<TicketStatus>> ALLOWED = new EnumMap<>(TicketStatus.class);

    static {
        ALLOWED.put(TicketStatus.LISTED, EnumSet.of(TicketStatus.PAYMENT_ESCROWED, TicketStatus.CANCELLED));
        ALLOWED.put(TicketStatus.PAYMENT_ESCROWED, EnumSet.of(TicketStatus.DEPOSIT_LOCKED, TicketStatus.CANCELLED));
        ALLOWED.put(TicketStatus.DEPOSIT_LOCKED, EnumSet.of(TicketStatus.AWAITING_USE, TicketStatus.CANCELLED));
        ALLOWED.put(TicketStatus.AWAITING_USE, EnumSet.of(TicketStatus.AUTO_COMPLETE, TicketStatus.DISPUTE_UNDER_REVIEW));
        ALLOWED.put(TicketStatus.DISPUTE_UNDER_REVIEW, EnumSet.of(TicketStatus.RESOLVED_BUYER_WINS, TicketStatus.RESOLVED_SELLER_WINS, TicketStatus.AWAITING_USE));
        ALLOWED.put(TicketStatus.AUTO_COMPLETE, EnumSet.noneOf(TicketStatus.class));
        ALLOWED.put(TicketStatus.RESOLVED_BUYER_WINS, EnumSet.noneOf(TicketStatus.class));
        ALLOWED.put(TicketStatus.RESOLVED_SELLER_WINS, EnumSet.noneOf(TicketStatus.class));
        ALLOWED.put(TicketStatus.CANCELLED, EnumSet.noneOf(TicketStatus.class));
    }

    private final TicketRepository ticketRepository;

    private final List<TicketAdapter> ticketAdapters;

    private Map<TicketType, TicketAdapter> adapterMap;

    @PostConstruct
    void initAdapterMap() {
        adapterMap = new EnumMap<>(TicketType.class);
        for (TicketAdapter adapter : ticketAdapters) {
            adapterMap.put(adapter.supportedType(), adapter);
        }
    }

    public Set<TicketStatus> allowedNextStates(TicketStatus current) {
        if (current == null) {
            return EnumSet.noneOf(TicketStatus.class);
        }
        return ALLOWED.getOrDefault(current, EnumSet.noneOf(TicketStatus.class));
    }

    public void assertCanTransition(TicketStatus from, TicketStatus to) {
        if (!allowedNextStates(from).contains(to)) {
            throw new IllegalTicketTransitionException(from, to);
        }
    }

    @Transactional
    public Ticket transition(Long ticketId, TicketStatus target) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket", ticketId));

        assertCanTransition(ticket.getStatus(), target);

        ticket.setStatus(target);

        Instant now = Instant.now();

        switch (target) {
            case LISTED:
                ticket.setListedAt(now);
                break;
            case PAYMENT_ESCROWED:
                ticket.setEscrowedAt(now);
                break;
            case AWAITING_USE:
                if (ticket.getTransferredAt() == null) {
                    ticket.setTransferredAt(now);
                }
                if (ticket.getReceivedAt() == null) {
                    ticket.setReceivedAt(now);
                }
                if (ticket.getDisputeWindowClosesAt() == null) {
                    ticket.setDisputeWindowClosesAt(computeDisputeWindowClose(ticket));
                }
                break;
            case AUTO_COMPLETE:
            case RESOLVED_BUYER_WINS:
            case RESOLVED_SELLER_WINS:
            case CANCELLED:
                ticket.setCompletedAt(now);
                break;
            default:
                break;
        }

        return ticketRepository.save(ticket);
    }

    public TicketAdapter resolveAdapter(TicketType ticketType) {
        TicketAdapter adapter = adapterMap.get(ticketType);
        if (adapter == null) {
            throw new BusinessRuleViolationException(
                    "Loai ve khong duoc ho tro: " + ticketType);
        }
        return adapter;
    }

    public Instant computeDisputeWindowClose(Ticket ticket) {
        if (ticket.getEventStartAt() != null) {
            return ticket.getEventStartAt().plus(Duration.ofMinutes(30));
        }

        TicketAdapter adapter = resolveAdapter(ticket.getTicketType());
        Duration disputeWindow = adapter.disputeWindowAfterTransfer();

        Instant baseTime = ticket.getReceivedAt() != null
                ? ticket.getReceivedAt()
                : (ticket.getTransferredAt() != null ? ticket.getTransferredAt() : Instant.now());

        return baseTime.plus(disputeWindow);
    }
}
