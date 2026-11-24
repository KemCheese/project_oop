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

public interface TicketJpaRepository extends JpaRepository<Ticket, Long> {

    List<Ticket> findBySellerId(Long sellerId);

    List<Ticket> findByBuyerId(Long buyerId);

    List<Ticket> findByStatus(TicketStatus status);

    long countByStatus(TicketStatus status);

    List<Ticket> findByTicketTypeAndStatus(TicketType ticketType, TicketStatus status);

    List<Ticket> findByStatusAndDisputeWindowClosesAtBefore(TicketStatus status, Instant before);

    List<Ticket> findByStatusAndEscrowedAtBefore(TicketStatus status, Instant before);

    List<Ticket> findByStatusAndTransferredAtBefore(TicketStatus status, Instant before);

    List<Ticket> findByStatusAndEventStartAtBefore(TicketStatus status, Instant before);

    @Query("SELECT t FROM Ticket t WHERE t.status = :status AND " +
           "(:ticketType IS NULL OR t.ticketType = :ticketType) AND " +
           "(:keyword IS NULL OR :keyword = '' OR LOWER(t.eventName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(t.title) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    List<Ticket> searchListedTickets(@Param("status") TicketStatus status,
                                     @Param("ticketType") TicketType ticketType,
                                     @Param("keyword") String keyword);

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

    @Query("SELECT t FROM Ticket t WHERE t.status = :status AND " +
           "(:keyword IS NULL OR :keyword = '' OR LOWER(t.eventName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(t.title) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "ORDER BY t.createdAt DESC, t.id DESC")
    List<Ticket> findTopSuggestions(@Param("status") TicketStatus status,
                                    @Param("keyword") String keyword,
                                    Pageable pageable);

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

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Ticket t SET t.reminded24hAt = :remindedAt " +
           "WHERE t.id = :ticketId AND t.reminded24hAt IS NULL")
    int markReminded24hIfUnset(@Param("ticketId") Long ticketId,
                               @Param("remindedAt") Instant remindedAt);

    List<Ticket> findByIdIn(Collection<Long> ids);

    @Query("SELECT new com.yourapp.ticketpass.ticket.domain.TicketTypeStatusCount(t.ticketType, t.status, COUNT(t)) " +
           "FROM Ticket t " +
           "WHERE t.completedAt IS NOT NULL AND t.completedAt >= :startInclusive AND t.completedAt < :endExclusive " +
           "GROUP BY t.ticketType, t.status")
    List<TicketTypeStatusCount> countCompletedGroupedByTypeAndStatus(@Param("startInclusive") Instant startInclusive,
                                                                     @Param("endExclusive") Instant endExclusive);

    @Query("SELECT new com.yourapp.ticketpass.ticket.domain.TicketTypeStatusCount(t.ticketType, t.status, COUNT(t)) " +
           "FROM Ticket t " +
           "GROUP BY t.ticketType, t.status")
    List<TicketTypeStatusCount> countLiveGroupedByTypeAndStatus();
}
