package com.yourapp.ticketpass.user.application;

import com.yourapp.ticketpass.user.domain.User;
import com.yourapp.ticketpass.user.domain.UserRepository;
import com.yourapp.ticketpass.user.domain.UserStatus;
import com.yourapp.ticketpass.user.web.dto.UserSummaryResponse;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserQueryService {

    private final UserRepository userRepository;

    public Optional<User> findById(Long userId) {
        return userRepository.findById(userId);
    }

    public Optional<User> findByEmail(String email) {
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }
        return userRepository.findByEmail(email.trim().toLowerCase());
    }

    public List<UserSummaryResponse> listByStatus(UserStatus status) {
        return userRepository.findByStatus(status).stream()
                .map(this::mapToSummary)
                .toList();
    }

    private UserSummaryResponse mapToSummary(User user) {
        UserSummaryResponse summary = new UserSummaryResponse();
        summary.setId(user.getId());
        summary.setEmail(user.getEmail());
        summary.setFullName(user.getFullName());
        summary.setRole(user.getRole());
        summary.setStatus(user.getStatus());
        return summary;
    }
}
