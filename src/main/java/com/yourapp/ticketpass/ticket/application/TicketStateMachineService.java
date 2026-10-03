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

/**
 * Hien thuc State pattern cho vong doi cua ve.
 *
 * <p>Day la NOI DUY NHAT trong toan he thong duoc phep doi {@code Ticket.status}. Moi module khac
 * ({@code payment}, {@code dispute}) muon dua ve sang trang thai moi deu phai di qua service nay
 * de bao dam khong co buoc nhay trai voi state machine mo ta trong {@link TicketStatus}.</p>
 *
 * <p>Service inject {@code List<TicketAdapter>} do Spring tap hop, roi chon adapter theo
 * {@link TicketAdapter#supportedType()} de tinh cua so khieu nai rieng cho tung loai ve.</p>
 *
 * <p>Module: ticket</p>
 *
 * Chu so huu: Nguoi A
 */
@Service
@RequiredArgsConstructor
public class TicketStateMachineService {

    // --- Bang chuyen trang thai hop le (Section 4.1, 05-state-machine-giao-dich.md) ---
    // EnumMap + EnumSet: luu tru bang mang bit ordinal, nhanh hon HashMap/HashSet.
    // 3 trang thai terminal khai bao tuong minh voi EnumSet.noneOf() thay vi bo trong
    // de nguoi doc thay ro "co y khong co loi ra", khong phai "quen".
    private static final Map<TicketStatus, Set<TicketStatus>> ALLOWED =
            new EnumMap<>(Map.of(
                    TicketStatus.LISTED,               EnumSet.of(TicketStatus.PAYMENT_ESCROWED, TicketStatus.CANCELLED),
                    TicketStatus.PAYMENT_ESCROWED,      EnumSet.of(TicketStatus.DEPOSIT_LOCKED),
                    TicketStatus.DEPOSIT_LOCKED,         EnumSet.of(TicketStatus.TRANSFERRED),
                    TicketStatus.TRANSFERRED,            EnumSet.of(TicketStatus.AWAITING_USE),
                    TicketStatus.AWAITING_USE,            EnumSet.of(TicketStatus.AUTO_COMPLETE,
                                                                     TicketStatus.DISPUTE_UNDER_REVIEW),
                    TicketStatus.DISPUTE_UNDER_REVIEW,   EnumSet.of(TicketStatus.RESOLVED_BUYER_WINS,
                                                                     TicketStatus.RESOLVED_SELLER_WINS),
                    TicketStatus.AUTO_COMPLETE,           EnumSet.noneOf(TicketStatus.class),
                    TicketStatus.RESOLVED_BUYER_WINS,     EnumSet.noneOf(TicketStatus.class),
                    TicketStatus.RESOLVED_SELLER_WINS,    EnumSet.noneOf(TicketStatus.class),
                    TicketStatus.CANCELLED,               EnumSet.noneOf(TicketStatus.class)
            ));

    private final TicketRepository ticketRepository;

    private final List<TicketAdapter> ticketAdapters;

    /** Cache adapter theo TicketType de tra nhanh, build mot lan duy nhat khi khoi dong. */
    private Map<TicketType, TicketAdapter> adapterMap;

    @PostConstruct
    void initAdapterMap() {
        adapterMap = new EnumMap<>(TicketType.class);
        for (TicketAdapter adapter : ticketAdapters) {
            adapterMap.put(adapter.supportedType(), adapter);
        }
    }

    /**
     * Liet ke cac trang thai ke tiep hop le tu mot trang thai hien tai.
     *
     * @param current trang thai hien tai cua ve
     * @return tap trang thai co the chuyen toi
     */
    public Set<TicketStatus> allowedNextStates(TicketStatus current) {
        if (current == null) {
            return EnumSet.noneOf(TicketStatus.class);
        }
        return ALLOWED.getOrDefault(current, EnumSet.noneOf(TicketStatus.class));
    }

    /**
     * Kiem tra mot buoc chuyen trang thai co hop le khong, nem ngoai le neu khong.
     *
     * @param from trang thai hien tai
     * @param to trang thai dich
     * @throws IllegalTicketTransitionException neu buoc chuyen khong hop le
     */
    public void assertCanTransition(TicketStatus from, TicketStatus to) {
        if (!allowedNextStates(from).contains(to)) {
            throw new IllegalTicketTransitionException(from, to);
        }
    }

    /**
     * Chuyen mot ve sang trang thai moi va luu lai.
     *
     * <p>Luong xu ly:
     * 1. Tim ve theo ticketId (nem ResourceNotFoundException neu khong tim thay)
     * 2. Kiem tra buoc chuyen co hop le khong (nem IllegalTicketTransitionException neu sai)
     * 3. Gan trang thai moi
     * 4. Cap nhat timestamp tuong ung voi trang thai dich
     * 5. Luu lai qua ticketRepository.save</p>
     *
     * @param ticketId id cua ve can chuyen
     * @param target trang thai dich
     * @return ve sau khi da doi trang thai
     */
    @Transactional
    public Ticket transition(Long ticketId, TicketStatus target) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket", ticketId));

        assertCanTransition(ticket.getStatus(), target);

        ticket.setStatus(target);

        // --- Cap nhat timestamp tuong ung (Section 4, 05-state-machine-giao-dich.md) ---
        Instant now = Instant.now();

        switch (target) {
            case LISTED:
                ticket.setListedAt(now);
                break;
            case TRANSFERRED:
                // Ghi thoi diem nguoi ban bao da chuyen ve
                ticket.setTransferredAt(now);
                break;
            case AWAITING_USE:
                // Tinh cua so khieu nai theo BR-09: min(transferredAt + disputeWindow, eventStartAt)
                ticket.setDisputeWindowClosesAt(computeDisputeWindowClose(ticket));
                break;
            case AUTO_COMPLETE:
            case RESOLVED_BUYER_WINS:
            case RESOLVED_SELLER_WINS:
            case CANCELLED:
                // Giao dich ket thuc (terminal state)
                ticket.setCompletedAt(now);
                break;
            default:
                // PAYMENT_ESCROWED, DEPOSIT_LOCKED, DISPUTE_UNDER_REVIEW:
                // khong co timestamp rieng can gan
                break;
        }

        return ticketRepository.save(ticket);
    }

    /**
     * Chon adapter phu hop voi loai ve.
     *
     * @param ticketType loai ve can tim adapter
     * @return adapter co {@code supportedType()} trung voi loai ve
     * @throws BusinessRuleViolationException neu khong tim thay adapter cho loai ve
     */
    public TicketAdapter resolveAdapter(TicketType ticketType) {
        TicketAdapter adapter = adapterMap.get(ticketType);
        if (adapter == null) {
            throw new BusinessRuleViolationException(
                    "Loai ve khong duoc ho tro: " + ticketType);
        }
        return adapter;
    }

    /**
     * Tinh thoi diem dong cua so khieu nai cho mot ve.
     *
     * <p>Theo BR-09: cua so khieu nai = transferredAt + disputeWindowAfterTransfer() cua adapter,
     * nhung luon phai dong truoc eventStartAt. Lay moc som hon trong hai gia tri.</p>
     *
     * @param ticket ve can tinh (phai da co transferredAt)
     * @return moc thoi gian nguoi mua het quyen mo tranh chap
     */
    public Instant computeDisputeWindowClose(Ticket ticket) {
        TicketAdapter adapter = resolveAdapter(ticket.getTicketType());
        Duration disputeWindow = adapter.disputeWindowAfterTransfer();

        Instant windowByTransfer = ticket.getTransferredAt().plus(disputeWindow);

        // BR-09: cua so khieu nai luon phai dong truoc khi su kien bat dau
        if (ticket.getEventStartAt() != null && ticket.getEventStartAt().isBefore(windowByTransfer)) {
            return ticket.getEventStartAt();
        }

        return windowByTransfer;
    }
}
