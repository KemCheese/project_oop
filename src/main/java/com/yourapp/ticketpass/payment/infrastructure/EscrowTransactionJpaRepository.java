package com.yourapp.ticketpass.payment.infrastructure;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.yourapp.ticketpass.payment.domain.EscrowStatus;
import com.yourapp.ticketpass.payment.domain.EscrowTransaction;

public interface EscrowTransactionJpaRepository extends JpaRepository<EscrowTransaction, Long> {

    Optional<EscrowTransaction> findFirstByTicketIdOrderByIdDesc(Long ticketId);

    List<EscrowTransaction> findByBuyerId(Long buyerId);

    List<EscrowTransaction> findBySellerId(Long sellerId);

    List<EscrowTransaction> findByStatus(EscrowStatus status);

    Optional<EscrowTransaction> findByGatewayTransactionId(String gatewayTransactionId);

    @Query("SELECT e FROM EscrowTransaction e " +
           "WHERE e.status = com.yourapp.ticketpass.payment.domain.EscrowStatus.RELEASED " +
           "AND e.releasedAt >= :startInclusive AND e.releasedAt < :endExclusive " +
           "ORDER BY e.releasedAt DESC, e.id DESC")
    List<EscrowTransaction> findReleasedBetween(@Param("startInclusive") Instant startInclusive,
                                                @Param("endExclusive") Instant endExclusive);

    @Query("SELECT COALESCE(SUM(e.amountHeld), 0.00) FROM EscrowTransaction e " +
           "WHERE e.status = com.yourapp.ticketpass.payment.domain.EscrowStatus.HELD")
    BigDecimal sumHeldAmount();
}
