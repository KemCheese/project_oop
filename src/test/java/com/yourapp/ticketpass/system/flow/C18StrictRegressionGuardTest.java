package com.yourapp.ticketpass.system.flow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yourapp.ticketpass.notification.domain.NotificationType;
import com.yourapp.ticketpass.system.base.AbstractSystemIntegrationTest;
import com.yourapp.ticketpass.ticket.application.TicketStateMachineService;
import com.yourapp.ticketpass.ticket.domain.Ticket;
import com.yourapp.ticketpass.ticket.domain.TicketStatus;
import com.yourapp.ticketpass.ticket.domain.TicketType;
import com.yourapp.ticketpass.user.application.AccountSecurityService;
import com.yourapp.ticketpass.user.domain.User;
import com.yourapp.ticketpass.user.domain.UserRole;
import com.yourapp.ticketpass.user.domain.UserStatus;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

@DisplayName("Hệ Thống - C18 Strict Regression Guards (Enum, DB Schema, HTTP 404/403, UI & Permanent Ban)")
class C18StrictRegressionGuardTest extends AbstractSystemIntegrationTest {

    @Autowired
    private TicketStateMachineService ticketStateMachineService;

    @Autowired
    private AccountSecurityService accountSecurityService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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
    @DisplayName("Guard 1 — Domain Enum & State Machine: không còn SUSPENDED, không còn TECHNICAL_ISSUE")
    void domainEnumsAndStateMachine_ExcludeRemovedStates() {
        assertThat(UserStatus.values()).containsExactlyInAnyOrder(
                UserStatus.PENDING_VERIFICATION,
                UserStatus.ACTIVE,
                UserStatus.BANNED
        );
        assertThat(Arrays.stream(TicketStatus.values()).map(Enum::name))
                .doesNotContain("TECHNICAL_ISSUE");
        assertThat(Arrays.stream(NotificationType.values()).map(Enum::name))
                .doesNotContain("TECHNICAL_ISSUE");

        assertThat(ticketStateMachineService.allowedNextStates(TicketStatus.TRANSFERRED))
                .isEqualTo(Set.of(TicketStatus.AWAITING_USE, TicketStatus.CANCELLED));
    }

    @Test
    @DisplayName("Guard 2 — Schema DB: không tồn tại bảng reputation_history và 2 cột technical_issue_* trên tickets")
    void databaseSchema_ExcludesReputationHistoryAndTechnicalIssueColumns() {
        Integer historyTableCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE LOWER(table_name) = 'reputation_history'",
                Integer.class);
        assertThat(historyTableCount).isZero();

        Integer techColsCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns "
                        + "WHERE LOWER(table_name) = 'tickets' "
                        + "AND LOWER(column_name) IN ('technical_issue_reason', 'technical_issue_reported_at')",
                Integer.class);
        assertThat(techColsCount).isZero();
    }

    @Test
    @DisplayName("Guard 3 — HTTP Endpoints: toàn bộ route /suspend, /technical-issue và /admin/technical-issues/** trả 404")
    void removedEndpoints_ReturnNotFoundEvenForAuthorizedSessions() throws Exception {
        String adminEmail = "c18-admin-" + System.nanoTime() + "@test.com";
        String buyerEmail = "c18-buyer-" + System.nanoTime() + "@test.com";
        String sellerEmail = "c18-seller-" + System.nanoTime() + "@test.com";
        createAndSaveUser(adminEmail, "Admin123@", UserRole.ADMIN);
        User buyer = createAndSaveUser(buyerEmail, "Pass1234@", UserRole.USER);
        User seller = createAndSaveUser(sellerEmail, "Pass1234@", UserRole.USER);

        Ticket ticket = createAndSaveTicket(seller.getId(), "Vé Test 404 Route", TicketType.MOVIE,
                new BigDecimal("200000.00"), TicketStatus.TRANSFERRED);
        ticket.setBuyerId(buyer.getId());
        ticketRepository.save(ticket);

        MockHttpSession adminSession = loginAs(adminEmail, "Admin123@");
        MockHttpSession buyerSession = loginAs(buyerEmail, "Pass1234@");

        mockMvc.perform(post("/admin/users/" + seller.getId() + "/suspend")
                        .param("reason", "Thử route tạm khóa đã xóa")
                        .session(adminSession)
                        .with(csrf()))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/tickets/" + ticket.getId() + "/technical-issue")
                        .param("reason", "Thử route báo lỗi đã xóa")
                        .session(buyerSession)
                        .with(csrf()))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/admin/technical-issues/" + ticket.getId() + "/reopen")
                        .param("note", "Thử route admin kỹ thuật đã xóa")
                        .session(adminSession)
                        .with(csrf()))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/admin/technical-issues/" + ticket.getId() + "/continue")
                        .param("note", "Thử route admin kỹ thuật đã xóa")
                        .session(adminSession)
                        .with(csrf()))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/admin/technical-issues/" + ticket.getId() + "/cancel-refund")
                        .param("note", "Thử route admin kỹ thuật đã xóa")
                        .session(adminSession)
                        .with(csrf()))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/admin/users/" + seller.getId() + "/ban")
                        .param("reason", "Người dùng thường thử khóa")
                        .session(buyerSession)
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Guard 4 — Giao diện HTML: kiểm tra đồng bộ nhãn Khóa, bỏ Nhật ký uy tín và bỏ Lỗi kỹ thuật QR")
    void uiTemplates_ReflectAllThreeRemovalsCleanly() throws Exception {
        String adminEmail = "c18-ui-admin-" + System.nanoTime() + "@test.com";
        String sellerEmail = "c18-ui-seller-" + System.nanoTime() + "@test.com";
        String buyerEmail = "c18-ui-buyer-" + System.nanoTime() + "@test.com";
        createAndSaveUser(adminEmail, "Admin123@", UserRole.ADMIN);
        User seller = createAndSaveUser(sellerEmail, "Pass1234@", UserRole.USER);
        User buyer = createAndSaveUser(buyerEmail, "Pass1234@", UserRole.USER);

        Ticket lostTicket = createAndSaveTicket(seller.getId(), "Vé Thua Tranh Chấp UI", TicketType.MOVIE,
                new BigDecimal("180000.00"), TicketStatus.RESOLVED_BUYER_WINS);
        lostTicket.setBuyerId(buyer.getId());
        ticketRepository.save(lostTicket);

        Ticket transferredTicket = createAndSaveTicket(seller.getId(), "Vé Chờ Xác Nhận UI", TicketType.CONCERT,
                new BigDecimal("350000.00"), TicketStatus.TRANSFERRED);
        transferredTicket.setBuyerId(buyer.getId());
        ticketRepository.save(transferredTicket);

        MockHttpSession adminSession = loginAs(adminEmail, "Admin123@");
        MockHttpSession buyerSession = loginAs(buyerEmail, "Pass1234@");

        mockMvc.perform(get("/admin/users").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Khóa")))
                .andExpect(content().string(not(containsString("Tạm khóa"))))
                .andExpect(content().string(not(containsString("Cấm vĩnh viễn"))))
                .andExpect(content().string(not(containsString("SUSPENDED"))));

        mockMvc.perform(get("/reputation/" + seller.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Nhật Ký Biến Động"))))
                .andExpect(content().string(containsString("Lịch Sử Giao Dịch")))
                .andExpect(content().string(containsString("Thua tranh chấp")));

        mockMvc.perform(get("/tickets/" + transferredTicket.getId()).session(buyerSession))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Xác nhận vé hợp lệ")))
                .andExpect(content().string(not(containsString("Báo lỗi vé không hợp lệ"))))
                .andExpect(content().string(not(containsString("reportIssueModal"))));

        mockMvc.perform(get("/admin/disputes").param("tab", "technical-issues").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Quản Trị Khiếu Nại")))
                .andExpect(content().string(not(containsString("Lỗi kỹ thuật QR"))))
                .andExpect(content().string(not(containsString("TECHNICAL_ISSUE"))));
    }

    @Test
    @DisplayName("Guard 5 — Phân biệt Khóa thủ công (mở được) vs Khóa vĩnh viễn 0đ (chặn tuyệt đối Admin mở khóa qua HTTP)")
    void manualLockCanUnlock_WhileZeroReputationPermanentBanCannotBeUnlockedViaHttp() throws Exception {
        String adminEmail = "c18-ban-admin-" + System.nanoTime() + "@test.com";
        String manualEmail = "c18-manual-" + System.nanoTime() + "@test.com";
        String permEmail = "c18-perm-" + System.nanoTime() + "@test.com";
        createAndSaveUser(adminEmail, "Admin123@", UserRole.ADMIN);
        User manualTarget = createAndSaveUser(manualEmail, "Pass1234@", UserRole.USER);
        User permTarget = createAndSaveUser(permEmail, "Pass1234@", UserRole.USER);

        MockHttpSession adminSession = loginAs(adminEmail, "Admin123@");
        MockHttpSession manualSession = loginAs(manualEmail, "Pass1234@");

        mockMvc.perform(post("/admin/users/" + manualTarget.getId() + "/ban")
                        .param("reason", "Vi phạm quy chế đăng vé lặp lại")
                        .session(adminSession)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("successMessage"));

        User afterLock = userRepository.findById(manualTarget.getId()).orElseThrow();
        assertThat(afterLock.getStatus()).isEqualTo(UserStatus.BANNED);
        assertThat(afterLock.isPermanentlyBanned()).isFalse();

        mockMvc.perform(get("/profile").session(manualSession))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(post("/admin/users/" + manualTarget.getId() + "/activate")
                        .param("reason", "Đã khắc phục vi phạm")
                        .session(adminSession)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("successMessage"));

        assertThat(userRepository.findById(manualTarget.getId()).orElseThrow().getStatus())
                .isEqualTo(UserStatus.ACTIVE);

        accountSecurityService.permanentlyBanForZeroReputation(permTarget.getId(), "Điểm uy tín giảm về 0");

        mockMvc.perform(post("/admin/users/" + permTarget.getId() + "/activate")
                        .param("reason", "Admin cố tình mở khóa tài khoản 0đ")
                        .session(adminSession)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("errorMessage"));

        User afterAttempt = userRepository.findById(permTarget.getId()).orElseThrow();
        assertThat(afterAttempt.getStatus()).isEqualTo(UserStatus.BANNED);
        assertThat(afterAttempt.isPermanentlyBanned()).isTrue();
    }
}
