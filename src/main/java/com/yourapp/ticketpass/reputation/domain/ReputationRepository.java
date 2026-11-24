package com.yourapp.ticketpass.reputation.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ReputationRepository {

    UserReputationScore save(UserReputationScore score);

    Optional<UserReputationScore> findByUserId(Long userId);

    Optional<UserReputationScore> findById(Long id);

    List<UserReputationScore> findTopSellers(int limit);

    List<UserReputationScore> findByUserIdIn(Collection<Long> userIds);
}
