package com.yourapp.ticketpass.dispute.domain;

import java.util.List;
import java.util.Optional;

public interface DisputeEvidenceRepository {

    DisputeEvidence save(DisputeEvidence evidence);

    List<DisputeEvidence> findByDisputeId(Long disputeId);

    Optional<DisputeEvidence> findById(Long id);

    void deleteById(Long id);
}
