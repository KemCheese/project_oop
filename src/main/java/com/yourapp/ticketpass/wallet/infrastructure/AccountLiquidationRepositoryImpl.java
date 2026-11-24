package com.yourapp.ticketpass.wallet.infrastructure;

import com.yourapp.ticketpass.wallet.domain.AccountLiquidation;
import com.yourapp.ticketpass.wallet.domain.AccountLiquidationRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AccountLiquidationRepositoryImpl implements AccountLiquidationRepository {

    private final AccountLiquidationJpaRepository jpaRepository;

    @Override
    public AccountLiquidation save(AccountLiquidation liquidation) {
        return jpaRepository.save(liquidation);
    }

    @Override
    public Optional<AccountLiquidation> findByTriggerKey(String triggerKey) {
        return jpaRepository.findByTriggerKey(triggerKey);
    }

    @Override
    public boolean existsByTriggerKey(String triggerKey) {
        return jpaRepository.existsByTriggerKey(triggerKey);
    }

    @Override
    public List<AccountLiquidation> findByUserId(Long userId) {
        return jpaRepository.findByUserIdOrderByLiquidatedAtDesc(userId);
    }

    @Override
    public List<AccountLiquidation> findWithFeeBetween(Instant startInclusive, Instant endExclusive) {
        return jpaRepository.findWithFeeBetween(BigDecimal.ZERO, startInclusive, endExclusive);
    }
}
