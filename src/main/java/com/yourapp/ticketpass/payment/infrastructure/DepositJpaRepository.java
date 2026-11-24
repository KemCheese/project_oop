package com.yourapp.ticketpass.payment.infrastructure;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.yourapp.ticketpass.payment.domain.Deposit;
import com.yourapp.ticketpass.payment.domain.DepositStatus;

public interface DepositJpaRepository extends JpaRepository<Deposit, Long> {

    Optional<Deposit> findByTicketId(Long ticketId);

    List<Deposit> findBySellerId(Long sellerId);

    List<Deposit> findByStatus(DepositStatus status);

    @Query("SELECT d FROM Deposit d " +
           "WHERE d.status = com.yourapp.ticketpass.payment.domain.DepositStatus.FORFEITED " +
           "AND d.forfeitedAt >= :startInclusive AND d.forfeitedAt < :endExclusive " +
           "ORDER BY d.forfeitedAt DESC, d.id DESC")
    List<Deposit> findForfeitedBetween(@Param("startInclusive") Instant startInclusive,
                                       @Param("endExclusive") Instant endExclusive);
}
