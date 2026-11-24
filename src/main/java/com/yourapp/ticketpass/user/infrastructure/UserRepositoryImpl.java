package com.yourapp.ticketpass.user.infrastructure;

import com.yourapp.ticketpass.user.domain.User;
import com.yourapp.ticketpass.user.domain.UserRepository;
import com.yourapp.ticketpass.user.domain.UserRole;
import com.yourapp.ticketpass.user.domain.UserStatus;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

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
