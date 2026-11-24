package com.yourapp.ticketpass.shared.security;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yourapp.ticketpass.shared.event.UserAccountModeratedEvent;
import com.yourapp.ticketpass.shared.event.UserPasswordChangedEvent;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.UserDetails;

@ExtendWith(MockitoExtension.class)
@DisplayName("Unit Test - SessionRevocationListener (Thu hồi session AFTER_COMMIT)")
class SessionRevocationListenerTest {

        @Mock
        private SessionRegistry sessionRegistry;

        private SessionRevocationListener listener;

        @BeforeEach
        void setUp() {
                listener = new SessionRevocationListener(sessionRegistry);
        }

        @Test
        @DisplayName("onPasswordChanged: giữ nguyên keepSessionId và expireNow các phiên khác khớp UserDetails")
        void onPasswordChanged_ShouldKeepCurrentSessionAndExpireOthers() {
                UserDetails alicePrincipal = org.springframework.security.core.userdetails.User
                                .withUsername("ALICE@ticketpass.vn").password("x").roles("USER").build();
                UserDetails bobPrincipal = org.springframework.security.core.userdetails.User
                                .withUsername("bob@ticketpass.vn").password("x").roles("USER").build();

                SessionInformation current = mock(SessionInformation.class);
                when(current.getSessionId()).thenReturn("sess-1");
                SessionInformation other = mock(SessionInformation.class);
                when(other.getSessionId()).thenReturn("sess-2");

                when(sessionRegistry.getAllPrincipals()).thenReturn(List.of(alicePrincipal, bobPrincipal));
                when(sessionRegistry.getAllSessions(alicePrincipal, false)).thenReturn(List.of(current, other));

                listener.onPasswordChanged(
                                new UserPasswordChangedEvent(10L, "alice@ticketpass.vn", "sess-1", Instant.now()));

                verify(current, never()).expireNow();
                verify(other).expireNow();
                verify(sessionRegistry, never()).getAllSessions(bobPrincipal, false);
        }

        @Test
        @DisplayName("onAccountModerated: chỉ BANNED hủy toàn bộ session; ACTIVE không hủy")
        void onAccountModerated_ShouldExpireAllSessionsOnBanOnly() {
                UserDetails fraudPrincipal = org.springframework.security.core.userdetails.User
                                .withUsername("fraud@ticketpass.vn").password("x").roles("USER").build();
                SessionInformation s1 = mock(SessionInformation.class);

                when(sessionRegistry.getAllPrincipals()).thenReturn(List.of(fraudPrincipal));
                when(sessionRegistry.getAllSessions(fraudPrincipal, false)).thenReturn(List.of(s1));

                listener.onAccountModerated(new UserAccountModeratedEvent(
                                10L, "fraud@ticketpass.vn", 1L, "BANNED", "Gian lận", Instant.now()));
                verify(s1).expireNow();

                SessionRegistry untouched = mock(SessionRegistry.class);
                SessionRevocationListener activeListener = new SessionRevocationListener(untouched);
                activeListener.onAccountModerated(new UserAccountModeratedEvent(
                                10L, "fraud@ticketpass.vn", 1L, "ACTIVE", "Mở khóa", Instant.now()));
                verify(untouched, never()).getAllPrincipals();
        }
}
