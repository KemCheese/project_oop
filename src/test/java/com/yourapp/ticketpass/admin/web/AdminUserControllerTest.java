package com.yourapp.ticketpass.admin.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yourapp.ticketpass.admin.application.AdminUserModerationQueryService;
import com.yourapp.ticketpass.admin.application.AdminUserModerationQueryService.ModerationOverview;
import com.yourapp.ticketpass.admin.web.dto.AdminUserStatsDto;
import com.yourapp.ticketpass.shared.exception.BusinessRuleViolationException;
import com.yourapp.ticketpass.user.application.AccountSecurityService;
import com.yourapp.ticketpass.user.application.UserQueryService;
import com.yourapp.ticketpass.user.domain.User;
import com.yourapp.ticketpass.user.domain.UserRole;
import com.yourapp.ticketpass.user.domain.UserStatus;
import java.security.Principal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

@ExtendWith(MockitoExtension.class)
@DisplayName("Unit Test - AdminUserController")
class AdminUserControllerTest {

    @Mock
    private AdminUserModerationQueryService queryService;

    @Mock
    private AccountSecurityService accountSecurityService;

    @Mock
    private UserQueryService userQueryService;

    @InjectMocks
    private AdminUserController controller;

    private User adminUser() {
        User admin = new User();
        admin.setId(1L);
        admin.setEmail("admin@ticketpass.vn");
        admin.setRole(UserRole.ADMIN);
        admin.setStatus(UserStatus.ACTIVE);
        return admin;
    }

    @Test
    @DisplayName("GET /admin/users nạp danh sách, stats và currentAdminId")
    void listUsers_ShouldPopulateModelAndReturnUsersView() {
        Principal principal = () -> "admin@ticketpass.vn";
        when(userQueryService.findByEmail("admin@ticketpass.vn")).thenReturn(Optional.of(adminUser()));
        ModerationOverview overview = new ModerationOverview(
                AdminUserStatsDto.builder().totalUsers(2).activeUsers(1).lockedUsers(1).warningUsers(1).build(),
                List.of()
        );
        when(queryService.loadModerationOverview("alice", UserStatus.BANNED, true)).thenReturn(overview);

        Model model = new ConcurrentModel();
        String view = controller.listUsers("alice", "BANNED", "true", principal, model);

        assertThat(view).isEqualTo("admin/users");
        assertThat(model.getAttribute("currentAdminId")).isEqualTo(1L);
        assertThat(model.getAttribute("selectedStatus")).isEqualTo(UserStatus.BANNED);
        assertThat(model.getAttribute("warningOnly")).isEqualTo(true);

        Model badStatusModel = new ConcurrentModel();
        when(queryService.loadModerationOverview(null, null, false)).thenReturn(overview);
        controller.listUsers(null, "SUSPENDED", "garbage", principal, badStatusModel);
        assertThat(badStatusModel.getAttribute("warningOnly")).isEqualTo(false);
        assertThat(badStatusModel.getAttribute("errorMessage")).isNotNull();
    }

    @Test
    @DisplayName("POST /admin/users/{id}/ban thành công: gọi service khóa (BANNED), giữ filter và đặt flash successMessage")
    void banUser_WhenValid_ShouldRedirectWithSuccessFlash() {
        Principal principal = () -> "admin@ticketpass.vn";
        when(userQueryService.findByEmail("admin@ticketpass.vn")).thenReturn(Optional.of(adminUser()));

        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();
        String view = controller.banUser(
                10L, "Gian lận bán vé", "alice", "ACTIVE", true, principal, redirect);

        assertThat(view).isEqualTo("redirect:/admin/users");
        verify(accountSecurityService).moderateUserStatus(10L, 1L, UserStatus.BANNED, "Gian lận bán vé");
        assertThat(redirect.getFlashAttributes().get("successMessage").toString()).contains("Đã khóa tài khoản #10");
        assertThat(redirect.asMap()).containsEntry("keyword", "alice");
        assertThat(redirect.asMap()).containsEntry("status", "ACTIVE");
        assertThat(redirect.asMap()).containsEntry("warningOnly", "true");
    }

    @Test
    @DisplayName("POST /admin/users/{id}/ban khi ném BusinessRuleViolationException: chuyển thành flash errorMessage")
    void banUser_WhenBusinessError_ShouldRedirectWithErrorFlash() {
        Principal principal = () -> "admin@ticketpass.vn";
        when(userQueryService.findByEmail("admin@ticketpass.vn")).thenReturn(Optional.of(adminUser()));
        doThrow(new BusinessRuleViolationException("Không thể tự khóa"))
                .when(accountSecurityService).moderateUserStatus(1L, 1L, UserStatus.BANNED, "test");

        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();
        String view = controller.banUser(1L, "test", null, null, null, principal, redirect);

        assertThat(view).isEqualTo("redirect:/admin/users");
        assertThat(redirect.getFlashAttributes().get("errorMessage")).isEqualTo("Không thể tự khóa");

        doThrow(new OptimisticLockingFailureException("Concurrent update"))
                .when(accountSecurityService).moderateUserStatus(2L, 1L, UserStatus.BANNED, "Vi phạm");
        RedirectAttributesModelMap conflictRedirect = new RedirectAttributesModelMap();
        String conflictView = controller.banUser(2L, "Vi phạm", null, null, null, principal, conflictRedirect);
        assertThat(conflictView).isEqualTo("redirect:/admin/users");
        assertThat(conflictRedirect.getFlashAttributes().get("errorMessage")).isNotNull();
    }

    @Test
    @DisplayName("POST /admin/users/{id}/activate thành công: mở khóa về ACTIVE")
    void activateUser_WhenValid_ShouldActivateAndRedirect() {
        Principal principal = () -> "admin@ticketpass.vn";
        when(userQueryService.findByEmail("admin@ticketpass.vn")).thenReturn(Optional.of(adminUser()));

        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();
        String view = controller.activateUser(10L, "Đã xác minh", null, null, false, principal, redirect);

        assertThat(view).isEqualTo("redirect:/admin/users");
        verify(accountSecurityService).moderateUserStatus(10L, 1L, UserStatus.ACTIVE, "Đã xác minh");
        assertThat(redirect.getFlashAttributes()).containsKey("successMessage");
    }
}
