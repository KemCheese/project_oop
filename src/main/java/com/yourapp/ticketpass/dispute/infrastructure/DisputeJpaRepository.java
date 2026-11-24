package com.yourapp.ticketpass.dispute.infrastructure;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.yourapp.ticketpass.dispute.domain.Dispute;
import com.yourapp.ticketpass.dispute.domain.DisputeStatus;

public interface DisputeJpaRepository extends JpaRepository<Dispute, Long> {

    Optional<Dispute> findByTicketId(Long ticketId);

    List<Dispute> findByBuyerId(Long buyerId);

    List<Dispute> findBySellerId(Long sellerId);

    List<Dispute> findByStatus(DisputeStatus status);

    List<Dispute> findByStatusAndSellerResponseDeadlineBefore(DisputeStatus status, Instant sellerResponseDeadline);

    boolean existsByTicketIdAndStatusIn(Long ticketId, List<DisputeStatus> statuses);
}
