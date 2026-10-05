package com.yourapp.ticketpass.user.web;

import com.yourapp.ticketpass.shared.exception.ResourceNotFoundException;
import com.yourapp.ticketpass.user.application.StarredTicketUseCase;
import com.yourapp.ticketpass.user.application.UserQueryService;
import com.yourapp.ticketpass.user.domain.User;
import com.yourapp.ticketpass.user.web.dto.ToggleStarResponse;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API Controller phục vụ tương tác Đánh sao vé (Toggle Star) qua AJAX.
 *
 * <p>Endpoint: {@code POST /api/tickets/{ticketId}/star}</p>
 * <p>Module: user</p>
 * <p>Chủ sở hữu: Người C</p>
 */
@RestController
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
@Slf4j
public class StarredTicketApiController {

    private final StarredTicketUseCase starredTicketUseCase;
    private final UserQueryService userQueryService;

    /**
     * Bật/tắt trạng thái sao của vé cho người dùng hiện tại.
     *
     * @param ticketId  ID của vé cần thao tác
     * @param principal thông tin người dùng đăng nhập hiện tại
     * @return {@link ToggleStarResponse} chứa trạng thái sao mới và thông điệp
     */
    @PostMapping("/{ticketId}/star")
    public ResponseEntity<ToggleStarResponse> toggleStar(
            @PathVariable Long ticketId,
            Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ToggleStarResponse(false, ticketId, false, "Vui lòng đăng nhập để lưu vé này"));
        }

        User user = userQueryService.findByEmail(principal.getName())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng: " + principal.getName()));
        boolean starred = starredTicketUseCase.toggleStar(user.getId(), ticketId);
        String message = starred
                ? "Đã lưu vé vào danh sách theo dõi"
                : "Đã hủy lưu vé khỏi danh sách theo dõi";

        return ResponseEntity.ok(new ToggleStarResponse(true, ticketId, starred, message));
    }
}
