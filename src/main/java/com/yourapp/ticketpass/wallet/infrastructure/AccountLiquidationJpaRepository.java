package com.yourapp.ticketpass.wallet.infrastructure;

import com.yourapp.ticketpass.wallet.domain.AccountLiquidation;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountLiquidationJpaRepository extends JpaRepository<AccountLiquidation, Long> {

    Optional<AccountLiquidation> findByTriggerKey(String triggerKey);

    boolean existsByTriggerKey(String triggerKey);

    List<AccountLiquidation> findByUserIdOrderByLiquidatedAtDesc(Long userId);

    @Query("""
            SELECT l FROM AccountLiquidation l
            WHERE l.feeAmount > :zero
              AND l.liquidatedAt >= :startInclusive
              AND l.liquidatedAt < :endExclusive
            ORDER BY l.liquidatedAt DESC, l.id DESC
            """)
    List<AccountLiquidation> findWithFeeBetween(
            @Param("zero") BigDecimal zero,
            @Param("startInclusive") Instant startInclusive,
            @Param("endExclusive") Instant endExclusive);
}
