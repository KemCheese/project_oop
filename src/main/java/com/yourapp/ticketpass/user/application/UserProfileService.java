package com.yourapp.ticketpass.user.application;

import com.yourapp.ticketpass.shared.storage.FileStorageService;
import com.yourapp.ticketpass.user.domain.UserRepository;
import com.yourapp.ticketpass.user.web.dto.UpdateProfileRequest;
import com.yourapp.ticketpass.user.web.dto.UserProfileResponse;
import java.io.InputStream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Dịch vụ đọc và cập nhật hồ sơ cá nhân của người dùng.
 *
 * <p><b>Nguyên tắc 3:</b> ảnh đại diện được lưu qua {@link FileStorageService} chứ không ghi
 * thẳng ra đĩa. Entity {@code User} chỉ giữ {@code avatarStorageKey}; URL hiển thị được
 * dựng lại bằng {@code FileStorageService.buildPublicUrl} khi map sang
 * {@link UserProfileResponse}. Nhờ vậy khi đổi sang S3/MinIO không phải sửa code nghiệp vụ.</p>
 *
 * <p>Module: user</p>
 *
 * Chủ sở hữu: Người C
 */
@Service
@RequiredArgsConstructor
public class UserProfileService {

    private final UserRepository userRepository;

    private final FileStorageService fileStorageService;

    /**
     * Lấy hồ sơ đầy đủ của một người dùng.
     *
     * @param userId id người dùng
     * @return hồ sơ người dùng
     */
    public UserProfileResponse getProfile(Long userId) {
        throw new UnsupportedOperationException(
                "TODO [Người C] Prompt 2: tìm user theo userId qua userRepository.findById "
                        + "(ném ResourceNotFoundException nếu không có), map sang UserProfileResponse, "
                        + "dựng avatarUrl bằng fileStorageService.buildPublicUrl(avatarStorageKey) khi "
                        + "khoá ảnh khác null; để trống reputationScore/totalTransactions cho tầng web lắp thêm.");
    }

    /**
     * Cập nhật họ tên và số điện thoại.
     *
     * @param userId  id người dùng
     * @param request dữ liệu mới đã qua Bean Validation
     * @return hồ sơ sau khi cập nhật
     */
    public UserProfileResponse updateProfile(Long userId, UpdateProfileRequest request) {
        throw new UnsupportedOperationException(
                "TODO [Người C] Prompt 2: tìm user theo userId (ném ResourceNotFoundException nếu "
                        + "không có), gán fullName và phoneNumber từ request; nếu số điện thoại thay đổi "
                        + "thì đặt lại phoneVerified = false để bắt xác minh OTP lần nữa, lưu qua "
                        + "userRepository.save rồi map sang UserProfileResponse.");
    }

    /**
     * Thay ảnh đại diện.
     *
     * @param userId           id người dùng
     * @param originalFilename tên tệp gốc do người dùng tải lên
     * @param contentType      kiểu MIME của tệp
     * @param sizeBytes        kích thước tệp tính bằng byte
     * @param content          luồng dữ liệu ảnh
     */
    public void updateAvatar(Long userId, String originalFilename, String contentType, long sizeBytes, InputStream content) {
        throw new UnsupportedOperationException(
                "TODO [Người C] Prompt 2: kiểm tra contentType nằm trong danh sách ảnh cho phép và "
                        + "sizeBytes không vượt hạn mức (ném BusinessRuleViolationException nếu vi phạm), "
                        + "gọi fileStorageService.store(\"avatars\", originalFilename, content, sizeBytes, contentType), "
                        + "ghi storageKey mới vào user.avatarStorageKey, lưu user, sau đó xoá tệp ảnh cũ "
                        + "bằng fileStorageService.delete nếu trước đó đã có ảnh.");
    }

    /**
     * Cập nhật tài khoản ngân hàng nhận tiền bán vé.
     *
     * @param userId            id người dùng
     * @param bankName          tên ngân hàng
     * @param bankAccountNumber số tài khoản
     * @param bankAccountHolder tên chủ tài khoản
     */
    public void updateBankAccount(Long userId, String bankName, String bankAccountNumber, String bankAccountHolder) {
        throw new UnsupportedOperationException(
                "TODO [Người C] Prompt 2: tìm user theo userId (ném ResourceNotFoundException nếu "
                        + "không có), gán bankName/bankAccountNumber/bankAccountHolder rồi lưu; "
                        + "cân nhắc chặn thay đổi khi người dùng còn giao dịch đang giữ tiền tạm khoá "
                        + "để tránh chuyển tiền sai tài khoản.");
    }
}
