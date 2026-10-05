package com.yourapp.ticketpass.reputation.infrastructure;

import com.yourapp.ticketpass.reputation.domain.ReputationRepository;
import com.yourapp.ticketpass.reputation.domain.UserReputationScore;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

/**
 * Hiện thực port {@link ReputationRepository} bằng Spring Data JPA.
 *
 * <p>Adapter chỉ chuyển tiếp lời gọi sang {@link ReputationJpaRepository}, không chứa
 * business logic. Riêng {@link #findTopSellers(int)} quy đổi tham số {@code limit} sang
 * {@code PageRequest} vì Spring Data giới hạn số dòng qua {@code Pageable}.</p>
 *
 * <p>Module: reputation</p>
 *
 * Chủ sở hữu: Người C
 */
@Repository
@RequiredArgsConstructor
public class ReputationRepositoryImpl implements ReputationRepository {

    private final ReputationJpaRepository reputationJpaRepository;

    @Override
    public UserReputationScore save(UserReputationScore score) {
        return reputationJpaRepository.save(score);
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
}
