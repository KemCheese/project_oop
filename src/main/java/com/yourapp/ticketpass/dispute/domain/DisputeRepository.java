package com.yourapp.ticketpass.dispute.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface DisputeRepository {

    Dispute save(Dispute dispute);

    Optional<Dispute> findById(Long id);

    Optional<Dispute> findByTicketId(Long ticketId);

    List<Dispute> findByBuyerId(Long buyerId);

    List<Dispute> findBySellerId(Long sellerId);

    List<Dispute> findByStatus(DisputeStatus status);

    List<Dispute> findOverdueSellerResponses(Instant before);

    boolean existsByTicketIdAndStatusIn(Long ticketId, List<DisputeStatus> statuses);
}
