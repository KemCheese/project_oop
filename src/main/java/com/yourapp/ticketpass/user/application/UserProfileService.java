package com.yourapp.ticketpass.user.application;

import com.yourapp.ticketpass.shared.exception.BusinessRuleViolationException;
import com.yourapp.ticketpass.shared.exception.ResourceNotFoundException;
import com.yourapp.ticketpass.shared.storage.FileStorageService;
import com.yourapp.ticketpass.shared.storage.StoredFileRef;
import com.yourapp.ticketpass.user.domain.User;
import com.yourapp.ticketpass.user.domain.UserRepository;
import com.yourapp.ticketpass.user.web.dto.UpdateProfileRequest;
import com.yourapp.ticketpass.user.web.dto.UserProfileResponse;
import java.io.InputStream;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dịch vụ đọc và cập nhật hồ sơ cá nhân của người dùng.
 *
 * <p><b>Nguyên tắc 3:</b> ảnh đại diện được lưu qua {@link FileStorageService} chứ không ghi
 * thẳng ra đĩa. Entity {@code User} chỉ giữ {@code avatarStorageKey}; URL hiển thị được
 * dựng lại bằng {@code FileStorageService.buildPublicUrl} khi map sang
 * {@link UserProfileResponse}. Nhờ vậy khi đổi sang S3/MinIO không phải sửa code nghiệp vụ.</p>
 *
 * <p><b>Adapter Pattern:</b> {@code updateAvatar} gọi {@code fileStorageService.store()} /
 * {@code delete()} / {@code buildPublicUrl()} — service nghiệp vụ chỉ biết interface trừu tượng,
 * không biết file lưu ở đĩa hay S3.</p>
 *
 * <p><b>Repository Pattern:</b> {@code userRepository.findById()} / {@code save()} — tầng
 * application chỉ thấy interface domain, không dính Spring Data JPA.</p>
 *
 * <p>Module: user</p>
 *
 * Chủ sở hữu: Người C
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserProfileService {

    /** Các kiểu MIME ảnh được phép dùng làm avatar. */
    private static final Set<String> ALLOWED_AVATAR_MIME_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp"
    );

    /** Kích thước tối đa cho ảnh đại diện: 10 MB. */
    private static final long MAX_AVATAR_SIZE_BYTES = 10L * 1024 * 1024;

    private final UserRepository userRepository;

    private final FileStorageService fileStorageService;

    // ──────────────────────────────────────────────────────────────────────────
    // 1. Xem hồ sơ
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Lấy hồ sơ đầy đủ của một người dùng.
     *
     * <p>Hai trường {@code reputationScore} và {@code totalTransactions} được để {@code null}
     * vì chúng thuộc module {@code reputation}; tầng web ({@code UserController}) sẽ lắp thêm
     * sau khi truy vấn module uy tín. Nhờ vậy module {@code user} không phụ thuộc ngược vào
     * module {@code reputation} ở tầng domain.</p>
     *
     * @param userId id người dùng
     * @return hồ sơ người dùng
     * @throws ResourceNotFoundException nếu không tìm thấy user
     */
    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(Long userId) {
        User user = findUserOrThrow(userId);
        return mapToProfileResponse(user);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 2. Cập nhật thông tin cá nhân
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Cập nhật họ tên và số điện thoại.
     *
     * <p>Nếu số điện thoại thay đổi (bao gồm cả trường hợp xóa số), cờ
     * {@code phoneVerified} được đặt lại {@code false} để bắt buộc xác minh OTP lần nữa.
     * So sánh null-safe bằng {@code Objects.equals()}.</p>
     *
     * @param userId  id người dùng
     * @param request dữ liệu mới đã qua Bean Validation
     * @return hồ sơ sau khi cập nhật
     * @throws ResourceNotFoundException nếu không tìm thấy user
     */
    @Transactional
    public UserProfileResponse updateProfile(Long userId, UpdateProfileRequest request) {
        User user = findUserOrThrow(userId);

        // Cập nhật họ tên
        user.setFullName(request.getFullName() != null ? request.getFullName().trim() : null);

        // Kiểm tra thay đổi số điện thoại → reset cờ xác minh
        String oldPhone = user.getPhoneNumber();
        String newPhone = (request.getPhoneNumber() != null && !request.getPhoneNumber().trim().isEmpty())
                ? request.getPhoneNumber().trim()
                : null;

        if (!Objects.equals(oldPhone, newPhone)) {
            user.setPhoneVerified(false);
        }
        user.setPhoneNumber(newPhone);

        User savedUser = userRepository.save(user);
        return mapToProfileResponse(savedUser);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 3. Thay ảnh đại diện
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Thay ảnh đại diện.
     *
     * <p><b>Thứ tự an toàn:</b> Lưu ảnh mới TRƯỚC, xóa ảnh cũ SAU. Nếu {@code store()}
     * thất bại thì avatar cũ vẫn nguyên vẹn. Nếu {@code delete()} thất bại (file đã bị xóa
     * thủ công trên đĩa), chỉ log cảnh báo chứ không ném exception — vì avatar mới đã lưu
     * thành công, thao tác về phía người dùng hoàn tất.</p>
     *
     * <p><b>Adapter Pattern:</b> toàn bộ thao tác lưu trữ đi qua {@link FileStorageService}.
     * Đổi từ đĩa cục bộ sang S3 chỉ cần thay adapter, không sửa method này.</p>
     *
     * @param userId           id người dùng
     * @param originalFilename tên tệp gốc do người dùng tải lên
     * @param contentType      kiểu MIME của tệp
     * @param sizeBytes        kích thước tệp tính bằng byte
     * @param content          luồng dữ liệu ảnh
     * @throws ResourceNotFoundException       nếu không tìm thấy user
     * @throws BusinessRuleViolationException  nếu MIME không hợp lệ hoặc file quá lớn/rỗng
     */
    @Transactional
    public void updateAvatar(Long userId, String originalFilename, String contentType,
                             long sizeBytes, InputStream content) {
        // --- Validate MIME type ---
        if (contentType == null || !ALLOWED_AVATAR_MIME_TYPES.contains(contentType.toLowerCase())) {
            throw new BusinessRuleViolationException(
                    "Chỉ chấp nhận ảnh đại diện định dạng JPEG, PNG hoặc WebP.");
        }

        // --- Validate kích thước ---
        if (sizeBytes <= 0) {
            throw new BusinessRuleViolationException("Tệp ảnh rỗng, vui lòng chọn ảnh khác.");
        }
        if (sizeBytes > MAX_AVATAR_SIZE_BYTES) {
            throw new BusinessRuleViolationException(
                    "Ảnh đại diện không được vượt quá 10 MB.");
        }

        // --- Tìm user ---
        User user = findUserOrThrow(userId);

        // --- Lưu ảnh mới qua FileStorageService (Adapter Pattern) ---
        StoredFileRef ref = fileStorageService.store(
                "avatars", originalFilename, content, sizeBytes, contentType);

        // --- Ghi nhận storage key cũ trước khi ghi đè ---
        String oldAvatarKey = user.getAvatarStorageKey();

        // --- Cập nhật entity với key mới ---
        user.setAvatarStorageKey(ref.getStorageKey());
        userRepository.save(user);

        // --- Dọn rác: xóa avatar cũ (nếu có) ---
        if (oldAvatarKey != null) {
            try {
                fileStorageService.delete(oldAvatarKey);
            } catch (Exception e) {
                // Avatar mới đã lưu thành công → chỉ log cảnh báo, không phá luồng chính
                log.warn("Không thể xóa avatar cũ [storageKey={}]: {}", oldAvatarKey, e.getMessage());
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 4. Cập nhật tài khoản ngân hàng
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Cập nhật tài khoản ngân hàng nhận tiền bán vé.
     *
     * <p><b>Lưu ý rủi ro:</b> Hiện tại chưa chặn thay đổi khi user đang có giao dịch escrow
     * chưa giải ngân, vì module {@code user} không được import module {@code payment} theo
     * ma trận kiến trúc (01-kien-truc-he-thong.md, mục 4.2). Module {@code payment} khi giải
     * ngân nên snapshot thông tin ngân hàng tại thời điểm giao dịch để tránh rủi ro.</p>
     *
     * @param userId            id người dùng
     * @param bankName          tên ngân hàng
     * @param bankAccountNumber số tài khoản
     * @param bankAccountHolder tên chủ tài khoản
     * @throws ResourceNotFoundException nếu không tìm thấy user
     */
    @Transactional
    public void updateBankAccount(Long userId, String bankName,
                                  String bankAccountNumber, String bankAccountHolder) {
        User user = findUserOrThrow(userId);

        user.setBankName(bankName != null ? bankName.trim() : null);
        user.setBankAccountNumber(bankAccountNumber != null ? bankAccountNumber.trim() : null);
        user.setBankAccountHolder(bankAccountHolder != null ? bankAccountHolder.trim() : null);

        userRepository.save(user);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Helper methods
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Tìm user theo ID hoặc ném {@link ResourceNotFoundException}.
     *
     * @param userId id cần tìm
     * @return entity {@code User}
     */
    private User findUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
    }

    /**
     * Map entity {@code User} sang DTO {@link UserProfileResponse}.
     *
     * <p>Hai trường {@code reputationScore} và {@code totalTransactions} được để {@code null};
     * tầng web ({@code UserController}) sẽ truy vấn module {@code reputation} và lắp thêm.
     * Điều này giữ cho module {@code user} không phụ thuộc ngược vào module {@code reputation}
     * (tuân thủ ma trận import kiến trúc).</p>
     *
     * @param user entity cần chuyển đổi
     * @return DTO cho view
     */
    private UserProfileResponse mapToProfileResponse(User user) {
        UserProfileResponse response = new UserProfileResponse();
        response.setId(user.getId());
        response.setEmail(user.getEmail());
        response.setFullName(user.getFullName());
        response.setPhoneNumber(user.getPhoneNumber());
        response.setRole(user.getRole());
        response.setStatus(user.getStatus());
        response.setEmailVerified(user.isEmailVerified());
        response.setPhoneVerified(user.isPhoneVerified());
        response.setIdentityVerified(user.isIdentityVerified());
        response.setCreatedAt(user.getCreatedAt());

        // Thông tin tài khoản ngân hàng (RR-1: tầng web sẽ masking trước khi hiển thị)
        response.setBankName(user.getBankName());
        response.setBankAccountNumber(user.getBankAccountNumber());
        response.setBankAccountHolder(user.getBankAccountHolder());

        // Dựng URL avatar qua FileStorageService (Adapter Pattern)
        // Nếu user chưa upload avatar → avatarUrl = null
        if (user.getAvatarStorageKey() != null) {
            response.setAvatarUrl(
                    fileStorageService.buildPublicUrl(user.getAvatarStorageKey()));
        }

        // reputationScore và totalTransactions → null
        // Tầng web (UserController) sẽ lắp từ module reputation (Task C10)
        return response;
    }
}
