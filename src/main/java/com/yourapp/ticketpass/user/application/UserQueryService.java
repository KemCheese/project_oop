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
        throw new UnsupportedOperationException(
                "TODO [Người C] Prompt 2: delegate sang userRepository.findById(userId).");
    }

    /**
     * Tìm người dùng theo email.
     *
     * @param email email đăng nhập
     * @return người dùng nếu tồn tại
     */
    public Optional<User> findByEmail(String email) {
        throw new UnsupportedOperationException(
                "TODO [Người C] Prompt 2: chuẩn hoá email về chữ thường rồi delegate sang "
                        + "userRepository.findByEmail.");
    }

    /**
     * Liệt kê người dùng theo trạng thái, phục vụ màn hình quản trị.
     *
     * @param status trạng thái cần lọc
     * @return danh sách bản rút gọn của người dùng
     */
    public List<UserSummaryResponse> listByStatus(UserStatus status) {
        throw new UnsupportedOperationException(
                "TODO [Người C] Prompt 2: gọi userRepository.findByStatus(status) rồi map từng User "
                        + "sang UserSummaryResponse; để trống reputationScore cho tầng web lắp thêm "
                        + "từ module reputation.");
    }
}
