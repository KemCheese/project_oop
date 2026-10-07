package com.yourapp.ticketpass.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.io.IOException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.Authentication;

import jakarta.servlet.ServletException;

@DisplayName("Kiểm thử SmartAuthenticationSuccessHandler - Chuyển hướng thông minh sau đăng nhập")
class SmartAuthenticationSuccessHandlerTest {

    private SmartAuthenticationSuccessHandler handler;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private Authentication authentication;

    @BeforeEach
    void setUp() {
        handler = new SmartAuthenticationSuccessHandler("/tickets");
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        authentication = mock(Authentication.class);
    }

    @Nested
    @DisplayName("1. Kiểm tra tham số request redirect")
    class RequestParamTests {

        @Test
        @DisplayName("Có tham số redirect hợp lệ: chuyển hướng đúng về URL đó")
        void whenValidRedirectParam_shouldRedirectToParam() throws ServletException, IOException {
            request.setParameter("redirect", "/tickets/42");

            handler.onAuthenticationSuccess(request, response, authentication);

            assertThat(response.getRedirectedUrl()).isEqualTo("/tickets/42");
        }

        @Test
        @DisplayName("Tham số redirect là link độc hại bên ngoài: từ chối và về /tickets")
        void whenMaliciousRedirectParam_shouldFallbackToDefault() throws ServletException, IOException {
            request.setParameter("redirect", "https://evil.com/hack");

            handler.onAuthenticationSuccess(request, response, authentication);

            assertThat(response.getRedirectedUrl()).isEqualTo("/tickets");
        }

        @Test
        @DisplayName("Tham số redirect dạng protocol-relative (//evil.com): từ chối và về /tickets")
        void whenProtocolRelativeRedirect_shouldFallbackToDefault() throws ServletException, IOException {
            request.setParameter("redirect", "//evil.com");

            handler.onAuthenticationSuccess(request, response, authentication);

            assertThat(response.getRedirectedUrl()).isEqualTo("/tickets");
        }

        @Test
        @DisplayName("Tham số redirect trỏ vào trang /login: từ chối tránh loop")
        void whenRedirectToLogin_shouldFallbackToDefault() throws ServletException, IOException {
            request.setParameter("redirect", "/login");

            handler.onAuthenticationSuccess(request, response, authentication);

            assertThat(response.getRedirectedUrl()).isEqualTo("/tickets");
        }
    }

    @Nested
    @DisplayName("2. Kiểm tra Session attribute REDIRECT_AFTER_AUTH")
    class SessionAttributeTests {

        @Test
        @DisplayName("Có session attribute hợp lệ: chuyển hướng đúng và dọn dẹp attribute")
        void whenValidSessionAttribute_shouldRedirectAndClean() throws ServletException, IOException {
            MockHttpSession session = new MockHttpSession();
            session.setAttribute(SmartAuthenticationSuccessHandler.REDIRECT_ATTRIBUTE, "/reputation/top");
            request.setSession(session);

            handler.onAuthenticationSuccess(request, response, authentication);

            assertThat(response.getRedirectedUrl()).isEqualTo("/reputation/top");
            assertThat(session.getAttribute(SmartAuthenticationSuccessHandler.REDIRECT_ATTRIBUTE)).isNull();
        }

        @Test
        @DisplayName("Không có tham số nào: chuyển hướng về default /tickets")
        void whenNoRedirectInfo_shouldRedirectToDefault() throws ServletException, IOException {
            handler.onAuthenticationSuccess(request, response, authentication);

            assertThat(response.getRedirectedUrl()).isEqualTo("/tickets");
        }
    }

    @Nested
    @DisplayName("3. Kiểm tra hàm tĩnh isValidRedirect")
    class ValidationLogicTests {

        @Test
        @DisplayName("Relative URL hợp lệ")
        void testValidRelativePaths() {
            assertThat(SmartAuthenticationSuccessHandler.isValidRedirect("/")).isTrue();
            assertThat(SmartAuthenticationSuccessHandler.isValidRedirect("/tickets/123")).isTrue();
            assertThat(SmartAuthenticationSuccessHandler.isValidRedirect("/reputation/top?page=1")).isTrue();
        }

        @Test
        @DisplayName("URL không hợp lệ hoặc chứa mã độc")
        void testInvalidPaths() {
            assertThat(SmartAuthenticationSuccessHandler.isValidRedirect(null)).isFalse();
            assertThat(SmartAuthenticationSuccessHandler.isValidRedirect("")).isFalse();
            assertThat(SmartAuthenticationSuccessHandler.isValidRedirect("   ")).isFalse();
            assertThat(SmartAuthenticationSuccessHandler.isValidRedirect("http://attacker.com")).isFalse();
            assertThat(SmartAuthenticationSuccessHandler.isValidRedirect("//attacker.com")).isFalse();
            assertThat(SmartAuthenticationSuccessHandler.isValidRedirect("/\\attacker.com")).isFalse();
            assertThat(SmartAuthenticationSuccessHandler.isValidRedirect("/tickets\r\nSet-Cookie: evil")).isFalse();
            assertThat(SmartAuthenticationSuccessHandler.isValidRedirect("/login")).isFalse();
            assertThat(SmartAuthenticationSuccessHandler.isValidRedirect("/register")).isFalse();
            assertThat(SmartAuthenticationSuccessHandler.isValidRedirect("/logout")).isFalse();
        }
    }
}
