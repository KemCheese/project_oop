package com.yourapp.ticketpass.dispute.infrastructure;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.yourapp.ticketpass.dispute.domain.Dispute;
import com.yourapp.ticketpass.dispute.domain.DisputeRepository;
import com.yourapp.ticketpass.dispute.domain.DisputeStatus;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class DisputeRepositoryImpl implements DisputeRepository {

    private final DisputeJpaRepository jpaRepository;

    @Override
    public Dispute save(Dispute dispute) {
        return jpaRepository.save(dispute);
    }

    @Override
    public Optional<Dispute> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public Optional<Dispute> findByTicketId(Long ticketId) {
        return jpaRepository.findByTicketId(ticketId);
    }

    @Override
    public List<Dispute> findByBuyerId(Long buyerId) {
        return jpaRepository.findByBuyerId(buyerId);
    }

    @Override
    public List<Dispute> findBySellerId(Long sellerId) {
        return jpaRepository.findBySellerId(sellerId);
    }

    @Override
    public List<Dispute> findByStatus(DisputeStatus status) {
        return jpaRepository.findByStatus(status);
    }

    @Override
    public List<Dispute> findOverdueSellerResponses(Instant before) {
        return jpaRepository.findByStatusAndSellerResponseDeadlineBefore(DisputeStatus.AWAITING_SELLER_RESPONSE, before);
    }

    @Override
    public boolean existsByTicketIdAndStatusIn(Long ticketId, List<DisputeStatus> statuses) {
        return jpaRepository.existsByTicketIdAndStatusIn(ticketId, statuses);
    }
}
