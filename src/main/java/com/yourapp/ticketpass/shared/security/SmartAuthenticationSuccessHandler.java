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

@Slf4j
public class SmartAuthenticationSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

    public static final String REDIRECT_ATTRIBUTE = "REDIRECT_AFTER_AUTH";

    private final RequestCache requestCache = new HttpSessionRequestCache();

    public SmartAuthenticationSuccessHandler(String defaultTargetUrl) {
        setDefaultTargetUrl(defaultTargetUrl);
        setAlwaysUseDefaultTargetUrl(false);
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) throws ServletException, IOException {

        SavedRequest savedRequest = requestCache.getRequest(request, response);
        if (savedRequest != null) {
            String redirectUrl = savedRequest.getRedirectUrl();
            if (isValidRedirect(redirectUrl)) {
                log.info("[SmartRedirect] Chuyển hướng theo SavedRequest: {}", redirectUrl);
                super.onAuthenticationSuccess(request, response, authentication);
                return;
            }
        }

        String target = request.getParameter("redirect");

        HttpSession session = request.getSession(false);
        if ((target == null || target.isBlank()) && session != null) {
            target = (String) session.getAttribute(REDIRECT_ATTRIBUTE);
        }

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

        super.onAuthenticationSuccess(request, response, authentication);
    }

    public static boolean isValidRedirect(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        String trimmed = url.trim();

        if (trimmed.contains("\r") || trimmed.contains("\n")) {
            return false;
        }

        if (trimmed.contains("://")) {
            return false;
        }

        if (!trimmed.startsWith("/")) {
            return false;
        }

        if (trimmed.startsWith("//") || trimmed.startsWith("/\\")) {
            return false;
        }

        if (trimmed.startsWith("/login") || trimmed.startsWith("/register")
                || trimmed.startsWith("/logout") || trimmed.startsWith("/error")) {
            return false;
        }

        return true;
    }

    public static String sanitizeRedirect(String url) {
        if (!isValidRedirect(url)) {
            return "/tickets";
        }
        return url.trim();
    }
}
