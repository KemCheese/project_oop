package com.yourapp.ticketpass.user.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yourapp.ticketpass.user.application.StarredTicketUseCase;
import com.yourapp.ticketpass.user.application.UserQueryService;
import com.yourapp.ticketpass.user.domain.User;
import com.yourapp.ticketpass.user.domain.UserRole;
import com.yourapp.ticketpass.user.domain.UserStatus;
import com.yourapp.ticketpass.user.web.dto.ToggleStarResponse;
import java.security.Principal;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
@DisplayName("StarredTicketApiController Unit Tests")
class StarredTicketApiControllerTest {

    @Mock
    private StarredTicketUseCase starredTicketUseCase;

    @Mock
    private UserQueryService userQueryService;

    @Mock
    private Principal principal;

    @InjectMocks
    private StarredTicketApiController controller;

    private User buildUser(Long id, String email) {
        User u = new User();
        u.setId(id);
        u.setEmail(email);
        u.setFullName("Test User");
        u.setRole(UserRole.USER);
        u.setStatus(UserStatus.ACTIVE);
        return u;
    }

    @Test
    @DisplayName("Trả về 401 UNAUTHORIZED nếu người dùng chưa đăng nhập (Principal null)")
    void toggleStar_PrincipalNull_ReturnsUnauthorized() {
        ResponseEntity<ToggleStarResponse> response = controller.toggleStar(10L, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isFalse();
        assertThat(response.getBody().message()).contains("Vui lòng đăng nhập");
    }

    @Test
    @DisplayName("Đánh sao thành công: trả về 200 OK với starred = true")
    void toggleStar_Success_StarredTrue() {
        when(principal.getName()).thenReturn("buyer@test.com");
        User user = buildUser(1L, "buyer@test.com");
        when(userQueryService.findByEmail("buyer@test.com")).thenReturn(Optional.of(user));
        when(starredTicketUseCase.toggleStar(1L, 10L)).thenReturn(true);

        ResponseEntity<ToggleStarResponse> response = controller.toggleStar(10L, principal);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isTrue();
        assertThat(response.getBody().ticketId()).isEqualTo(10L);
        assertThat(response.getBody().starred()).isTrue();
        assertThat(response.getBody().message()).contains("Đã lưu vé vào danh sách theo dõi");

        verify(starredTicketUseCase).toggleStar(1L, 10L);
    }

    @Test
    @DisplayName("Hủy sao thành công: trả về 200 OK với starred = false")
    void toggleStar_Success_StarredFalse() {
        when(principal.getName()).thenReturn("buyer@test.com");
        User user = buildUser(1L, "buyer@test.com");
        when(userQueryService.findByEmail("buyer@test.com")).thenReturn(Optional.of(user));
        when(starredTicketUseCase.toggleStar(1L, 10L)).thenReturn(false);

        ResponseEntity<ToggleStarResponse> response = controller.toggleStar(10L, principal);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isTrue();
        assertThat(response.getBody().ticketId()).isEqualTo(10L);
        assertThat(response.getBody().starred()).isFalse();
        assertThat(response.getBody().message()).contains("Đã hủy lưu vé khỏi danh sách theo dõi");

        verify(starredTicketUseCase).toggleStar(1L, 10L);
    }
}
