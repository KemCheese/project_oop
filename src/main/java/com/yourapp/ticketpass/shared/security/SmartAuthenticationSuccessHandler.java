package com.yourapp.ticketpass.shared.security;

import java.io.IOException;

import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;

/**
 * Bộ xử lý chuyển hướng thông minh sau khi xác thực thành công.
 *
 * <p>Giải quyết triệt để bài toán điều hướng cho khách vãng lai:
 * <ol>
 *   <li>Ưu tiên 1: {@link SavedRequest} nếu người dùng bị chặn khi truy cập trang yêu cầu đăng nhập.</li>
 *   <li>Ưu tiên 2: Tham số request {@code redirect} được truyền vào form đăng nhập.</li>
 *   <li>Ưu tiên 3: Session attribute {@code REDIRECT_AFTER_AUTH} được lưu trước đó từ trang xem vé hoặc header Referer.</li>
 *   <li>Ưu tiên 4: Mặc định chuyển về sàn vé {@code /tickets}.</li>
 * </ol>
 * </p>
 *
 * <p><b>Bảo mật chống Open Redirect (CWE-601):</b> Chỉ chấp nhận relative URL hợp lệ bắt đầu bằng 1 dấu
 * gạch chéo {@code /}, không chứa ký tự điều khiển xuống dòng, không bắt đầu bằng {@code //} hoặc {@code /\\},
 * và không trỏ vào các trang xác thực/đăng xuất.</p>
 *
 * <p>Module: shared</p>
 * <p>Chủ sở hữu: Dev C</p>
 */
@Slf4j
public class SmartAuthenticationSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

    /** Tên session attribute dùng để lưu URL trang trước đó của khách vãng lai. */
    public static final String REDIRECT_ATTRIBUTE = "REDIRECT_AFTER_AUTH";

    private final RequestCache requestCache = new HttpSessionRequestCache();

    public SmartAuthenticationSuccessHandler(String defaultTargetUrl) {
        setDefaultTargetUrl(defaultTargetUrl);
        setAlwaysUseDefaultTargetUrl(false);
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) throws ServletException, IOException {

        // 1. Kiểm tra SavedRequest của Spring Security (nếu bị chặn bởi filter từ trước)
        SavedRequest savedRequest = requestCache.getRequest(request, response);
        if (savedRequest != null) {
            String redirectUrl = savedRequest.getRedirectUrl();
            if (isValidRedirect(redirectUrl)) {
                log.info("[SmartRedirect] Chuyển hướng theo SavedRequest: {}", redirectUrl);
                super.onAuthenticationSuccess(request, response, authentication);
                return;
            }
        }

        // 2. Kiểm tra tham số request 'redirect'
        String target = request.getParameter("redirect");

        // 3. Nếu không có, kiểm tra session attribute do UserController lưu
        HttpSession session = request.getSession(false);
        if ((target == null || target.isBlank()) && session != null) {
            target = (String) session.getAttribute(REDIRECT_ATTRIBUTE);
        }

        // Dọn dẹp session attribute sau khi đã đọc
        if (session != null) {
            session.removeAttribute(REDIRECT_ATTRIBUTE);
        }

        if (target != null && isValidRedirect(target)) {
            String sanitized = sanitizeRedirect(target);
            log.info("[SmartRedirect] Chuyển hướng người dùng về trang trước đó: {}", sanitized);
            clearAuthenticationAttributes(request);
            getRedirectStrategy().sendRedirect(request, response, sanitized);
            return;
        }

        // 4. Mặc định chuyển về trang defaultTargetUrl (/tickets)
        super.onAuthenticationSuccess(request, response, authentication);
    }

    /**
     * Kiểm tra tính an toàn của URL chuyển hướng chống Open Redirect (CWE-601).
     *
     * @param url URL cần kiểm tra
     * @return {@code true} nếu là relative URL an toàn, ngược lại {@code false}
     */
    public static boolean isValidRedirect(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        String trimmed = url.trim();

        // Chống CRLF injection (HTTP response splitting)
        if (trimmed.contains("\r") || trimmed.contains("\n")) {
            return false;
        }

        // Từ chối tuyệt đối bất kỳ URL tuyệt đối nào chứa scheme (http://, https://, javascript://...)
        if (trimmed.contains("://")) {
            return false;
        }

        // Bắt buộc là relative path bắt đầu bằng đúng 1 dấu '/'
        if (!trimmed.startsWith("/")) {
            return false;
        }

        // Chống protocol-relative URLs: //evil.com hoặc /\evil.com
        if (trimmed.startsWith("//") || trimmed.startsWith("/\\")) {
            return false;
        }

        // Không redirect vòng lặp vào các trang auth / logout / error
        if (trimmed.startsWith("/login") || trimmed.startsWith("/register")
                || trimmed.startsWith("/logout") || trimmed.startsWith("/error")) {
            return false;
        }

        return true;
    }

    /**
     * Chuẩn hoá chuỗi URL chuyển hướng an toàn.
     *
     * @param url URL đầu vào
     * @return URL sau khi chuẩn hoá
     */
    public static String sanitizeRedirect(String url) {
        if (!isValidRedirect(url)) {
            return "/tickets";
        }
        return url.trim();
    }
}
