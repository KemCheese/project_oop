package com.yourapp.ticketpass.user.infrastructure;

import com.yourapp.ticketpass.user.domain.User;
import com.yourapp.ticketpass.user.domain.UserRole;
import com.yourapp.ticketpass.user.domain.UserStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repository Spring Data JPA cho bảng {@code users}.
 *
 * <p>Đây là chi tiết hạ tầng, chỉ được {@link UserRepositoryImpl} sử dụng. Tầng domain và
 * application không bao giờ inject trực tiếp interface này mà đi qua port
 * {@code com.yourapp.ticketpass.user.domain.UserRepository}.</p>
 *
 * <p>Module: user</p>
 *
 * Chủ sở hữu: Người C
 */
public interface UserJpaRepository extends JpaRepository<User, Long> {

    /**
     * Tìm tài khoản theo email.
     *
     * @param email email đăng nhập
     * @return tài khoản nếu tồn tại
     */
    Optional<User> findByEmail(String email);

    /**
     * Kiểm tra email đã tồn tại hay chưa.
     *
     * @param email email cần kiểm tra
     * @return {@code true} nếu đã có tài khoản dùng email này
     */
    boolean existsByEmail(String email);

    /**
     * Lấy tài khoản theo trạng thái.
     *
     * @param status trạng thái cần lọc
     * @return danh sách tài khoản
     */
    List<User> findByStatus(UserStatus status);

    /**
     * Lấy tài khoản theo vai trò.
     *
     * @param role vai trò cần lọc
     * @return danh sách tài khoản
     */
    List<User> findByRole(UserRole role);

    /**
     * Tìm kiếm tài khoản trên toàn bộ hệ thống khi Admin không lọc theo trạng thái.
     * Tách riêng khỏi query có {@code status} để MySQL sử dụng đúng index sắp xếp thay vì OR-null scan.
     */
    @Query("SELECT u FROM User u " +
           "WHERE (:keyword IS NULL OR :keyword = '' " +
           "     OR LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "     OR LOWER(COALESCE(u.fullName, '')) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "     OR LOWER(COALESCE(u.phoneNumber, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "ORDER BY u.createdAt DESC, u.id DESC")
    List<User> searchAdminByKeyword(@Param("keyword") String keyword);

    /**
     * Tìm kiếm tài khoản theo trạng thái cụ thể và từ khóa tùy chọn, tối ưu qua {@code idx_users_status_created_id}.
     */
    @Query("SELECT u FROM User u " +
           "WHERE u.status = :status " +
           "AND (:keyword IS NULL OR :keyword = '' " +
           "     OR LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "     OR LOWER(COALESCE(u.fullName, '')) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "     OR LOWER(COALESCE(u.phoneNumber, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "ORDER BY u.createdAt DESC, u.id DESC")
    List<User> searchAdminByStatusAndKeyword(@Param("status") UserStatus status,
                                             @Param("keyword") String keyword);
}
