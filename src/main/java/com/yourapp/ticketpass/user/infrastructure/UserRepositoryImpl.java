package com.yourapp.ticketpass.user.infrastructure;

import com.yourapp.ticketpass.user.domain.User;
import com.yourapp.ticketpass.user.domain.UserRepository;
import com.yourapp.ticketpass.user.domain.UserRole;
import com.yourapp.ticketpass.user.domain.UserStatus;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * Hiện thực port {@link UserRepository} bằng Spring Data JPA.
 *
 * <p>Lớp adapter này chỉ chuyển tiếp lời gọi sang {@link UserJpaRepository}, không chứa
 * business logic. Đây là chỗ duy nhất trong module {@code user} biết đến sự tồn tại của
 * Spring Data; nhờ vậy muốn đổi công nghệ lưu trữ chỉ cần viết một adapter khác.</p>
 *
 * <p>Module: user</p>
 *
 * Chủ sở hữu: Người C
 */
@Repository
@RequiredArgsConstructor
public class UserRepositoryImpl implements UserRepository {

    private final UserJpaRepository userJpaRepository;

    @Override
    public User save(User user) {
        return userJpaRepository.save(user);
    }

    @Override
    public Optional<User> findById(Long id) {
        return userJpaRepository.findById(id);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return userJpaRepository.findByEmail(email);
    }

    @Override
    public boolean existsByEmail(String email) {
        return userJpaRepository.existsByEmail(email);
    }

    @Override
    public List<User> findByStatus(UserStatus status) {
        return userJpaRepository.findByStatus(status);
    }

    @Override
    public List<User> findByRole(UserRole role) {
        return userJpaRepository.findByRole(role);
    }

    @Override
    public List<User> searchForAdmin(String keyword, UserStatus status) {
        String cleanKeyword = (keyword != null && !keyword.isBlank()) ? keyword.trim() : null;
        if (status == null) {
            return userJpaRepository.searchAdminByKeyword(cleanKeyword);
        }
        return userJpaRepository.searchAdminByStatusAndKeyword(status, cleanKeyword);
    }
}
