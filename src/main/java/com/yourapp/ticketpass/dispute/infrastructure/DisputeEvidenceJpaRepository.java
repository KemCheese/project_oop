package com.yourapp.ticketpass.dispute.infrastructure;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.yourapp.ticketpass.dispute.domain.DisputeEvidence;

public interface DisputeEvidenceJpaRepository extends JpaRepository<DisputeEvidence, Long> {

    List<DisputeEvidence> findByDisputeId(Long disputeId);
}
