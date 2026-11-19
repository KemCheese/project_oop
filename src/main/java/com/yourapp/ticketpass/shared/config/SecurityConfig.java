package com.yourapp.ticketpass.shared.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import com.yourapp.ticketpass.shared.security.CsrfDeferredTokenInitFilter;
import com.yourapp.ticketpass.shared.security.SmartAuthenticationSuccessHandler;

/**
 * Cấu hình bảo mật của ứng dụng: ai được vào đường dẫn nào, đăng nhập/đăng xuất ra sao,
 * và cách băm mật khẩu.
 *
 * <p><b>Nguyên tắc 4 - lựa chọn cơ chế xác thực:</b> hệ thống dùng xác thực dựa trên
 * session (mặc định của Spring Security): sau khi đăng nhập, định danh người dùng được
 * giữ trong {@code HttpSession} của chính instance đang phục vụ. Cách này đủ tốt cho quy
 * mô đồ án - một instance duy nhất, giao diện render phía máy chủ bằng Thymeleaf, không
 * cần phục vụ ứng dụng di động hay client bên thứ ba.</p>
 *
 * <p><b>Đây là điểm cần đổi khi mở rộng (ghi rõ để bảo vệ đồ án):</b> nếu triển khai
 * scale ngang - chạy nhiều instance sau một load balancer - thì session nằm trong bộ nhớ
 * của từng instance sẽ không dùng chung được; người dùng bị đăng xuất ngẫu nhiên mỗi khi
 * request rơi vào instance khác. Khi đó có hai hướng xử lý:</p>
 * <ul>
 *   <li>Chuyển sang JWT stateless: máy chủ không giữ trạng thái, token tự mang định danh.
 *       Đổi lại phải tự lo việc thu hồi token.</li>
 *   <li>Hoặc giữ session nhưng đưa ra kho lưu trữ dùng chung (Spring Session + Redis),
 *       mọi instance đọc chung một nơi. Ít phải sửa code nghiệp vụ hơn.</li>
 * </ul>
 * <p>Cả hai hướng chỉ động vào lớp cấu hình này, không lan sang code nghiệp vụ.</p>
 *
 * <p>Module sở hữu: shared.</p>
 * <p>Chủ sở hữu theo phân công: Người C.</p>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Khai báo chuỗi bộ lọc bảo mật: phân quyền theo đường dẫn, trang đăng nhập và đăng xuất.
     *
     * @param http đối tượng cấu hình do Spring Security cung cấp
     * @return chuỗi bộ lọc đã dựng
     * @throws Exception khi cấu hình không hợp lệ
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        // Trang công khai: khách chưa đăng nhập vẫn xem được
                        .requestMatchers("/", "/register", "/login", "/error").permitAll()
                        // Form đăng vé mới của Dev A: bắt buộc đăng nhập (đặt TRƯỚC permitAll để không bị match nhầm bởi "/tickets/*")
                        .requestMatchers("/tickets/new").authenticated()
                        // Danh sách vé và chi tiết vé — marketplace mở cho guest duyệt (chỉ cho phép GET để bảo vệ POST /tickets tạo listing)
                        .requestMatchers(HttpMethod.GET, "/tickets", "/tickets/*").permitAll()
                        // Uy tín công khai — guest cần xem uy tín seller trước khi quyết định mua (chỉ GET)
                        .requestMatchers(HttpMethod.GET, "/reputation/**").permitAll()
                        // Tài nguyên tĩnh và file người dùng tải lên
                        .requestMatchers("/css/**", "/js/**", "/images/**", "/files/**").permitAll()
                        // Console H2 chỉ dùng khi phát triển
                        .requestMatchers("/h2-console/**").permitAll()
                        // Khu vực quản trị
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        // Ví điện tử và thanh toán ví: bắt buộc đăng nhập
                        .requestMatchers("/wallet/**", "/payment/wallet/**").authenticated()
                        // Còn lại bắt buộc đăng nhập
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .successHandler(new SmartAuthenticationSuccessHandler("/tickets"))
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutSuccessUrl("/")
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID")
                        .permitAll()
                )
                .sessionManagement(session -> session
                        .maximumSessions(-1)
                        .sessionRegistry(sessionRegistry())
                        .expiredUrl("/login?expired")
                )
                // Cho phép H2 Console hiển thị trong iframe (dev tool dùng <iframe>)
                .headers(headers -> headers
                        .frameOptions(frame -> frame.sameOrigin())
                )
                // Miễn CSRF cho H2 Console và API notifications (xác thực qua session)
                .csrf(csrf -> csrf
                        .ignoringRequestMatchers("/h2-console/**", "/api/notifications/**")
                )
                // Ép nạp sớm CSRF Token và khởi tạo Session trước khi Thymeleaf render HTML lớn vượt buffer Tomcat
                .addFilterAfter(new CsrfDeferredTokenInitFilter(), CsrfFilter.class);

        return http.build();
    }

    /**
     * Đăng ký bộ theo dõi các {@code HttpSession} đang hoạt động phục vụ thu hồi phiên tức thì
     * khi người dùng đổi mật khẩu hoặc bị Admin khóa tài khoản.
     *
     * @return {@link SessionRegistry} dùng chung toàn ứng dụng
     */
    @Bean
    public SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }

    /**
     * Đồng bộ vòng đời hủy session của Servlet Container với {@link SessionRegistry}.
     *
     * @return listener xuất sự kiện session lifecycle cho Spring Security
     */
    @Bean
    public HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }

    /**
     * Thuật toán băm mật khẩu dùng cho toàn hệ thống.
     *
     * <p>BCrypt tự sinh salt cho từng mật khẩu và có chi phí tính toán cố ý cao, nên
     * chống được tấn công dò mật khẩu hàng loạt.</p>
     *
     * @return bộ mã hoá mật khẩu
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
