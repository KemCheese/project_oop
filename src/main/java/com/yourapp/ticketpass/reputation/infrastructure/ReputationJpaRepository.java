package com.yourapp.ticketpass.reputation.infrastructure;

import com.yourapp.ticketpass.reputation.domain.UserReputationScore;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository Spring Data JPA cho bảng {@code user_reputation_scores}.
 *
 * <p>Chi tiết hạ tầng, chỉ được {@link ReputationRepositoryImpl} sử dụng. Tầng application
 * đi qua port {@code com.yourapp.ticketpass.reputation.domain.ReputationRepository}.</p>
 *
 * <p>Module: reputation</p>
 *
 * Chủ sở hữu: Người C
 */
public interface ReputationJpaRepository extends JpaRepository<UserReputationScore, Long> {

    /**
     * Tìm bản ghi điểm theo id người dùng.
     *
     * @param userId id thuần của người dùng
     * @return bản ghi điểm nếu tồn tại
     */
    Optional<UserReputationScore> findByUserId(Long userId);

    /**
     * Lấy các bản ghi điểm sắp xếp giảm dần theo điểm, có phân trang.
     *
     * @param pageable thông tin phân trang, dùng để giới hạn số dòng trả về
     * @return danh sách bản ghi của trang tương ứng
     */
    List<UserReputationScore> findAllByOrderByScoreDesc(Pageable pageable);
}
