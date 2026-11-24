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

@Service
@RequiredArgsConstructor
@Slf4j
public class UserProfileService {

    private static final Set<String> ALLOWED_AVATAR_MIME_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp"
    );

    private static final long MAX_AVATAR_SIZE_BYTES = 10L * 1024 * 1024;

    private final UserRepository userRepository;

    private final FileStorageService fileStorageService;

    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(Long userId) {
        User user = findUserOrThrow(userId);
        return mapToProfileResponse(user);
    }

    @Transactional
    public UserProfileResponse updateProfile(Long userId, UpdateProfileRequest request) {
        User user = findUserOrThrow(userId);

        user.setFullName(request.getFullName() != null ? request.getFullName().trim() : null);

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

    @Transactional
    public void updateAvatar(Long userId, String originalFilename, String contentType,
                             long sizeBytes, InputStream content) {
        if (contentType == null || !ALLOWED_AVATAR_MIME_TYPES.contains(contentType.toLowerCase())) {
            throw new BusinessRuleViolationException(
                    "Chỉ chấp nhận ảnh đại diện định dạng JPEG, PNG hoặc WebP.");
        }

        if (sizeBytes <= 0) {
            throw new BusinessRuleViolationException("Tệp ảnh rỗng, vui lòng chọn ảnh khác.");
        }
        if (sizeBytes > MAX_AVATAR_SIZE_BYTES) {
            throw new BusinessRuleViolationException(
                    "Ảnh đại diện không được vượt quá 10 MB.");
        }

        User user = findUserOrThrow(userId);

        StoredFileRef ref = fileStorageService.store(
                "avatars", originalFilename, content, sizeBytes, contentType);

        String oldAvatarKey = user.getAvatarStorageKey();

        user.setAvatarStorageKey(ref.getStorageKey());
        userRepository.save(user);

        if (oldAvatarKey != null) {
            try {
                fileStorageService.delete(oldAvatarKey);
            } catch (Exception e) {
                log.warn("Không thể xóa avatar cũ [storageKey={}]: {}", oldAvatarKey, e.getMessage());
            }
        }
    }

    @Transactional
    public void updateBankAccount(Long userId, String bankName,
                                  String bankAccountNumber, String bankAccountHolder) {
        User user = findUserOrThrow(userId);

        user.setBankName(bankName != null ? bankName.trim() : null);
        user.setBankAccountNumber(bankAccountNumber != null ? bankAccountNumber.trim() : null);
        user.setBankAccountHolder(bankAccountHolder != null ? bankAccountHolder.trim() : null);

        userRepository.save(user);
    }

    private User findUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
    }

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

        response.setBankName(user.getBankName());
        response.setBankAccountNumber(user.getBankAccountNumber());
        response.setBankAccountHolder(user.getBankAccountHolder());

        if (user.getAvatarStorageKey() != null) {
            response.setAvatarUrl(
                    fileStorageService.buildPublicUrl(user.getAvatarStorageKey()));
        }

        return response;
    }
}
