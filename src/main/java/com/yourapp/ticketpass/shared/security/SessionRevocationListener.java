package com.yourapp.ticketpass.shared.security;

import com.yourapp.ticketpass.shared.event.UserAccountModeratedEvent;
import com.yourapp.ticketpass.shared.event.UserPasswordChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Listener lắng nghe sự kiện bảo mật ở phase {@code AFTER_COMMIT} để thu hồi
 * {@code HttpSession} đang mở trong {@link SessionRegistry}.
 *
 * <p>Chạy sau khi transaction đã commit thành công giúp tránh lỗi bị đăng xuất nhầm
 * nếu transaction đổi mật khẩu hoặc kiểm duyệt gặp {@code OptimisticLockingFailureException}
 * rồi rollback, đồng thời tách hoàn toàn tầng {@code user.application} khỏi hạ tầng
 * Spring Security Session.</p>
 *
 * <p>Module: shared</p>
 * <p>Chủ sở hữu theo phân công: Người C (Dev C)</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SessionRevocationListener {

    private final SessionRegistry sessionRegistry;

    /**
     * Thu hồi các phiên đăng nhập khác sau khi người dùng đổi mật khẩu thành công.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onPasswordChanged(UserPasswordChangedEvent event) {
        if (event == null || event.getUserEmail() == null) {
            return;
        }
        expireSessionsByEmail(event.getUserEmail(), event.getKeepSessionId());
    }

    /**
     * Thu hồi toàn bộ phiên đăng nhập khi tài khoản bị Admin chuyển sang SUSPENDED hoặc BANNED.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onAccountModerated(UserAccountModeratedEvent event) {
        if (event == null || event.getUserEmail() == null) {
            return;
        }
        String status = event.getTargetStatusName();
        if ("SUSPENDED".equals(status) || "BANNED".equals(status)) {
            expireSessionsByEmail(event.getUserEmail(), null);
        }
    }

    /**
     * Hủy toàn bộ phiên đăng nhập đang hoạt động của một email trong {@link SessionRegistry},
     * ngoại trừ {@code excludeSessionId} (nếu được cung cấp).
     *
     * <p>Duyệt {@code getAllPrincipals()} và đối chiếu {@link UserDetails#getUsername()} thay vì
     * truyền chuỗi email vào {@code getAllSessions} vì key nội bộ của {@code SessionRegistryImpl}
     * là đối tượng {@link UserDetails}.</p>
     *
     * @param email email đăng nhập của tài khoản
     * @param excludeSessionId session ID cần giữ lại (hoặc null nếu hủy toàn bộ)
     */
    public void expireSessionsByEmail(String email, String excludeSessionId) {
        if (email == null || email.isBlank() || sessionRegistry == null) {
            return;
        }
        for (Object principal : sessionRegistry.getAllPrincipals()) {
            if (principal instanceof UserDetails userDetails
                    && userDetails.getUsername() != null
                    && userDetails.getUsername().equalsIgnoreCase(email)) {
                for (SessionInformation info : sessionRegistry.getAllSessions(principal, false)) {
                    if (excludeSessionId != null && excludeSessionId.equals(info.getSessionId())) {
                        continue;
                    }
                    info.expireNow();
                }
            }
        }
    }
}
