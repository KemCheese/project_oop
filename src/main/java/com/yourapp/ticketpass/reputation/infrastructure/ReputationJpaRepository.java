package com.yourapp.ticketpass.reputation.infrastructure;

import com.yourapp.ticketpass.reputation.domain.UserReputationScore;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReputationJpaRepository extends JpaRepository<UserReputationScore, Long> {

    Optional<UserReputationScore> findByUserId(Long userId);

    List<UserReputationScore> findAllByOrderByScoreDescSuccessfulTransactionsDesc(Pageable pageable);

    List<UserReputationScore> findByUserIdIn(Collection<Long> userIds);
}
