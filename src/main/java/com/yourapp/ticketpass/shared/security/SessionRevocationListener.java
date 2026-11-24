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

@Slf4j
@Component
@RequiredArgsConstructor
public class SessionRevocationListener {

    private final SessionRegistry sessionRegistry;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onPasswordChanged(UserPasswordChangedEvent event) {
        if (event == null || event.getUserEmail() == null) {
            return;
        }
        expireSessionsByEmail(event.getUserEmail(), event.getKeepSessionId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onAccountModerated(UserAccountModeratedEvent event) {
        if (event == null || event.getUserEmail() == null) {
            return;
        }
        String status = event.getTargetStatusName();
        if ("BANNED".equals(status)) {
            expireSessionsByEmail(event.getUserEmail(), null);
        }
    }

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
