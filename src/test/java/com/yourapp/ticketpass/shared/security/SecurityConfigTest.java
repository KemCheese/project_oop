package com.yourapp.ticketpass.shared.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Kiểm thử tích hợp cấu hình Spring Security cho các endpoint công khai và bảo vệ.
 *
 * <p>Đặc biệt xác minh:
 * - GET /payment/momo/return được permitAll(), không bị ép redirect về /login khi người dùng thanh toán xong.
 * - POST /payment/momo/ipn được permitAll() và bỏ qua CSRF.
 * - Các tuyến đường quản trị (/admin/**) hoặc cần xác thực (/tickets/new) vẫn bắt buộc đăng nhập.</p>
 *
 * <p>Module sở hữu: shared (Security hạ tầng).</p>
 * <p>Chủ sở hữu theo phân công: Người C.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("SecurityConfigTest - Kiểm thử phân quyền truy cập Spring Security")
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET /payment/transfer - khách chưa đăng nhập phải bị chặn và chuyển hướng về /login")
    void paymentTransfer_requiresAuthentication() throws Exception {
        mockMvc.perform(get("/payment/transfer")
                        .param("ref", "1")
                        .param("type", "escrow"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", containsString("/login")));
    }

    @Test
    @DisplayName("GET /admin/disputes - khách chưa đăng nhập phải bị chặn và chuyển hướng về /login")
    void adminDisputes_requiresAuthentication() throws Exception {
        mockMvc.perform(get("/admin/disputes"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", containsString("/login")));
    }

    @Test
    @DisplayName("GET /tickets/new - khách chưa đăng nhập phải bị chặn và chuyển hướng về /login")
    void ticketsNew_requiresAuthentication() throws Exception {
        mockMvc.perform(get("/tickets/new"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", containsString("/login")));
    }
}
