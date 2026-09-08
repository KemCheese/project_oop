package com.yourapp.ticketpass.ticket.application;

import com.yourapp.ticketpass.ticket.domain.Ticket;
import com.yourapp.ticketpass.ticket.domain.TicketAdapter;
import com.yourapp.ticketpass.ticket.domain.TicketRepository;
import com.yourapp.ticketpass.ticket.domain.TicketStatus;
import com.yourapp.ticketpass.ticket.domain.TicketType;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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

    private final TicketRepository ticketRepository;

    private final List<TicketAdapter> ticketAdapters;

    /**
     * Liet ke cac trang thai ke tiep hop le tu mot trang thai hien tai.
     *
     * @param current trang thai hien tai cua ve
     * @return tap trang thai co the chuyen toi
     */
    public Set<TicketStatus> allowedNextStates(TicketStatus current) {
        throw new UnsupportedOperationException(
                "TODO [Nguoi A] Prompt 2: Khai bao bang chuyen trang thai theo dung so do trong TicketStatus "
                        + "(LISTED -> PAYMENT_ESCROWED -> DEPOSIT_LOCKED -> TRANSFERRED -> AWAITING_USE -> "
                        + "AUTO_COMPLETE hoac DISPUTE_UNDER_REVIEW -> RESOLVED_BUYER_WINS/RESOLVED_SELLER_WINS) "
                        + "va tra ve tap trang thai ke tiep cua trang thai dua vao; cac trang thai ket thuc tra ve tap rong.");
    }

    /**
     * Kiem tra mot buoc chuyen trang thai co hop le khong, nem ngoai le neu khong.
     *
     * @param from trang thai hien tai
     * @param to trang thai dich
     * @throws com.yourapp.ticketpass.ticket.domain.IllegalTicketTransitionException neu buoc chuyen khong hop le
     */
    public void assertCanTransition(TicketStatus from, TicketStatus to) {
        throw new UnsupportedOperationException(
                "TODO [Nguoi A] Prompt 2: Doi chieu 'to' voi allowedNextStates(from); neu khong nam trong tap hop "
                        + "thi nem IllegalTicketTransitionException(from, to).");
    }

    /**
     * Chuyen mot ve sang trang thai moi va luu lai.
     *
     * @param ticketId id cua ve can chuyen
     * @param target trang thai dich
     * @return ve sau khi da doi trang thai
     */
    public Ticket transition(Long ticketId, TicketStatus target) {
        throw new UnsupportedOperationException(
                "TODO [Nguoi A] Prompt 2: Nap ve qua ticketRepository.findById (khong thay thi nem "
                        + "ResourceNotFoundException), goi assertCanTransition, gan status moi, cap nhat cac moc "
                        + "thoi gian tuong ung (transferredAt khi sang TRANSFERRED, disputeWindowClosesAt khi sang "
                        + "AWAITING_USE, completedAt khi sang AUTO_COMPLETE hoac cac trang thai RESOLVED_*), "
                        + "roi luu lai bang ticketRepository.save.");
    }

    /**
     * Chon adapter phu hop voi loai ve.
     *
     * @param ticketType loai ve can tim adapter
     * @return adapter co {@code supportedType()} trung voi loai ve
     */
    public TicketAdapter resolveAdapter(TicketType ticketType) {
        throw new UnsupportedOperationException(
                "TODO [Nguoi A] Prompt 2: Duyet ticketAdapters, tim adapter co supportedType() bang ticketType; "
                        + "khong tim thay thi nem BusinessRuleViolationException bao chua ho tro loai ve nay. "
                        + "Can nhac cache lai thanh Map<TicketType, TicketAdapter> trong @PostConstruct.");
    }

    /**
     * Tinh thoi diem dong cua so khieu nai cho mot ve.
     *
     * @param ticket ve can tinh
     * @return moc thoi gian nguoi mua het quyen mo tranh chap
     */
    public Instant computeDisputeWindowClose(Ticket ticket) {
        throw new UnsupportedOperationException(
                "TODO [Nguoi A] Prompt 2: Lay adapter theo loai ve, cong transferredAt voi "
                        + "disputeWindowAfterTransfer(), roi lay moc som hon giua ket qua do va ticket.eventStartAt "
                        + "vi cua so khieu nai luon phai dong truoc khi su kien bat dau.");
    }
}
