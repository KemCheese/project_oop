package com.yourapp.ticketpass.shared.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

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
                        // Tài nguyên tĩnh và file người dùng tải lên
                        .requestMatchers("/css/**", "/js/**", "/images/**", "/files/**").permitAll()
                        // Console H2 chỉ dùng khi phát triển
                        .requestMatchers("/h2-console/**").permitAll()
                        // Khu vực quản trị
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        // Còn lại bắt buộc đăng nhập
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .permitAll()
                )
                .logout(logout -> logout.permitAll());

        return http.build();
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
