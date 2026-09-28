package com.yourapp.ticketpass.user.infrastructure;

import com.yourapp.ticketpass.user.domain.User;
import com.yourapp.ticketpass.user.domain.UserRepository;
import com.yourapp.ticketpass.user.domain.UserStatus;
import java.util.Collections;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
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

    /**
     * Nạp thông tin người dùng từ database theo email để Spring Security xác thực.
     *
     * <p>Luồng hoạt động:</p>
     * <ol>
     *   <li>Tra cứu {@code User} qua {@link UserRepository#findByEmail(String)}.</li>
     *   <li>Nếu không tìm thấy → ném {@link UsernameNotFoundException}.</li>
     *   <li>Ánh xạ {@link UserStatus} sang các cờ bảo mật của Spring Security:
     *       <ul>
     *         <li>{@code ACTIVE} → enabled=true, accountNonLocked=true → cho phép đăng nhập.</li>
     *         <li>{@code PENDING_VERIFICATION}, {@code SUSPENDED} → enabled=false → Spring từ chối
     *             với {@code DisabledException}.</li>
     *         <li>{@code BANNED} → accountNonLocked=false → Spring từ chối
     *             với {@code LockedException}.</li>
     *       </ul>
     *   </li>
     *   <li>Gắn authority {@code "ROLE_" + role.name()} để {@code SecurityConfig} phân quyền
     *       bằng {@code hasRole("ADMIN")}.</li>
     * </ol>
     *
     * @param email email đăng nhập (Spring Security truyền giá trị từ field "username" của form)
     * @return đối tượng {@link UserDetails} chứa thông tin xác thực
     * @throws UsernameNotFoundException nếu không tìm thấy tài khoản với email này
     */
    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        // 1. Tra cứu người dùng qua domain repository (Repository Pattern — không gọi JPA trực tiếp)
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "Không tìm thấy tài khoản với email: " + email));

        // 2. Ánh xạ UserStatus sang cờ bảo mật của Spring Security
        //    - ACTIVE          → enabled=true,  accountNonLocked=true  (đăng nhập bình thường)
        //    - PENDING / SUSPENDED → enabled=false, accountNonLocked=true  (DisabledException)
        //    - BANNED          → enabled=true,  accountNonLocked=false (LockedException)
        UserStatus status = user.getStatus();
        boolean enabled = (status == UserStatus.ACTIVE);
        boolean accountNonLocked = (status != UserStatus.BANNED);

        // 3. Dựng UserDetails với authority chuẩn "ROLE_" + role.name()
        //    SecurityConfig dùng hasRole("ADMIN") nên authority phải có prefix "ROLE_"
        return new org.springframework.security.core.userdetails.User(
                user.getEmail(),
                user.getPasswordHash(),
                enabled,                          // enabled
                true,                             // accountNonExpired (không dùng)
                true,                             // credentialsNonExpired (không dùng)
                accountNonLocked,                 // accountNonLocked
                Collections.singletonList(
                        new SimpleGrantedAuthority("ROLE_" + user.getRole().name())
                )
        );
    }
}
