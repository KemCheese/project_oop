package com.yourapp.ticketpass.payment.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface EscrowTransactionRepository {

    EscrowTransaction save(EscrowTransaction tx);

    Optional<EscrowTransaction> findById(Long id);

    Optional<EscrowTransaction> findByTicketId(Long ticketId);

    List<EscrowTransaction> findByBuyerId(Long buyerId);

    List<EscrowTransaction> findBySellerId(Long sellerId);

    List<EscrowTransaction> findByStatus(EscrowStatus status);

    Optional<EscrowTransaction> findByGatewayTransactionId(String gatewayTransactionId);

    List<EscrowTransaction> findReleasedBetween(Instant startInclusive, Instant endExclusive);

    BigDecimal sumHeldAmount();

    long countActivePendingByBuyerId(Long buyerId, Instant activeSince);
}
