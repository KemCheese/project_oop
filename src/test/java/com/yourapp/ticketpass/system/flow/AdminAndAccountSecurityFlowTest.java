package com.yourapp.ticketpass.system.flow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.yourapp.ticketpass.admin.application.AdminDashboardService;
import com.yourapp.ticketpass.admin.web.dto.AdminDashboardSummaryResponse;
import com.yourapp.ticketpass.notification.domain.Notification;
import com.yourapp.ticketpass.notification.domain.NotificationRepository;
import com.yourapp.ticketpass.notification.domain.NotificationType;
import com.yourapp.ticketpass.notification.infrastructure.NotificationJpaRepository;
import com.yourapp.ticketpass.payment.domain.Deposit;
import com.yourapp.ticketpass.payment.domain.DepositStatus;
import com.yourapp.ticketpass.payment.domain.EscrowStatus;
import com.yourapp.ticketpass.payment.domain.EscrowTransaction;
import com.yourapp.ticketpass.reputation.domain.UserReputationScore;
import com.yourapp.ticketpass.shared.exception.BusinessRuleViolationException;
import com.yourapp.ticketpass.system.base.AbstractSystemIntegrationTest;
import com.yourapp.ticketpass.ticket.domain.Ticket;
import com.yourapp.ticketpass.ticket.domain.TicketStatus;
import com.yourapp.ticketpass.ticket.domain.TicketType;
import com.yourapp.ticketpass.user.application.RegisterUserUseCase;
import com.yourapp.ticketpass.user.domain.User;
import com.yourapp.ticketpass.user.domain.UserRole;
import com.yourapp.ticketpass.user.domain.UserStatus;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

@DisplayName("System Integration Test - Admin Financial Dashboard, Password Security & User Moderation")
class AdminAndAccountSecurityFlowTest extends AbstractSystemIntegrationTest {

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private NotificationJpaRepository notificationJpaRepository;

    @Autowired
    private RegisterUserUseCase registerUserUseCase;

    @Autowired
    private AdminDashboardService adminDashboardService;

    @AfterEach
    void cleanupUserNotifications() {
        for (Long uid : createdUserIds) {
            List<Notification> notifs = notificationRepository.findRecentByUserId(uid, 100);
            notifs.forEach(n -> notificationJpaRepository.deleteById(n.getId()));
        }
    }

    private MockHttpSession loginAs(String email, String rawPassword) throws Exception {
        MvcResult result = mockMvc.perform(post("/login")
                        .param("username", email)
                        .param("password", rawPassword)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        assertThat(result.getResponse().getRedirectedUrl()).doesNotContain("error");
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertThat(session).isNotNull();
        return session;
    }

    @Test
    @DisplayName("RBAC & CSRF: Guest và ROLE_USER bị chặn khỏi /admin/**; POST thiếu CSRF bị 403")
    void rbacAndCsrf_ShouldBlockUnauthorizedAccessToAdminRoutes() throws Exception {
        MvcResult dashGuest = mockMvc.perform(get("/admin/dashboard"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        assertThat(dashGuest.getResponse().getRedirectedUrl()).endsWith("/login");

        MvcResult csvGuest = mockMvc.perform(get("/admin/dashboard/export.csv"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        assertThat(csvGuest.getResponse().getRedirectedUrl()).endsWith("/login");

        MvcResult usersGuest = mockMvc.perform(get("/admin/users"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        assertThat(usersGuest.getResponse().getRedirectedUrl()).endsWith("/login");

        String userEmail = "rbac-user-" + System.nanoTime() + "@test.com";
        createAndSaveUser(userEmail, "Pass1234", UserRole.USER);
        MockHttpSession userSession = loginAs(userEmail, "Pass1234");

        mockMvc.perform(get("/admin/dashboard").session(userSession))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/admin/dashboard/export.csv").session(userSession))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/admin/users").session(userSession))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/admin/users/999/ban")
                        .param("reason", "test access")
                        .session(userSession)
                        .with(csrf()))
                .andExpect(status().isForbidden());

        String adminEmail = "rbac-admin-" + System.nanoTime() + "@test.com";
        User admin = createAndSaveUser(adminEmail, "Admin1234", UserRole.ADMIN);
        MockHttpSession adminSession = loginAs(adminEmail, "Admin1234");

        mockMvc.perform(post("/admin/users/" + admin.getId() + "/ban")
                        .param("reason", "missing csrf")
                        .session(adminSession))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Đổi mật khẩu E2E: giữ nguyên phiên hiện tại, đá phiên khác về /login?expired và đăng nhập mật khẩu mới")
    void changePasswordE2E_ShouldKeepCurrentSessionExpireOtherSessionAndUpdatePassword() throws Exception {
        String email = "pwd-e2e-" + System.nanoTime() + "@test.com";
        createAndSaveUser(email, "OldPass123", UserRole.USER);

        MockHttpSession session1 = loginAs(email, "OldPass123");
        MockHttpSession session2 = loginAs(email, "OldPass123");
        assertThat(session1.getId()).isNotEqualTo(session2.getId());

        mockMvc.perform(get("/profile").session(session1)).andExpect(status().isOk());
        mockMvc.perform(get("/profile").session(session2)).andExpect(status().isOk());

        mockMvc.perform(post("/profile/password")
                        .session(session1)
                        .param("currentPassword", "OldPass123")
                        .param("newPassword", "NewPass456")
                        .param("confirmNewPassword", "NewPass456")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/profile?passwordUpdated#password-section"));

        mockMvc.perform(get("/profile").session(session1))
                .andExpect(status().isOk());

        mockMvc.perform(get("/profile").session(session2))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?expired"));

        mockMvc.perform(get("/login").param("expired", ""))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("hết hạn hoặc bị thu hồi")));

        mockMvc.perform(post("/login")
                        .param("username", email)
                        .param("password", "OldPass123")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error"));

        MockHttpSession session3 = loginAs(email, "NewPass456");
        mockMvc.perform(get("/profile").session(session3)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Admin Moderation E2E: cảnh báo rủi ro, khóa tài khoản đá văng phiên, chặn verifyEmail và mở khóa bắn thông báo")
    void adminUserModerationE2E_ShouldRevokeSessionBlockLoginAndNotifyOnReactivate() throws Exception {
        String adminEmail = "mod-admin-" + System.nanoTime() + "@test.com";
        User admin = createAndSaveUser(adminEmail, "Admin1234", UserRole.ADMIN);

        String targetEmail = "mod-target-" + System.nanoTime() + "@test.com";
        User target = createAndSaveUser(targetEmail, "User1234", UserRole.USER);

        UserReputationScore rep = reputationRepository.findByUserId(target.getId()).orElseThrow();
        rep.setScore(new BigDecimal("1.00"));
        rep.setDisputesLost(3);
        reputationRepository.save(rep);

        MockHttpSession adminSession = loginAs(adminEmail, "Admin1234");
        MockHttpSession targetSession = loginAs(targetEmail, "User1234");

        mockMvc.perform(get("/admin/users")
                        .param("warningOnly", "true")
                        .session(adminSession))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/users"))
                .andExpect(content().string(containsString(targetEmail)));

        mockMvc.perform(get("/admin/users")
                        .param("keyword", targetEmail)
                        .param("status", "ACTIVE")
                        .param("warningOnly", "true")
                        .session(adminSession))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/users"))
                .andExpect(content().string(containsString(targetEmail)));

        mockMvc.perform(post("/admin/users/" + admin.getId() + "/ban")
                        .param("reason", "Tự khóa thử nghiệm")
                        .session(adminSession)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("errorMessage"));

        mockMvc.perform(post("/admin/users/" + target.getId() + "/ban")
                        .param("reason", "abc")
                        .session(adminSession)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("errorMessage"));
        assertThat(userRepository.findById(target.getId()).orElseThrow().getStatus()).isEqualTo(UserStatus.ACTIVE);

        mockMvc.perform(post("/admin/users/" + target.getId() + "/ban")
                        .param("reason", "Nghi vấn bán vé trùng nhiều lần")
                        .session(adminSession)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("successMessage"));

        User locked = userRepository.findById(target.getId()).orElseThrow();
        assertThat(locked.getStatus()).isEqualTo(UserStatus.BANNED);
        assertThat(locked.isPermanentlyBanned()).isFalse();
        assertThat(locked.getModerationReason()).isEqualTo("Nghi vấn bán vé trùng nhiều lần");
        assertThat(locked.getModeratedByAdminId()).isEqualTo(admin.getId());

        mockMvc.perform(get("/profile").session(targetSession))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?expired"));

        mockMvc.perform(post("/login")
                        .param("username", targetEmail)
                        .param("password", "User1234")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error"));

        String token = registerUserUseCase.generateVerificationToken(target.getId(), targetEmail);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> registerUserUseCase.verifyEmail(target.getId(), token))
                .isInstanceOf(BusinessRuleViolationException.class);

        mockMvc.perform(post("/admin/users/" + target.getId() + "/activate")
                        .param("reason", "Đã đối soát hoàn tất")
                        .session(adminSession)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("successMessage"));

        User reactivated = userRepository.findById(target.getId()).orElseThrow();
        assertThat(reactivated.getStatus()).isEqualTo(UserStatus.ACTIVE);

        List<Notification> notifs = notificationRepository.findRecentByUserId(target.getId(), 10);
        assertThat(notifs).anyMatch(n -> n.getType() == NotificationType.SYSTEM
                && n.getTitle().contains("mở khóa"));

        MockHttpSession resumedSession = loginAs(targetEmail, "User1234");
        mockMvc.perform(get("/profile").session(resumedSession)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Admin Financial Dashboard & CSV E2E: khớp số liệu thực, lỗi ngày inline, UTF-8 BOM & anti-formula")
    void adminDashboardAndCsvE2E_ShouldAggregateRealDataAndSanitizeCsv() throws Exception {
        LocalDate today = LocalDate.now(AdminDashboardService.VIETNAM_ZONE);
        AdminDashboardSummaryResponse baseline = adminDashboardService.getDashboardSummary(today, today);

        String adminEmail = "dash-admin-" + System.nanoTime() + "@test.com";
        String sellerEmail = "dash-seller-" + System.nanoTime() + "@test.com";
        String buyerEmail = "dash-buyer-" + System.nanoTime() + "@test.com";
        createAndSaveUser(adminEmail, "Admin1234", UserRole.ADMIN);
        User seller = createAndSaveUser(sellerEmail, "Pass1234", UserRole.USER);
        User buyer = createAndSaveUser(buyerEmail, "Pass1234", UserRole.USER);

        Instant now = Instant.now();

        Ticket releasedTicket = createAndSaveTicket(
                seller.getId(), "=cmd|'/C calc'!A0 Vé Concert VIP", TicketType.CONCERT,
                new BigDecimal("1000000"), TicketStatus.AUTO_COMPLETE);
        releasedTicket.setBuyerId(buyer.getId());
        releasedTicket.setCompletedAt(now);
        ticketRepository.save(releasedTicket);

        EscrowTransaction releasedEscrow = createAndSaveEscrow(
                releasedTicket.getId(), buyer.getId(), new BigDecimal("1000000"), EscrowStatus.RELEASED);
        releasedEscrow.setTotalCommission(new BigDecimal("50000"));
        releasedEscrow.setTaxWithheld(new BigDecimal("15000"));
        releasedEscrow.setAmountHeld(new BigDecimal("1000000"));
        releasedEscrow.setReleasedAt(now);
        escrowTransactionRepository.save(releasedEscrow);

        Ticket forfeitTicket = createAndSaveTicket(
                seller.getId(), "Vé Phim Hỏng QR", TicketType.MOVIE,
                new BigDecimal("500000"), TicketStatus.RESOLVED_BUYER_WINS);
        forfeitTicket.setBuyerId(buyer.getId());
        forfeitTicket.setCompletedAt(now);
        ticketRepository.save(forfeitTicket);

        Deposit forfeitedDeposit = createAndSaveDeposit(
                forfeitTicket.getId(), seller.getId(), new BigDecimal("250000"), DepositStatus.FORFEITED);
        forfeitedDeposit.setFounderCut(new BigDecimal("35000"));
        forfeitedDeposit.setForfeitReason("+SUM(1,2) Phạt cọc");
        forfeitedDeposit.setForfeitedAt(now);
        depositRepository.save(forfeitedDeposit);

        Ticket heldTicket = createAndSaveTicket(
                seller.getId(), "Vé Xe Khách Đang Giao Dịch", TicketType.BUS,
                new BigDecimal("450000"), TicketStatus.DEPOSIT_LOCKED);
        heldTicket.setBuyerId(buyer.getId());
        ticketRepository.save(heldTicket);

        createAndSaveEscrow(heldTicket.getId(), buyer.getId(), new BigDecimal("450000"), EscrowStatus.HELD);

        MockHttpSession adminSession = loginAs(adminEmail, "Admin1234");

        MvcResult dashResult = mockMvc.perform(get("/admin/dashboard")
                        .param("fromDate", today.toString())
                        .param("toDate", today.toString())
                        .session(adminSession))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/dashboard"))
                .andExpect(model().attributeDoesNotExist("dateError"))
                .andReturn();

        AdminDashboardSummaryResponse actual =
                (AdminDashboardSummaryResponse) dashResult.getModelAndView().getModel().get("summary");
        assertThat(actual.getTotalGmv()).isEqualByComparingTo(baseline.getTotalGmv().add(new BigDecimal("1000000")));
        assertThat(actual.getTotalCommission()).isEqualByComparingTo(baseline.getTotalCommission().add(new BigDecimal("50000")));
        assertThat(actual.getTotalForfeitCut()).isEqualByComparingTo(baseline.getTotalForfeitCut().add(new BigDecimal("35000")));
        assertThat(actual.getTotalPlatformRevenue()).isEqualByComparingTo(baseline.getTotalPlatformRevenue().add(new BigDecimal("85000")));
        assertThat(actual.getTotalTaxWithheld()).isEqualByComparingTo(baseline.getTotalTaxWithheld().add(new BigDecimal("15000")));
        assertThat(actual.getLiveHeldEscrowAmount()).isEqualByComparingTo(baseline.getLiveHeldEscrowAmount().add(new BigDecimal("450000")));

        mockMvc.perform(get("/admin/dashboard")
                        .param("fromDate", "2026-12-31")
                        .param("toDate", "2026-01-01")
                        .session(adminSession))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/dashboard"))
                .andExpect(model().attributeExists("dateError"))
                .andExpect(content().string(containsString("Ngày bắt đầu không được lớn hơn ngày kết thúc")))
                .andExpect(content().string(not(containsString("Whitelabel Error Page"))));

        MvcResult csvResult = mockMvc.perform(get("/admin/dashboard/export.csv")
                        .param("fromDate", today.toString())
                        .param("toDate", today.toString())
                        .session(adminSession))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("text/csv")))
                .andExpect(header().string("Content-Type", containsString("UTF-8")))
                .andReturn();

        byte[] csvBytes = csvResult.getResponse().getContentAsByteArray();
        assertThat(csvBytes[0]).isEqualTo((byte) 0xEF);
        assertThat(csvBytes[1]).isEqualTo((byte) 0xBB);
        assertThat(csvBytes[2]).isEqualTo((byte) 0xBF);

        String csvText = new String(csvBytes, StandardCharsets.UTF_8);
        assertThat(csvText).contains("'=cmd|'/C calc'!A0 Vé Concert VIP");
        assertThat(csvText).contains("'+SUM(1,2) Phạt cọc");
        assertThat(csvText).contains("DEPOSIT_FORFEITED");
        assertThat(csvText).contains("Vé Phim Hỏng QR,MOVIE,0,35000,0");
    }
}
