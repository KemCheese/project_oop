package com.yourapp.ticketpass.ticket.infrastructure;

import com.yourapp.ticketpass.ticket.domain.Ticket;
import com.yourapp.ticketpass.ticket.domain.TicketStatus;
import com.yourapp.ticketpass.ticket.domain.TicketType;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /**
     * Tim kiem ve dang rao ban theo trang thai, loai ve va tu khoa (ten su kien hoac tieu de).
     *
     * @param status trang thai can loc (thuong la LISTED)
     * @param ticketType loai ve can loc (neu null thi bo qua loc theo loai)
     * @param keyword tu khoa tim kiem (neu null hoac rong thi bo qua loc theo tu khoa)
     * @return danh sach ve thoa man dieu kien
     */
    @Query("SELECT t FROM Ticket t WHERE t.status = :status AND " +
           "(:ticketType IS NULL OR t.ticketType = :ticketType) AND " +
           "(:keyword IS NULL OR :keyword = '' OR LOWER(t.eventName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(t.title) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    List<Ticket> searchListedTickets(@Param("status") TicketStatus status,
                                     @Param("ticketType") TicketType ticketType,
                                     @Param("keyword") String keyword);

    /**
     * Tim kiem danh sach ve dang rao ban theo tu khoa, sap xep theo thoi gian tao moi nhat va phan trang (dung cho autocomplete).
     *
     * @param status trang thai can loc (thuong la LISTED)
     * @param keyword tu khoa tim kiem
     * @param pageable thong tin phan trang va gioi han (vi du PageRequest.of(0, 5))
     * @return danh sach ve thoa man dieu kien
     */
    @Query("SELECT t FROM Ticket t WHERE t.status = :status AND " +
           "(:keyword IS NULL OR :keyword = '' OR LOWER(t.eventName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(t.title) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "ORDER BY t.createdAt DESC, t.id DESC")
    List<Ticket> findTopSuggestions(@Param("status") TicketStatus status,
                                    @Param("keyword") String keyword,
                                    Pageable pageable);
}
