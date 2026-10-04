package com.yourapp.ticketpass.user.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yourapp.ticketpass.reputation.application.UpdateReputationUseCase;
import com.yourapp.ticketpass.reputation.domain.UserReputationScore;
import com.yourapp.ticketpass.shared.exception.BusinessRuleViolationException;
import com.yourapp.ticketpass.user.application.RegisterUserUseCase;
import com.yourapp.ticketpass.user.application.StarredTicketUseCase;
import com.yourapp.ticketpass.user.application.UserProfileService;
import com.yourapp.ticketpass.user.application.UserQueryService;
import com.yourapp.ticketpass.user.domain.User;
import com.yourapp.ticketpass.user.domain.UserRole;
import com.yourapp.ticketpass.user.domain.UserStatus;
import com.yourapp.ticketpass.user.web.dto.RegisterRequest;
import com.yourapp.ticketpass.user.web.dto.UpdateBankAccountRequest;
import com.yourapp.ticketpass.user.web.dto.UpdateProfileRequest;
import com.yourapp.ticketpass.user.web.dto.UserProfileResponse;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.security.Principal;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.web.multipart.MultipartFile;

/**
 * Unit test cho {@link UserController} — Task C10.
 *
 * <p>Kiểm thử toàn diện các route xác thực (login, register) và quản lý hồ sơ cá nhân
 * (profile, updateProfile, updateAvatar, updateBankAccount). Xác minh việc che dấu số tài
 * khoản ngân hàng (RR-1 & RR-5), phân luồng lỗi validation, và chống lỗ hổng IDOR (RR-4).</p>
 *
 * <p>Module: user</p>
 *
 * Chủ sở hữu: Người C
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserController Test - Controller quản lý hồ sơ và xác thực")
class UserControllerTest {

    @Mock
    private RegisterUserUseCase registerUserUseCase;

    @Mock
    private UserProfileService userProfileService;

    @Mock
    private UserQueryService userQueryService;

    @Mock
    private UpdateReputationUseCase updateReputationUseCase;

    @Mock
    private StarredTicketUseCase starredTicketUseCase;

    @InjectMocks
    private UserController userController;

    private User buildUser(Long id, String email) {
        User u = new User();
        u.setId(id);
        u.setEmail(email);
        u.setFullName("Nguyen Van A");
        u.setRole(UserRole.USER);
        u.setStatus(UserStatus.ACTIVE);
        return u;
    }

    private UserProfileResponse buildProfileResponse(Long id, String email, String bankAccount) {
        UserProfileResponse resp = new UserProfileResponse();
        resp.setId(id);
        resp.setEmail(email);
        resp.setFullName("Nguyen Van A");
        resp.setRole(UserRole.USER);
        resp.setStatus(UserStatus.ACTIVE);
        resp.setBankName("Vietcombank");
        resp.setBankAccountNumber(bankAccount);
        resp.setBankAccountHolder("NGUYEN VAN A");
        return resp;
    }

    private UserReputationScore buildReputationScore(Long userId, BigDecimal score, int totalTx) {
        UserReputationScore s = new UserReputationScore();
        s.setId(userId);
        s.setUserId(userId);
        s.setScore(score);
        s.setTotalTransactions(totalTx);
        return s;
    }

    // ═══════════════════════════ AUTHENTICATION ROUTES ════════════════════════

    @Nested
    @DisplayName("1. GET /login")
    class LoginRouteTests {

        @Test
        @DisplayName("Trả về view auth/login")
        void loginForm_ShouldReturnLoginView() {
            Model model = new ConcurrentModel();
            String view = userController.loginForm(model);
            assertThat(view).isEqualTo("auth/login");
        }
    }

    @Nested
    @DisplayName("2. GET /register")
    class RegisterRouteTests {

        @Test
        @DisplayName("Trả về view auth/register kèm model attribute registerRequest")
        void registerForm_ShouldReturnRegisterViewWithModel() {
            Model model = new ConcurrentModel();
            String view = userController.registerForm(model);

            assertThat(view).isEqualTo("auth/register");
            assertThat(model.getAttribute("registerRequest")).isInstanceOf(RegisterRequest.class);
        }
    }

    @Nested
    @DisplayName("3. POST /register")
    class PostRegisterRouteTests {

        @Test
        @DisplayName("Đăng ký thành công: chuyển hướng sang /login?registered")
        void register_WhenValid_ShouldRedirectToLogin() {
            RegisterRequest request = new RegisterRequest();
            request.setEmail("newuser@example.com");
            BindingResult bindingResult = new BeanPropertyBindingResult(request, "registerRequest");

            String view = userController.register(request, bindingResult);

            assertThat(view).isEqualTo("redirect:/login?registered");
            verify(registerUserUseCase).register(request);
        }

        @Test
        @DisplayName("Lỗi Bean Validation: trả về view auth/register và không gọi UseCase")
        void register_WhenBindingErrors_ShouldReturnRegisterView() {
            RegisterRequest request = new RegisterRequest();
            BindingResult bindingResult = new BeanPropertyBindingResult(request, "registerRequest");
            bindingResult.rejectValue("email", "NotEmpty", "Email khong duoc de trong");

            String view = userController.register(request, bindingResult);

            assertThat(view).isEqualTo("auth/register");
            verify(registerUserUseCase, never()).register(any());
        }

        @Test
        @DisplayName("Lỗi trùng email: gán lỗi vào field email và trả về view auth/register")
        void register_WhenEmailDuplicate_ShouldRejectEmailField() {
            RegisterRequest request = new RegisterRequest();
            request.setEmail("exist@example.com");
            BindingResult bindingResult = new BeanPropertyBindingResult(request, "registerRequest");

            doThrow(new BusinessRuleViolationException("Email 'exist@example.com' da duoc su dung"))
                    .when(registerUserUseCase).register(request);

            String view = userController.register(request, bindingResult);

            assertThat(view).isEqualTo("auth/register");
            assertThat(bindingResult.hasFieldErrors("email")).isTrue();
            assertThat(bindingResult.getFieldError("email").getDefaultMessage()).contains("Email");
        }

        @Test
        @DisplayName("Lỗi mật khẩu không khớp: gán lỗi vào field confirmPassword")
        void register_WhenPasswordMismatch_ShouldRejectConfirmPasswordField() {
            RegisterRequest request = new RegisterRequest();
            BindingResult bindingResult = new BeanPropertyBindingResult(request, "registerRequest");

            doThrow(new BusinessRuleViolationException("Mật khẩu xác nhận không khớp"))
                    .when(registerUserUseCase).register(request);

            String view = userController.register(request, bindingResult);

            assertThat(view).isEqualTo("auth/register");
            assertThat(bindingResult.hasFieldErrors("confirmPassword")).isTrue();
            assertThat(bindingResult.getFieldError("confirmPassword").getDefaultMessage()).contains("Mật khẩu");
        }
    }

    // ═══════════════════════════ PROFILE ROUTES ════════════════════════

    @Nested
    @DisplayName("4. GET /profile")
    class GetProfileRouteTests {

        @Test
        @DisplayName("Lấy hồ sơ cá nhân thành công: nạp thông tin user, uy tín và che dấu số tài khoản (RR-1 & RR-5)")
        void profile_WhenAuthenticated_ShouldPopulateModelAndMaskBankAccount() {
            Principal principal = () -> "user@example.com";
            Model model = new ConcurrentModel();
            User user = buildUser(10L, "user@example.com");
            UserProfileResponse profileResponse = buildProfileResponse(10L, "user@example.com", "0123456789");
            UserReputationScore score = buildReputationScore(10L, new BigDecimal("98.50"), 15);

            when(userQueryService.findByEmail("user@example.com")).thenReturn(Optional.of(user));
            when(userProfileService.getProfile(10L)).thenReturn(profileResponse);
            when(updateReputationUseCase.getOrCreate(10L)).thenReturn(score);

            String view = userController.profile(principal, model);

            assertThat(view).isEqualTo("user/profile");

            UserProfileResponse modelProfile = (UserProfileResponse) model.getAttribute("profile");
            assertThat(modelProfile).isNotNull();
            assertThat(modelProfile.getReputationScore()).isEqualTo(new BigDecimal("98.50"));
            assertThat(modelProfile.getTotalTransactions()).isEqualTo(15);

            // RR-1 & RR-5: Số tài khoản ngân hàng phải được che dấu dạng ******6789
            String masked = (String) model.getAttribute("maskedBankAccount");
            assertThat(masked).isEqualTo("******6789");

            assertThat(model.getAttribute("starredTickets")).isNotNull();
            assertThat(model.getAttribute("starredCount")).isEqualTo(0);

            UpdateProfileRequest formReq = (UpdateProfileRequest) model.getAttribute("updateProfileRequest");
            assertThat(formReq).isNotNull();
            assertThat(formReq.getFullName()).isEqualTo(profileResponse.getFullName());
            assertThat(formReq.getPhoneNumber()).isEqualTo(profileResponse.getPhoneNumber());

            UpdateBankAccountRequest bankReq = (UpdateBankAccountRequest) model.getAttribute("updateBankAccountRequest");
            assertThat(bankReq).isNotNull();
            assertThat(bankReq.getBankName()).isEqualTo(profileResponse.getBankName());
            assertThat(bankReq.getBankAccountHolder()).isEqualTo(profileResponse.getBankAccountHolder());
            assertThat(bankReq.getBankAccountNumber()).isNull(); // RR-5
        }

        @Test
        @DisplayName("Khi số tài khoản ngân hàng chưa được khai báo: maskedBankAccount trả về null")
        void profile_WhenBankAccountNull_ShouldHaveNullMaskedBankAccount() {
            Principal principal = () -> "user@example.com";
            Model model = new ConcurrentModel();
            User user = buildUser(10L, "user@example.com");
            UserProfileResponse profileResponse = buildProfileResponse(10L, "user@example.com", null);
            UserReputationScore score = buildReputationScore(10L, new BigDecimal("100.00"), 0);

            when(userQueryService.findByEmail("user@example.com")).thenReturn(Optional.of(user));
            when(userProfileService.getProfile(10L)).thenReturn(profileResponse);
            when(updateReputationUseCase.getOrCreate(10L)).thenReturn(score);

            userController.profile(principal, model);

            assertThat(model.getAttribute("maskedBankAccount")).isNull();
        }
    }

    @Nested
    @DisplayName("5. POST /profile")
    class UpdateProfileRouteTests {

        @Test
        @DisplayName("Cập nhật thông tin cá nhân hợp lệ: chuyển hướng sang /profile?updated")
        void updateProfile_WhenValid_ShouldCallServiceAndRedirect() {
            Principal principal = () -> "user@example.com";
            Model model = new ConcurrentModel();
            User user = buildUser(10L, "user@example.com");

            UpdateProfileRequest request = new UpdateProfileRequest();
            request.setFullName("Nguyen Van B");
            request.setPhoneNumber("0912345678");
            BindingResult bindingResult = new BeanPropertyBindingResult(request, "updateProfileRequest");

            when(userQueryService.findByEmail("user@example.com")).thenReturn(Optional.of(user));

            String view = userController.updateProfile(request, bindingResult, principal, model);

            assertThat(view).isEqualTo("redirect:/profile?updated");
            verify(userProfileService).updateProfile(10L, request);
        }

        @Test
        @DisplayName("Cập nhật với số điện thoại rỗng: hợp lệ và chuyển hướng sang /profile?updated")
        void updateProfile_WhenPhoneNumberBlank_ShouldCallServiceAndRedirect() {
            Principal principal = () -> "user@example.com";
            Model model = new ConcurrentModel();
            User user = buildUser(10L, "user@example.com");

            UpdateProfileRequest request = new UpdateProfileRequest();
            request.setFullName("Nguyen Van B");
            request.setPhoneNumber("");
            BindingResult bindingResult = new BeanPropertyBindingResult(request, "updateProfileRequest");

            when(userQueryService.findByEmail("user@example.com")).thenReturn(Optional.of(user));

            String view = userController.updateProfile(request, bindingResult, principal, model);

            assertThat(view).isEqualTo("redirect:/profile?updated");
            verify(userProfileService).updateProfile(10L, request);
        }

        @Test
        @DisplayName("Lỗi validation khi cập nhật thông tin: nạp lại model và trả về view user/profile")
        void updateProfile_WhenValidationErrors_ShouldRepopulateModelAndReturnProfileView() {
            Principal principal = () -> "user@example.com";
            Model model = new ConcurrentModel();
            User user = buildUser(10L, "user@example.com");
            UserProfileResponse profileResponse = buildProfileResponse(10L, "user@example.com", "0123456789");
            UserReputationScore score = buildReputationScore(10L, new BigDecimal("100.00"), 0);

            UpdateProfileRequest request = new UpdateProfileRequest();
            BindingResult bindingResult = new BeanPropertyBindingResult(request, "updateProfileRequest");
            bindingResult.rejectValue("fullName", "NotBlank", "Ho ten khong duoc de trong");

            when(userQueryService.findByEmail("user@example.com")).thenReturn(Optional.of(user));
            when(userProfileService.getProfile(10L)).thenReturn(profileResponse);
            when(updateReputationUseCase.getOrCreate(10L)).thenReturn(score);

            String view = userController.updateProfile(request, bindingResult, principal, model);

            assertThat(view).isEqualTo("user/profile");
            verify(userProfileService, never()).updateProfile(anyLong(), any());
            assertThat(model.getAttribute("profile")).isNotNull();
            assertThat(model.getAttribute("maskedBankAccount")).isEqualTo("******6789");
            assertThat(model.getAttribute("updateProfileRequest")).isSameAs(request);
        }
    }

    @Nested
    @DisplayName("6. POST /profile/avatar")
    class UpdateAvatarRouteTests {

        @Test
        @DisplayName("Tải lên avatar hợp lệ: gọi service và chuyển hướng sang /profile?avatarUpdated")
        void updateAvatar_WhenValidFile_ShouldCallServiceAndRedirect() throws Exception {
            Principal principal = () -> "user@example.com";
            User user = buildUser(10L, "user@example.com");

            MultipartFile file = mock(MultipartFile.class);
            when(file.isEmpty()).thenReturn(false);
            when(file.getInputStream()).thenReturn(new ByteArrayInputStream("test image content".getBytes()));
            when(file.getOriginalFilename()).thenReturn("avatar.png");
            when(file.getContentType()).thenReturn("image/png");
            when(file.getSize()).thenReturn(1024L);

            when(userQueryService.findByEmail("user@example.com")).thenReturn(Optional.of(user));

            String view = userController.updateAvatar(file, principal);

            assertThat(view).isEqualTo("redirect:/profile?avatarUpdated");
            verify(userProfileService).updateAvatar(eq(10L), eq("avatar.png"), eq("image/png"), eq(1024L), any());
        }

        @Test
        @DisplayName("Tệp rỗng hoặc null: chuyển hướng sang /profile?avatarError=empty mà không gọi service")
        void updateAvatar_WhenFileIsEmpty_ShouldRedirectWithEmptyError() {
            Principal principal = () -> "user@example.com";
            MultipartFile file = mock(MultipartFile.class);
            when(file.isEmpty()).thenReturn(true);

            String view = userController.updateAvatar(file, principal);

            assertThat(view).isEqualTo("redirect:/profile?avatarError=empty");
            verify(userProfileService, never()).updateAvatar(anyLong(), anyString(), anyString(), anyLong(), any());
        }

        @Test
        @DisplayName("Lỗi vi phạm nghiệp vụ (dung lượng > 5MB hoặc sai định dạng): bắt ngoại lệ và redirect avatarError")
        void updateAvatar_WhenBusinessRuleViolation_ShouldRedirectWithAvatarError() throws Exception {
            Principal principal = () -> "user@example.com";
            User user = buildUser(10L, "user@example.com");

            MultipartFile file = mock(MultipartFile.class);
            when(file.isEmpty()).thenReturn(false);
            when(file.getInputStream()).thenReturn(new ByteArrayInputStream("test".getBytes()));
            when(file.getOriginalFilename()).thenReturn("malicious.exe");
            when(file.getContentType()).thenReturn("application/octet-stream");
            when(file.getSize()).thenReturn(2048L);

            when(userQueryService.findByEmail("user@example.com")).thenReturn(Optional.of(user));
            doThrow(new BusinessRuleViolationException("Dinh dang tep khong hop le"))
                    .when(userProfileService).updateAvatar(anyLong(), anyString(), anyString(), anyLong(), any());

            String view = userController.updateAvatar(file, principal);

            assertThat(view).isEqualTo("redirect:/profile?avatarError");
        }
    }

    @Nested
    @DisplayName("7. POST /profile/bank")
    class UpdateBankAccountRouteTests {

        @Test
        @DisplayName("Cập nhật tài khoản ngân hàng hợp lệ: gọi service và chuyển hướng sang /profile?bankUpdated")
        void updateBankAccount_WhenValid_ShouldCallServiceAndRedirect() {
            Principal principal = () -> "user@example.com";
            Model model = new ConcurrentModel();
            User user = buildUser(10L, "user@example.com");

            UpdateBankAccountRequest request = new UpdateBankAccountRequest();
            request.setBankName("MB Bank");
            request.setBankAccountNumber("9876543210");
            request.setBankAccountHolder("NGUYEN VAN A");
            BindingResult bindingResult = new BeanPropertyBindingResult(request, "updateBankAccountRequest");

            when(userQueryService.findByEmail("user@example.com")).thenReturn(Optional.of(user));

            String view = userController.updateBankAccount(request, bindingResult, principal, model);

            assertThat(view).isEqualTo("redirect:/profile?bankUpdated");
            verify(userProfileService).updateBankAccount(10L, "MB Bank", "9876543210", "NGUYEN VAN A");
        }

        @Test
        @DisplayName("Lỗi validation tài khoản ngân hàng: nạp lại model và trả về view user/profile")
        void updateBankAccount_WhenValidationErrors_ShouldRepopulateModelAndReturnProfileView() {
            Principal principal = () -> "user@example.com";
            Model model = new ConcurrentModel();
            User user = buildUser(10L, "user@example.com");
            UserProfileResponse profileResponse = buildProfileResponse(10L, "user@example.com", "0123456789");
            UserReputationScore score = buildReputationScore(10L, new BigDecimal("100.00"), 0);

            UpdateBankAccountRequest request = new UpdateBankAccountRequest();
            BindingResult bindingResult = new BeanPropertyBindingResult(request, "updateBankAccountRequest");
            bindingResult.rejectValue("bankAccountNumber", "NotBlank", "So tai khoan khong duoc de trong");

            when(userQueryService.findByEmail("user@example.com")).thenReturn(Optional.of(user));
            when(userProfileService.getProfile(10L)).thenReturn(profileResponse);
            when(updateReputationUseCase.getOrCreate(10L)).thenReturn(score);

            String view = userController.updateBankAccount(request, bindingResult, principal, model);

            assertThat(view).isEqualTo("user/profile");
            verify(userProfileService, never()).updateBankAccount(anyLong(), anyString(), anyString(), anyString());
            assertThat(model.getAttribute("profile")).isNotNull();
            assertThat(model.getAttribute("maskedBankAccount")).isEqualTo("******6789");
            assertThat(model.getAttribute("updateBankAccountRequest")).isSameAs(request);
        }
    }
}
