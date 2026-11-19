package com.yourapp.ticketpass.ticket.infrastructure;

import com.yourapp.ticketpass.ticket.domain.Ticket;
import com.yourapp.ticketpass.ticket.domain.TicketStatus;
import com.yourapp.ticketpass.ticket.domain.TicketType;
import com.yourapp.ticketpass.ticket.domain.TicketTypeStatusCount;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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
     * @param status trang thai can quet (PAYMENT_ESCROWED)
     * @param before moc thoi gian so sanh voi escrowedAt (now - 30m)
     * @return danh sach ve qua han nap coc
     */
    List<Ticket> findByStatusAndEscrowedAtBefore(TicketStatus status, Instant before);

    /**
     * @param status trang thai can quet (TRANSFERRED)
     * @param before moc thoi gian so sanh voi transferredAt (now - 30m)
     * @return danh sach ve qua han xac nhan nhan ve
     */
    List<Ticket> findByStatusAndTransferredAtBefore(TicketStatus status, Instant before);

    /**
     * @param status trang thai can quet (LISTED)
     * @param before moc thoi gian so sanh voi eventStartAt
     * @return danh sach ve can tu dong go do qua han su kien
     */
    List<Ticket> findByStatusAndEventStartAtBefore(TicketStatus status, Instant before);

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
     * Tim kiem ve dang rao ban theo trang thai, loai ve, tu khoa va phan trang.
     *
     * @param status trang thai can loc (thuong la LISTED)
     * @param ticketType loai ve can loc (neu null thi bo qua loc theo loai)
     * @param keyword tu khoa tim kiem
     * @param pageable thong tin phan trang
     * @return trang ve thoa man dieu kien
     */
    @Query(value = "SELECT t FROM Ticket t WHERE t.status = :status AND " +
           "(:ticketType IS NULL OR t.ticketType = :ticketType) AND " +
           "(:keyword IS NULL OR :keyword = '' OR LOWER(t.eventName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(t.title) LIKE LOWER(CONCAT('%', :keyword, '%')))",
           countQuery = "SELECT COUNT(t) FROM Ticket t WHERE t.status = :status AND " +
           "(:ticketType IS NULL OR t.ticketType = :ticketType) AND " +
           "(:keyword IS NULL OR :keyword = '' OR LOWER(t.eventName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(t.title) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<Ticket> searchListedTickets(@Param("status") TicketStatus status,
                                     @Param("ticketType") TicketType ticketType,
                                     @Param("keyword") String keyword,
                                     Pageable pageable);

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

    /**
     * Tim cac ve ma nguoi mua dang so huu hieu luc trong khoang thoi gian co kha nang trung lich.
     * Giu lai ca ve cu co {@code eventEndAt IS NULL} neu {@code eventStartAt < targetEnd}.
     */
    @Query("SELECT t FROM Ticket t WHERE t.buyerId = :buyerId AND t.status IN :statuses " +
           "AND (:excludeTicketId IS NULL OR t.id <> :excludeTicketId) " +
           "AND t.eventStartAt IS NOT NULL AND t.eventStartAt < :targetEnd " +
           "AND (t.eventEndAt IS NULL OR t.eventEndAt > :targetStart) " +
           "ORDER BY t.eventStartAt ASC, t.id ASC")
    List<Ticket> findPotentialScheduleConflicts(@Param("buyerId") Long buyerId,
                                                @Param("statuses") Collection<TicketStatus> statuses,
                                                @Param("targetEnd") Instant targetEnd,
                                                @Param("targetStart") Instant targetStart,
                                                @Param("excludeTicketId") Long excludeTicketId);

    /**
     * Lay theo batch cac ve can gui nhac lich su dung trong cua so (now, windowEnd].
     */
    @Query("SELECT t FROM Ticket t WHERE t.status IN :statuses " +
           "AND t.reminded24hAt IS NULL " +
           "AND t.buyerId IS NOT NULL " +
           "AND t.eventStartAt IS NOT NULL " +
           "AND t.eventStartAt > :now AND t.eventStartAt <= :windowEnd " +
           "ORDER BY t.eventStartAt ASC, t.id ASC")
    List<Ticket> findTicketsDueFor24hReminder(@Param("statuses") Collection<TicketStatus> statuses,
                                              @Param("now") Instant now,
                                              @Param("windowEnd") Instant windowEnd,
                                              Pageable pageable);

    /**
     * Danh dau nguyen tu mot ve da duoc nhac lich 24h (idempotent).
     * flushAutomatically + clearAutomatically giup dong bo Hibernate L1 cache khi goi trong transaction cha.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Ticket t SET t.reminded24hAt = :remindedAt " +
           "WHERE t.id = :ticketId AND t.reminded24hAt IS NULL")
    int markReminded24hIfUnset(@Param("ticketId") Long ticketId,
                               @Param("remindedAt") Instant remindedAt);

    /**
     * Tai hang loat ve theo tap ID phuc vu bao cao Admin.
     */
    List<Ticket> findByIdIn(Collection<Long> ids);

    /**
     * Thong ke so ve ket thuc theo loai ve va trang thai trong ky [startInclusive, endExclusive).
     */
    @Query("SELECT new com.yourapp.ticketpass.ticket.domain.TicketTypeStatusCount(t.ticketType, t.status, COUNT(t)) " +
           "FROM Ticket t " +
           "WHERE t.completedAt IS NOT NULL AND t.completedAt >= :startInclusive AND t.completedAt < :endExclusive " +
           "GROUP BY t.ticketType, t.status")
    List<TicketTypeStatusCount> countCompletedGroupedByTypeAndStatus(@Param("startInclusive") Instant startInclusive,
                                                                     @Param("endExclusive") Instant endExclusive);

    /**
     * Thong ke snapshot tuc thoi (live) toan bo ve theo loai ve va trang thai.
     */
    @Query("SELECT new com.yourapp.ticketpass.ticket.domain.TicketTypeStatusCount(t.ticketType, t.status, COUNT(t)) " +
           "FROM Ticket t " +
           "GROUP BY t.ticketType, t.status")
    List<TicketTypeStatusCount> countLiveGroupedByTypeAndStatus();
}
