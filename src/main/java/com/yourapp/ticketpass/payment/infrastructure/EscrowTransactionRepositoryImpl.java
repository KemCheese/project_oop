package com.yourapp.ticketpass.payment.infrastructure;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.yourapp.ticketpass.payment.domain.EscrowStatus;
import com.yourapp.ticketpass.payment.domain.EscrowTransaction;
import com.yourapp.ticketpass.payment.domain.EscrowTransactionRepository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class EscrowTransactionRepositoryImpl implements EscrowTransactionRepository {

    private final EscrowTransactionJpaRepository jpaRepository;

    @Override
    public EscrowTransaction save(EscrowTransaction tx) {
        return jpaRepository.save(tx);
    }

    @Override
    public Optional<EscrowTransaction> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public Optional<EscrowTransaction> findByTicketId(Long ticketId) {
        return jpaRepository.findFirstByTicketIdOrderByIdDesc(ticketId);
    }

    @Override
    public List<EscrowTransaction> findByBuyerId(Long buyerId) {
        return jpaRepository.findByBuyerId(buyerId);
    }

    @Override
    public List<EscrowTransaction> findBySellerId(Long sellerId) {
        return jpaRepository.findBySellerId(sellerId);
    }

    @Override
    public List<EscrowTransaction> findByStatus(EscrowStatus status) {
        return jpaRepository.findByStatus(status);
    }

    @Override
    public Optional<EscrowTransaction> findByGatewayTransactionId(String gatewayTransactionId) {
        return jpaRepository.findByGatewayTransactionId(gatewayTransactionId);
    }

    @Override
    public List<EscrowTransaction> findReleasedBetween(Instant startInclusive, Instant endExclusive) {
        if (startInclusive == null || endExclusive == null) {
            return List.of();
        }
        return jpaRepository.findReleasedBetween(startInclusive, endExclusive);
    }

    @Override
    public BigDecimal sumHeldAmount() {
        BigDecimal total = jpaRepository.sumHeldAmount();
        return total != null ? total : BigDecimal.ZERO;
    }

    @Override
    public long countActivePendingByBuyerId(Long buyerId, Instant activeSince) {
        if (buyerId == null || activeSince == null) {
            return 0L;
        }
        return jpaRepository.countActivePendingByBuyerId(buyerId, activeSince);
    }
}
