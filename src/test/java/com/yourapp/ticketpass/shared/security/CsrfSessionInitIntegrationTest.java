package com.yourapp.ticketpass.shared.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Kiểm thử tích hợp xác minh tính năng khởi tạo sớm CSRF Token và Session.
 *
 * <p>Kiểm tra kịch bản khách vãng lai (Anonymous / Incognito) truy cập trực tiếp
 * vào các trang chứa form POST (như /login và /register). Đảm bảo giao diện Thymeleaf
 * render trọn vẹn toàn bộ thẻ form và input hidden CSRF mà không gặp ngoại lệ
 * {@code IllegalStateException: Cannot create a session after the response has been committed}.</p>
 *
 * <p>Module sở hữu: shared (Security hạ tầng).</p>
 * <p>Chủ sở hữu theo phân công: Người C.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("CsrfSessionInitIntegrationTest - Kiểm thử khởi tạo sớm CSRF Token & Session")
class CsrfSessionInitIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET /login không có session trước - render trọn vẹn form đăng nhập và input _csrf")
    void getLogin_withoutSession_rendersSuccessfully() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("action=\"/login\"")))
                .andExpect(content().string(containsString("_csrf")))
                .andExpect(content().string(containsString("name=\"username\"")))
                .andExpect(content().string(containsString("name=\"password\"")));
    }

    @Test
    @DisplayName("GET /register không có session trước - render trọn vẹn form đăng ký và input _csrf")
    void getRegister_withoutSession_rendersSuccessfully() throws Exception {
        mockMvc.perform(get("/register"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("action=\"/register\"")))
                .andExpect(content().string(containsString("_csrf")))
                .andExpect(content().string(containsString("id=\"email\"")))
                .andExpect(content().string(containsString("id=\"password\"")));
    }
}
