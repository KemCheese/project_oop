package com.yourapp.ticketpass.user.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.yourapp.ticketpass.reputation.domain.ReputationRepository;
import com.yourapp.ticketpass.reputation.domain.UserReputationScore;
import com.yourapp.ticketpass.shared.exception.BusinessRuleViolationException;
import com.yourapp.ticketpass.shared.exception.ResourceNotFoundException;
import com.yourapp.ticketpass.user.domain.User;
import com.yourapp.ticketpass.user.domain.UserRepository;
import com.yourapp.ticketpass.user.domain.UserStatus;
import com.yourapp.ticketpass.user.web.dto.RegisterRequest;
import com.yourapp.ticketpass.user.web.dto.UserProfileResponse;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RegisterUserUseCaseTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private ReputationRepository reputationRepository;

    @InjectMocks
    private RegisterUserUseCase registerUserUseCase;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(registerUserUseCase, "autoActive", true);
        ReflectionTestUtils.setField(registerUserUseCase, "activationSecret", "TEST_SECRET");
    }

    @Test
    void register_Success_AutoActiveTrue() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("test@gmail.com ");
        request.setPassword("password123");
        request.setConfirmPassword("password123");
        request.setFullName("Test User");

        when(userRepository.existsByEmail("test@gmail.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("encoded_pass");

        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            u.setId(10L);
            return u;
        });

        UserProfileResponse response = registerUserUseCase.register(request);

        verify(userRepository).save(any(User.class));
        verify(reputationRepository).save(any(UserReputationScore.class));

        assertEquals(10L, response.getId());
        assertEquals("test@gmail.com", response.getEmail());
        assertEquals(UserStatus.ACTIVE, response.getStatus());
        assertTrue(response.isEmailVerified());
        assertEquals(new BigDecimal("100.00"), response.getReputationScore());
    }

    @Test
    void register_Success_AutoActiveFalse() {
        ReflectionTestUtils.setField(registerUserUseCase, "autoActive", false);

        RegisterRequest request = new RegisterRequest();
        request.setEmail("pending@gmail.com");
        request.setPassword("pass");
        request.setConfirmPassword("pass");

        when(userRepository.existsByEmail("pending@gmail.com")).thenReturn(false);
        when(passwordEncoder.encode("pass")).thenReturn("encoded_pass");
        
        when(userRepository.save(any(User.class))).thenAnswer(i -> {
            User u = i.getArgument(0);
            u.setId(11L);
            return u;
        });

        UserProfileResponse response = registerUserUseCase.register(request);

        assertEquals(UserStatus.PENDING_VERIFICATION, response.getStatus());
        assertFalse(response.isEmailVerified());
    }

    @Test
    void register_PasswordMismatch_ThrowsException() {
        RegisterRequest request = new RegisterRequest();
        request.setPassword("pass1");
        request.setConfirmPassword("pass2");

        BusinessRuleViolationException ex = assertThrows(BusinessRuleViolationException.class,
                () -> registerUserUseCase.register(request));
        assertEquals("Mật khẩu xác nhận không khớp.", ex.getMessage());
    }

    @Test
    void register_EmailExists_ThrowsException() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("dup@gmail.com");
        request.setPassword("pass");
        request.setConfirmPassword("pass");

        when(userRepository.existsByEmail("dup@gmail.com")).thenReturn(true);

        BusinessRuleViolationException ex = assertThrows(BusinessRuleViolationException.class,
                () -> registerUserUseCase.register(request));
        assertEquals("Email đã được đăng ký trong hệ thống.", ex.getMessage());
    }

    @Test
    void verifyEmail_Success() {
        User user = new User();
        user.setId(1L);
        user.setEmail("test@gmail.com");
        user.setStatus(UserStatus.PENDING_VERIFICATION);
        user.setEmailVerified(false);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        String validToken = registerUserUseCase.generateVerificationToken(1L, "test@gmail.com");

        registerUserUseCase.verifyEmail(1L, validToken);

        assertTrue(user.isEmailVerified());
        assertEquals(UserStatus.ACTIVE, user.getStatus());
        verify(userRepository).save(user);
    }

    @Test
    void verifyEmail_InvalidToken_ThrowsException() {
        User user = new User();
        user.setId(1L);
        user.setEmail("test@gmail.com");
        user.setStatus(UserStatus.PENDING_VERIFICATION);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThrows(BusinessRuleViolationException.class,
                () -> registerUserUseCase.verifyEmail(1L, "wrong_token"));
    }

    @Test
    void verifyEmail_BannedUser_ThrowsException() {
        User user = new User();
        user.setId(1L);
        user.setStatus(UserStatus.BANNED);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThrows(BusinessRuleViolationException.class,
                () -> registerUserUseCase.verifyEmail(1L, "any_token"));
    }

    @Test
    void verifyEmail_UserNotFound_ThrowsException() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> registerUserUseCase.verifyEmail(99L, "token"));
    }
}
