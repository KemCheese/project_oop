package com.yourapp.ticketpass.wallet.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface AccountLiquidationRepository {

    AccountLiquidation save(AccountLiquidation liquidation);

    Optional<AccountLiquidation> findByTriggerKey(String triggerKey);

    boolean existsByTriggerKey(String triggerKey);

    List<AccountLiquidation> findByUserId(Long userId);

    List<AccountLiquidation> findWithFeeBetween(Instant startInclusive, Instant endExclusive);
}
