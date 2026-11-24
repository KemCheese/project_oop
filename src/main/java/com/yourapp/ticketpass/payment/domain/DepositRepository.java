package com.yourapp.ticketpass.payment.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface DepositRepository {

    Deposit save(Deposit deposit);

    Optional<Deposit> findById(Long id);

    Optional<Deposit> findByTicketId(Long ticketId);

    List<Deposit> findBySellerId(Long sellerId);

    List<Deposit> findByStatus(DepositStatus status);

    List<Deposit> findForfeitedBetween(Instant startInclusive, Instant endExclusive);
}
