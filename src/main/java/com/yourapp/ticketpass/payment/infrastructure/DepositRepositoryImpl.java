package com.yourapp.ticketpass.payment.infrastructure;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.yourapp.ticketpass.payment.domain.Deposit;
import com.yourapp.ticketpass.payment.domain.DepositRepository;
import com.yourapp.ticketpass.payment.domain.DepositStatus;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class DepositRepositoryImpl implements DepositRepository {

    private final DepositJpaRepository jpaRepository;

    @Override
    public Deposit save(Deposit deposit) {
        return jpaRepository.save(deposit);
    }

    @Override
    public Optional<Deposit> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public Optional<Deposit> findByTicketId(Long ticketId) {
        return jpaRepository.findByTicketId(ticketId);
    }

    @Override
    public List<Deposit> findBySellerId(Long sellerId) {
        return jpaRepository.findBySellerId(sellerId);
    }

    @Override
    public List<Deposit> findByStatus(DepositStatus status) {
        return jpaRepository.findByStatus(status);
    }

    @Override
    public List<Deposit> findForfeitedBetween(Instant startInclusive, Instant endExclusive) {
        if (startInclusive == null || endExclusive == null) {
            return List.of();
        }
        return jpaRepository.findForfeitedBetween(startInclusive, endExclusive);
    }
}
