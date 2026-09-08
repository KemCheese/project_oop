package com.yourapp.ticketpass.user.infrastructure;

import com.yourapp.ticketpass.user.domain.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Cầu nối giữa module {@code user} và Spring Security.
 *
 * <p>{@code shared.config.SecurityConfig} cấu hình {@code formLogin} với
 * {@code loginPage("/login")} và tự động dùng bean {@link UserDetailsService} này qua
 * auto-wiring. Đây là lớp duy nhất của module {@code user} phụ thuộc vào API xác thực của
 * Spring Security, nên nếu sau này đổi cơ chế đăng nhập thì chỉ sửa ở đây.</p>
 *
 * <p><b>Nguyên tắc 4:</b> hệ thống dùng session mặc định (in-memory) của Spring Security.
 * Cách này đủ cho đồ án chạy trên một tiến trình duy nhất; khi cần scale ngang (nhiều
 * instance sau load balancer) phải chuyển sang JWT stateless hoặc dùng session store chung
 * như Spring Session + Redis, vì session in-memory không chia sẻ được giữa các instance.</p>
 *
 * <p>Module: user</p>
 *
 * Chủ sở hữu: Người C
 */
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        throw new UnsupportedOperationException(
                "TODO [Người C] Prompt 2: gọi userRepository.findByEmail(email); nếu không tìm thấy "
                        + "thì ném UsernameNotFoundException. Nếu tìm thấy, dựng "
                        + "org.springframework.security.core.userdetails.User với username = email, "
                        + "password = passwordHash, authority = new SimpleGrantedAuthority(\"ROLE_\" + role.name()); "
                        + "chặn đăng nhập khi status != UserStatus.ACTIVE bằng cách đặt các cờ "
                        + "enabled/accountLocked tương ứng (PENDING_VERIFICATION và SUSPENDED -> disabled, "
                        + "BANNED -> locked) để Spring Security từ chối phiên đăng nhập.");
    }
}
