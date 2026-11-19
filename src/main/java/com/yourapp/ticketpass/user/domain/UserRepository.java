package com.yourapp.ticketpass.user.domain;

import java.util.List;
import java.util.Optional;

/**
 * Port lưu trữ của module {@code user}, khai báo tại tầng domain.
 *
 * <p>Theo nguyên tắc Dependency Inversion, tầng domain và application chỉ phụ thuộc vào
 * interface này chứ không phụ thuộc Spring Data JPA. Hiện thực nằm ở
 * {@code com.yourapp.ticketpass.user.infrastructure.UserRepositoryImpl}, nơi delegate sang
 * {@code UserJpaRepository}. Khi cần đổi sang kho lưu trữ khác (in-memory cho unit test,
 * JDBC thuần) chỉ phải thay hiện thực mà không sửa tầng trên.</p>
 *
 * <p>Module: user</p>
 *
 * Chủ sở hữu: Người C
 */
public interface UserRepository {

    /**
     * Lưu mới hoặc cập nhật một tài khoản.
     *
     * @param user tài khoản cần lưu
     * @return tài khoản sau khi lưu, đã có id
     */
    User save(User user);

    /**
     * Tìm tài khoản theo khoá chính.
     *
     * @param id id của tài khoản
     * @return tài khoản nếu tồn tại
     */
    Optional<User> findById(Long id);

    /**
     * Tìm tài khoản theo email đăng nhập, phục vụ xác thực và kiểm tra trùng.
     *
     * @param email email cần tìm
     * @return tài khoản nếu tồn tại
     */
    Optional<User> findByEmail(String email);

    /**
     * Kiểm tra nhanh email đã được đăng ký hay chưa.
     *
     * @param email email cần kiểm tra
     * @return {@code true} nếu đã có tài khoản dùng email này
     */
    boolean existsByEmail(String email);

    /**
     * Lấy danh sách tài khoản theo trạng thái, phục vụ màn hình quản trị.
     *
     * @param status trạng thái cần lọc
     * @return danh sách tài khoản đang ở trạng thái đó
     */
    List<User> findByStatus(UserStatus status);

    /**
     * Lấy danh sách tài khoản theo vai trò.
     *
     * @param role vai trò cần lọc
     * @return danh sách tài khoản có vai trò đó
     */
    List<User> findByRole(UserRole role);

    /**
     * Tìm kiếm và lọc tài khoản phục vụ màn hình quản trị người dùng của Admin.
     *
     * @param keyword từ khóa tìm không phân biệt hoa thường theo email, họ tên hoặc SĐT (có thể null)
     * @param status trạng thái cần lọc (có thể null nếu lấy mọi trạng thái)
     * @return danh sách tài khoản khớp điều kiện, mới nhất xếp trước
     */
    List<User> searchForAdmin(String keyword, UserStatus status);
}
