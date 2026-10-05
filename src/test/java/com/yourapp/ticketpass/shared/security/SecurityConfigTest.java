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
    @DisplayName("GET /payment/momo/return - permitAll: không bị Spring Security chuyển hướng sang trang /login")
    void momoReturn_isPermittedAll_notRedirectedToLogin() throws Exception {
        // Thực hiện request giả lập MoMo redirect người dùng về
        mockMvc.perform(get("/payment/momo/return")
                        .param("partnerCode", "MOMO")
                        .param("orderId", "DEMO-123")
                        .param("resultCode", "0"))
                // Khẳng định: Không bị 302 chuyển hướng về /login
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    String redirectUrl = result.getResponse().getRedirectedUrl();
                    if (status == 302 && redirectUrl != null && redirectUrl.contains("/login")) {
                        throw new AssertionError("Yêu cầu bị chặn bởi Spring Security và redirect về /login: " + redirectUrl);
                    }
                });
    }

    @Test
    @DisplayName("POST /payment/momo/ipn - permitAll và miễn CSRF: không bị từ chối 401 Unauthorized hoặc 403 Forbidden")
    void momoIpn_isPermittedAll_andCsrfIgnored() throws Exception {
        mockMvc.perform(post("/payment/momo/ipn")
                        .contentType("application/json")
                        .content("{\"orderId\":\"DEMO-123\",\"resultCode\":0}"))
                // Không bị 401 hoặc 403 từ Spring Security
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    if (status == 401 || status == 403) {
                        throw new AssertionError("MoMo IPN bị chặn bởi Spring Security với HTTP " + status);
                    }
                });
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
