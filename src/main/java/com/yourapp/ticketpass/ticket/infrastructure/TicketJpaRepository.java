package com.yourapp.ticketpass.ticket.infrastructure;

import com.yourapp.ticketpass.ticket.domain.Ticket;
import com.yourapp.ticketpass.ticket.domain.TicketStatus;
import com.yourapp.ticketpass.ticket.domain.TicketType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository Spring Data JPA cho entity {@code Ticket}.
 *
 * <p>Interface nay la chi tiet ky thuat cua tang {@code infrastructure}; tang domain va application
 * khong duoc phu thuoc truc tiep vao no ma di qua port
 * {@code com.yourapp.ticketpass.ticket.domain.TicketRepository}.</p>
 *
 * <p>Module: ticket</p>
 *
 * Chu so huu: Nguoi A
 */
public interface TicketJpaRepository extends JpaRepository<Ticket, Long> {

    /**
     * @param sellerId id nguoi ban
     * @return cac ve do nguoi ban nay dang len
     */
    List<Ticket> findBySellerId(Long sellerId);

    /**
     * @param buyerId id nguoi mua
     * @return cac ve nguoi mua nay da mua
     */
    List<Ticket> findByBuyerId(Long buyerId);

    /**
     * @param status trang thai can loc
     * @return cac ve dang o trang thai do
     */
    List<Ticket> findByStatus(TicketStatus status);

    /**
     * @param ticketType loai ve can loc
     * @param status trang thai can loc
     * @return cac ve khop ca loai lan trang thai
     */
    List<Ticket> findByTicketTypeAndStatus(TicketType ticketType, TicketStatus status);

    /**
     * @param status trang thai can quet
     * @param before moc thoi gian so sanh voi disputeWindowClosesAt
     * @return cac ve da het cua so khieu nai va den han tu dong hoan tat
     */
    List<Ticket> findByStatusAndDisputeWindowClosesAtBefore(TicketStatus status, Instant before);
}
