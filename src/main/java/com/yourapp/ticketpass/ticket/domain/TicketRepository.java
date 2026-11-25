package com.yourapp.ticketpass.ticket.domain;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface TicketRepository {

    Ticket save(Ticket ticket);

    Optional<Ticket> findById(Long id);

    Optional<Ticket> findByIdWithLock(Long id);

    boolean existsActiveByProviderOrderCode(String providerOrderCode, Collection<TicketStatus> statuses);

    boolean existsActiveByEventStartVenueAndSeat(Instant eventStartAt,
                                                 String venue,
                                                 String seatInfo,
                                                 Collection<TicketStatus> statuses);

    List<Ticket> findBySellerId(Long sellerId);

    List<Ticket> findByBuyerId(Long buyerId);

    List<Ticket> findByStatus(TicketStatus status);

    long countByStatus(TicketStatus status);

    List<Ticket> findListedByType(TicketType ticketType);

    List<Ticket> searchListed(String keyword, TicketType ticketType);

    Page<Ticket> searchListed(String keyword, TicketType ticketType, Pageable pageable);

    List<Ticket> findTopSuggestions(String keyword, int limit);

    List<Ticket> findDueForAutoComplete(TicketStatus status, Instant before);

    List<Ticket> findOverdueDepositTickets(Instant before);

    List<Ticket> findExpiredListedTickets(Instant now);

    List<Ticket> findPotentialScheduleConflicts(Long buyerId,
                                                Collection<TicketStatus> statuses,
                                                Instant targetEnd,
                                                Instant targetStart,
                                                Long excludeTicketId);

    List<Ticket> findTicketsDueFor24hReminder(Collection<TicketStatus> statuses,
                                              Instant now,
                                              Instant windowEnd,
                                              Pageable pageable);

    int markReminded24hIfUnset(Long ticketId, Instant remindedAt);

    List<Ticket> findByIdIn(Collection<Long> ids);

    List<TicketTypeStatusCount> countCompletedGroupedByTypeAndStatus(Instant startInclusive, Instant endExclusive);

    List<TicketTypeStatusCount> countLiveGroupedByTypeAndStatus();

    void deleteById(Long id);
}
