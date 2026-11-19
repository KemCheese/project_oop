package com.yourapp.ticketpass.reputation.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Port lưu trữ của module {@code reputation}, khai báo tại tầng domain.
 *
 * <p>Tầng application chỉ phụ thuộc interface này, không phụ thuộc Spring Data JPA. Hiện
 * thực nằm ở {@code com.yourapp.ticketpass.reputation.infrastructure.ReputationRepositoryImpl}.</p>
 *
 * <p>Module: reputation</p>
 *
 * Chủ sở hữu: Người C
 */
public interface ReputationRepository {

    /**
     * Lưu mới hoặc cập nhật bản ghi điểm uy tín.
     *
     * @param score bản ghi cần lưu
     * @return bản ghi sau khi lưu, đã có id và version mới nhất
     */
    UserReputationScore save(UserReputationScore score);

    /**
     * Tìm bản ghi điểm theo id người dùng.
     *
     * @param userId id thuần của người dùng
     * @return bản ghi điểm nếu đã tồn tại
     */
    Optional<UserReputationScore> findByUserId(Long userId);

    /**
     * Tìm bản ghi điểm theo khoá chính.
     *
     * @param id id bản ghi
     * @return bản ghi nếu tồn tại
     */
    Optional<UserReputationScore> findById(Long id);

    /**
     * Lấy danh sách người bán có điểm uy tín cao nhất, phục vụ bảng xếp hạng.
     *
     * @param limit số lượng bản ghi tối đa cần lấy
     * @return danh sách bản ghi điểm sắp xếp giảm dần theo điểm
     */
    List<UserReputationScore> findTopSellers(int limit);

    /**
     * Tải hàng loạt bản ghi điểm uy tín theo tập {@code userIds} để tránh N+1 query.
     *
     * @param userIds tập ID người dùng cần tra cứu
     * @return danh sách bản ghi điểm tương ứng (rỗng nếu đầu vào rỗng)
     */
    List<UserReputationScore> findByUserIdIn(Collection<Long> userIds);
}
