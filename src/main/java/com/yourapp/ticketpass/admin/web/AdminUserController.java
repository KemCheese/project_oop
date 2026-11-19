package com.yourapp.ticketpass.admin.web;

import com.yourapp.ticketpass.admin.application.AdminUserModerationQueryService;
import com.yourapp.ticketpass.admin.application.AdminUserModerationQueryService.ModerationOverview;
import com.yourapp.ticketpass.shared.exception.BusinessRuleViolationException;
import com.yourapp.ticketpass.shared.exception.ResourceNotFoundException;
import com.yourapp.ticketpass.user.application.AccountSecurityService;
import com.yourapp.ticketpass.user.application.UserQueryService;
import com.yourapp.ticketpass.user.domain.User;
import com.yourapp.ticketpass.user.domain.UserStatus;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Controller quản trị người dùng, theo dõi điểm uy tín / cảnh báo gian lận và thực hiện
 * khóa, cấm hoặc mở khóa tài khoản dành riêng cho vai trò {@code ROLE_ADMIN}.
 *
 * <p>Module: admin</p>
 * <p>Chủ sở hữu: Người C (Dev C)</p>
 */
@Controller
@RequestMapping("/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserModerationQueryService adminUserModerationQueryService;
    private final AccountSecurityService accountSecurityService;
    private final UserQueryService userQueryService;

    /**
     * Hiển thị danh sách người dùng kèm điểm uy tín, lịch sử thua tranh chấp và nút kiểm duyệt.
     *
     * @param keyword từ khóa tìm theo email, họ tên hoặc số điện thoại
     * @param statusRaw chuỗi {@link UserStatus} tùy chọn
     * @param warningOnlyRaw tham số {@code true/false} lọc tài khoản có cờ cảnh báo rủi ro
     * @param principal thông tin Admin đang đăng nhập
     * @param model model Thymeleaf
     * @return tên view {@code admin/users}
     */
    @GetMapping
    public String listUsers(@RequestParam(value = "keyword", required = false) String keyword,
                            @RequestParam(value = "status", required = false) String statusRaw,
                            @RequestParam(value = "warningOnly", required = false) String warningOnlyRaw,
                            Principal principal,
                            Model model) {
        UserStatus selectedStatus = null;
        if (statusRaw != null && !statusRaw.isBlank()) {
            try {
                selectedStatus = UserStatus.valueOf(statusRaw.trim().toUpperCase());
            } catch (IllegalArgumentException ex) {
                model.addAttribute("errorMessage",
                        "Trạng thái lọc \"" + statusRaw.trim() + "\" không hợp lệ, đang hiển thị tất cả.");
            }
        }

        boolean warningOnly = "true".equalsIgnoreCase(warningOnlyRaw != null ? warningOnlyRaw.trim() : null);

        ModerationOverview overview = adminUserModerationQueryService.loadModerationOverview(
                keyword, selectedStatus, warningOnly);

        model.addAttribute("stats", overview.stats());
        model.addAttribute("users", overview.users());
        model.addAttribute("keyword", keyword != null ? keyword.trim() : "");
        model.addAttribute("selectedStatus", selectedStatus);
        model.addAttribute("warningOnly", warningOnly);
        model.addAttribute("allStatuses", UserStatus.values());
        model.addAttribute("currentAdminId", resolveAdminId(principal));

        return "admin/users";
    }

    /**
     * Tạm khóa tài khoản người dùng ({@link UserStatus#SUSPENDED}) và đá văng mọi phiên hoạt động.
     */
    @PostMapping("/{id}/suspend")
    public String suspendUser(@PathVariable("id") Long targetUserId,
                              @RequestParam(value = "reason", required = false) String reason,
                              @RequestParam(value = "keyword", required = false) String keyword,
                              @RequestParam(value = "status", required = false) String statusFilter,
                              @RequestParam(value = "warningOnly", required = false) Boolean warningOnly,
                              Principal principal,
                              RedirectAttributes redirectAttributes) {
        return handleModeration(
                targetUserId,
                UserStatus.SUSPENDED,
                reason,
                "Đã tạm khóa tài khoản #" + targetUserId + " và hủy toàn bộ phiên đăng nhập.",
                keyword,
                statusFilter,
                warningOnly,
                principal,
                redirectAttributes
        );
    }

    /**
     * Cấm tài khoản người dùng ({@link UserStatus#BANNED}) và đá văng mọi phiên hoạt động.
     */
    @PostMapping("/{id}/ban")
    public String banUser(@PathVariable("id") Long targetUserId,
                          @RequestParam(value = "reason", required = false) String reason,
                          @RequestParam(value = "keyword", required = false) String keyword,
                          @RequestParam(value = "status", required = false) String statusFilter,
                          @RequestParam(value = "warningOnly", required = false) Boolean warningOnly,
                          Principal principal,
                          RedirectAttributes redirectAttributes) {
        return handleModeration(
                targetUserId,
                UserStatus.BANNED,
                reason,
                "Đã cấm tài khoản #" + targetUserId + " và hủy toàn bộ phiên đăng nhập.",
                keyword,
                statusFilter,
                warningOnly,
                principal,
                redirectAttributes
        );
    }

    /**
     * Mở khóa / kích hoạt lại tài khoản ({@link UserStatus#ACTIVE}) và phát thông báo hệ thống.
     */
    @PostMapping("/{id}/activate")
    public String activateUser(@PathVariable("id") Long targetUserId,
                               @RequestParam(value = "reason", required = false) String reason,
                               @RequestParam(value = "keyword", required = false) String keyword,
                               @RequestParam(value = "status", required = false) String statusFilter,
                               @RequestParam(value = "warningOnly", required = false) Boolean warningOnly,
                               Principal principal,
                               RedirectAttributes redirectAttributes) {
        return handleModeration(
                targetUserId,
                UserStatus.ACTIVE,
                reason,
                "Đã mở khóa và kích hoạt lại tài khoản #" + targetUserId + ".",
                keyword,
                statusFilter,
                warningOnly,
                principal,
                redirectAttributes
        );
    }

    private String handleModeration(Long targetUserId,
                                    UserStatus targetStatus,
                                    String reason,
                                    String successMessage,
                                    String keyword,
                                    String statusFilter,
                                    Boolean warningOnly,
                                    Principal principal,
                                    RedirectAttributes redirectAttributes) {
        try {
            Long adminId = resolveAdminId(principal);
            accountSecurityService.moderateUserStatus(targetUserId, adminId, targetStatus, reason);
            redirectAttributes.addFlashAttribute("successMessage", successMessage);
        } catch (BusinessRuleViolationException | ResourceNotFoundException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        } catch (OptimisticLockingFailureException ex) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Tài khoản vừa được cập nhật bởi thao tác khác, vui lòng tải lại và thử lại.");
        }

        preserveFilterAttributes(keyword, statusFilter, warningOnly, redirectAttributes);
        return "redirect:/admin/users";
    }

    private void preserveFilterAttributes(String keyword,
                                          String statusFilter,
                                          Boolean warningOnly,
                                          RedirectAttributes redirectAttributes) {
        if (keyword != null && !keyword.isBlank()) {
            redirectAttributes.addAttribute("keyword", keyword.trim());
        }
        if (statusFilter != null && !statusFilter.isBlank()) {
            redirectAttributes.addAttribute("status", statusFilter.trim());
        }
        if (Boolean.TRUE.equals(warningOnly)) {
            redirectAttributes.addAttribute("warningOnly", true);
        }
    }

    private Long resolveAdminId(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            throw new BusinessRuleViolationException("Phiên quản trị không hợp lệ.");
        }
        User admin = userQueryService.findByEmail(principal.getName())
                .orElseThrow(() -> new ResourceNotFoundException("Admin", principal.getName()));
        return admin.getId();
    }
}
