package com.yourapp.ticketpass.shared.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filter hỗ trợ khởi tạo sớm (eager loading) CSRF Token cho kiến trúc render phía máy chủ (Thymeleaf SSR).
 *
 * <p><b>Vấn đề kỹ thuật được giải quyết:</b><br>
 * Trong Spring Security 6, {@link CsrfToken} mặc định sử dụng cơ chế lazy loading ({@code DeferredCsrfToken}).
 * Token chỉ được nạp thực sự khi có đối tượng truy cập (ví dụ khi Thymeleaf render đến thẻ {@code <form method="post">}).<br>
 * Nếu template giao diện có kích thước lớn (như file layout {@code base.html} chứa nhiều CSS tokens hoặc header),
 * bộ đệm phản hồi (Response Buffer mặc định 8KB của Tomcat) sẽ bị tràn và commit response về client trước.<br>
 * Khi Thymeleaf render đến thẻ form, việc nạp token lazy sẽ gọi {@code request.getSession(true)} để lưu token,
 * cố gắng ghi header {@code Set-Cookie: JSESSIONID}. Tuy nhiên do response đã commit, Tomcat sẽ ném ra ngoại lệ:
 * {@code java.lang.IllegalStateException: Cannot create a session after the response has been committed},
 * làm đứt gãy quá trình render HTML.</p>
 *
 * <p><b>Giải pháp:</b><br>
 * Filter này được cấu hình chạy ngay sau {@code CsrfFilter}. Tại thời điểm này, HTTP response headers vẫn hoàn toàn mở.
 * Filter chủ động lấy {@link CsrfToken} và gọi {@link CsrfToken#getToken()} để ép nạp token và thiết lập Session/Cookie
 * an toàn ngay từ đầu chuỗi filter, trước khi bất kỳ byte HTML nào của Thymeleaf được ghi ra response.</p>
 *
 * <p>Module sở hữu: shared (Security hạ tầng).</p>
 * <p>Chủ sở hữu theo phân công: Người C.</p>
 */
public class CsrfDeferredTokenInitFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (csrfToken != null) {
            // Ép nạp (eager load) CSRF token và tạo Session/Cookie nếu chưa có,
            // trước khi Response bị commit bởi Thymeleaf render HTML lớn.
            csrfToken.getToken();
        }

        filterChain.doFilter(request, response);
    }
}
