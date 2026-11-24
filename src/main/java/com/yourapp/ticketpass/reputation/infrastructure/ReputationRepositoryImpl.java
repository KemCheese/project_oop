package com.yourapp.ticketpass.reputation.infrastructure;

import com.yourapp.ticketpass.reputation.domain.ReputationRepository;
import com.yourapp.ticketpass.reputation.domain.UserReputationScore;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ReputationRepositoryImpl implements ReputationRepository {

    private final ReputationJpaRepository reputationJpaRepository;

    @Override
    public UserReputationScore save(UserReputationScore score) {
        return reputationJpaRepository.saveAndFlush(score);
    }

    @Override
    public Optional<UserReputationScore> findByUserId(Long userId) {
        return reputationJpaRepository.findByUserId(userId);
    }

    @Override
    public Optional<UserReputationScore> findById(Long id) {
        return reputationJpaRepository.findById(id);
    }

    @Override
    public List<UserReputationScore> findTopSellers(int limit) {
        return reputationJpaRepository.findAllByOrderByScoreDescSuccessfulTransactionsDesc(PageRequest.of(0, limit));
    }

    @Override
    public List<UserReputationScore> findByUserIdIn(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        return reputationJpaRepository.findByUserIdIn(userIds);
    }
}
