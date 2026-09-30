package com.yourapp.ticketpass.user.application;

import com.yourapp.ticketpass.user.domain.User;
import com.yourapp.ticketpass.user.domain.UserRepository;
import com.yourapp.ticketpass.user.domain.UserStatus;
import com.yourapp.ticketpass.user.web.dto.UserSummaryResponse;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Điểm truy vấn thông tin người dùng dành cho các module khác.
 *
 * <p>Khi module {@code ticket}, {@code payment} hay {@code dispute} cần
 * biết thông tin của một người dùng, họ gọi service này thay vì tự viết query hay tự inject
 * {@code UserJpaRepository}. Nhờ một cửa duy nhất, module {@code user} kiểm soát được dữ liệu
 * nào bị lộ ra ngoài và có thể đổi cấu trúc bảng mà không làm vỡ module khác.</p>
 *
 * <p>Các phương thức trả về {@code Optional} hoặc DTO chứ không phát tán entity đã gắn với
 * persistence context ra ngoài; riêng {@link #findById} và {@link #findByEmail} trả về entity
 * vì hai chỗ dùng chính là nội bộ module (ví dụ {@code UserDetailsServiceImpl}).</p>
 *
 * <p>Module: user</p>
 *
 * Chủ sở hữu: Người C
 */
@Service
@RequiredArgsConstructor
public class UserQueryService {

    private final UserRepository userRepository;

    /**
     * Tìm người dùng theo id.
     *
     * @param userId id người dùng
     * @return người dùng nếu tồn tại
     */
    public Optional<User> findById(Long userId) {
        return userRepository.findById(userId);
    }

    /**
     * Tìm người dùng theo email.
     *
     * <p>Email được chuẩn hoá về chữ thường và loại bỏ khoảng trắng đầu/cuối trước khi
     * truy vấn, đảm bảo khớp với cách lưu trữ trong {@code RegisterUserUseCase.register()}.</p>
     *
     * @param email email đăng nhập
     * @return người dùng nếu tồn tại
     */
    public Optional<User> findByEmail(String email) {
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }
        return userRepository.findByEmail(email.trim().toLowerCase());
    }

    /**
     * Liệt kê người dùng theo trạng thái, phục vụ màn hình quản trị.
     *
     * <p>Trả về DTO rút gọn {@link UserSummaryResponse}. Trường {@code reputationScore}
     * được để {@code null} — tầng {@code web} (AdminController) sẽ lắp thêm từ module
     * {@code reputation} nếu cần.</p>
     *
     * @param status trạng thái cần lọc
     * @return danh sách bản rút gọn của người dùng
     */
    public List<UserSummaryResponse> listByStatus(UserStatus status) {
        return userRepository.findByStatus(status).stream()
                .map(this::mapToSummary)
                .toList();
    }

    /**
     * Chuyển đổi entity {@code User} sang DTO rút gọn {@code UserSummaryResponse}.
     *
     * <p>Trường {@code reputationScore} để {@code null} — tầng web sẽ lắp thêm
     * nếu cần hiển thị. Không chứa thông tin nhạy cảm (số tài khoản ngân hàng,
     * số điện thoại).</p>
     */
    private UserSummaryResponse mapToSummary(User user) {
        UserSummaryResponse summary = new UserSummaryResponse();
        summary.setId(user.getId());
        summary.setEmail(user.getEmail());
        summary.setFullName(user.getFullName());
        summary.setRole(user.getRole());
        summary.setStatus(user.getStatus());
        // reputationScore → null, tầng web lắp thêm từ module reputation
        return summary;
    }
}
