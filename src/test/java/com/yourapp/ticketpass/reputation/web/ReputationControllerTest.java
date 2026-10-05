package com.yourapp.ticketpass.reputation.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yourapp.ticketpass.reputation.application.UpdateReputationUseCase;
import com.yourapp.ticketpass.reputation.domain.ReputationRepository;
import com.yourapp.ticketpass.reputation.domain.UserReputationScore;
import com.yourapp.ticketpass.reputation.web.dto.ReputationResponse;
import com.yourapp.ticketpass.user.application.UserQueryService;
import com.yourapp.ticketpass.user.domain.User;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;

/**
 * Unit test cho {@link ReputationController} — Task C9 (cập nhật tích hợp fullName từ UserQueryService).
 *
 * <p>Dùng Mockito thuần ({@code @ExtendWith(MockitoExtension.class)} + {@code @Mock})
 * thay vì {@code @WebMvcTest} + {@code @MockBean}, consistent với style test hiện có
 * trong dự án (xem {@code UpdateReputationUseCaseTest}).</p>
 *
 * <p>Test trực tiếp gọi method controller, kiểm tra: view name, model attributes,
 * mapping họ tên người bán (bao gồm cả fallback khi User không tồn tại hoặc tên rỗng)
 * và tương tác đúng với dependencies.</p>
 *
 * <p>Module: reputation</p>
 *
 * Chủ sở hữu: Người C
 */
@ExtendWith(MockitoExtension.class)
class ReputationControllerTest {

    @Mock
    private ReputationRepository reputationRepository;

    @Mock
    private UpdateReputationUseCase updateReputationUseCase;

    @Mock
    private UserQueryService userQueryService;

    @InjectMocks
    private ReputationController controller;

    // ────────────────────────────────────────────────────────────────
    // Helper: tạo entity mẫu
    // ────────────────────────────────────────────────────────────────

    private UserReputationScore buildScore(Long userId, String score, int totalTx, int successTx,
                                           int disputesWon, int disputesLost, String gmv) {
        UserReputationScore s = new UserReputationScore();
        s.setId(userId);
        s.setUserId(userId);
        s.setScore(new BigDecimal(score));
        s.setTotalTransactions(totalTx);
        s.setSuccessfulTransactions(successTx);
        s.setDisputesWon(disputesWon);
        s.setDisputesLost(disputesLost);
        s.setTotalGmv(new BigDecimal(gmv).setScale(2, RoundingMode.HALF_UP));
        s.setLastTransactionAt(Instant.parse("2026-09-29T10:00:00Z"));
        s.setCancelledListings(0);
        s.setDisputesOpenedAgainst(0);
        return s;
    }

    private User buildUser(Long id, String fullName) {
        User user = new User();
        user.setId(id);
        user.setFullName(fullName);
        return user;
    }

    // ────────────────────────────────────────────────────────────────
    // GET /reputation/{userId}
    // ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /reputation/{userId}")
    class DetailTests {

        @Test
        @DisplayName("User có sẵn bản ghi uy tín và thông tin User → trả view reputation/detail với score và fullName đúng")
        void detail_existingUser_returnsView() {
            UserReputationScore score = buildScore(1L, "85.00", 10, 8, 1, 1, "5000000");
            when(updateReputationUseCase.getOrCreate(1L)).thenReturn(score);
            when(userQueryService.findById(1L)).thenReturn(Optional.of(buildUser(1L, "Nguyễn Văn A")));

            Model model = new ConcurrentModel();
            String viewName = controller.detail(1L, model);

            assertThat(viewName).isEqualTo("reputation/detail");
            assertThat(model.containsAttribute("reputation")).isTrue();

            ReputationResponse response = (ReputationResponse) model.getAttribute("reputation");
            assertThat(response).isNotNull();
            assertThat(response.getUserId()).isEqualTo(1L);
            assertThat(response.getFullName()).isEqualTo("Nguyễn Văn A");
            assertThat(response.getScore()).isEqualByComparingTo(new BigDecimal("85.00"));
            assertThat(response.getTotalTransactions()).isEqualTo(10);
            assertThat(response.getSuccessfulTransactions()).isEqualTo(8);
            assertThat(response.getDisputesWon()).isEqualTo(1);
            assertThat(response.getDisputesLost()).isEqualTo(1);
            assertThat(response.getLastTransactionAt()).isNotNull();

            verify(updateReputationUseCase).getOrCreate(1L);
            verify(userQueryService).findById(1L);
        }

        @Test
        @DisplayName("User chưa có bản ghi → getOrCreate tạo mặc định 100.00, không tìm thấy User → fallback tên mặc định")
        void detail_newUser_createsDefaultScore() {
            UserReputationScore defaultScore = buildScore(999L, "100.00", 0, 0, 0, 0, "0");
            when(updateReputationUseCase.getOrCreate(999L)).thenReturn(defaultScore);
            when(userQueryService.findById(999L)).thenReturn(Optional.empty());

            Model model = new ConcurrentModel();
            String viewName = controller.detail(999L, model);

            assertThat(viewName).isEqualTo("reputation/detail");
            ReputationResponse response = (ReputationResponse) model.getAttribute("reputation");
            assertThat(response).isNotNull();
            assertThat(response.getUserId()).isEqualTo(999L);
            assertThat(response.getFullName()).isEqualTo("Thành viên #999");
            assertThat(response.getScore()).isEqualByComparingTo(new BigDecimal("100.00"));
            assertThat(response.getTotalTransactions()).isEqualTo(0);

            verify(updateReputationUseCase).getOrCreate(999L);
            verify(userQueryService).findById(999L);
        }

        @Test
        @DisplayName("User có tồn tại nhưng fullName null → gán fallback 'Thành viên #id'")
        void detail_userWithNullFullName_setsFallbackFullName() {
            UserReputationScore score = buildScore(2L, "90.00", 5, 5, 0, 0, "1000000");
            when(updateReputationUseCase.getOrCreate(2L)).thenReturn(score);
            when(userQueryService.findById(2L)).thenReturn(Optional.of(buildUser(2L, null)));

            Model model = new ConcurrentModel();
            controller.detail(2L, model);

            ReputationResponse response = (ReputationResponse) model.getAttribute("reputation");
            assertThat(response).isNotNull();
            assertThat(response.getFullName()).isEqualTo("Thành viên #2");
        }
    }

    // ────────────────────────────────────────────────────────────────
    // GET /reputation/top
    // ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /reputation/top")
    class TopSellersTests {

        @Test
        @DisplayName("Không truyền limit → mặc định 10, trả view reputation/top kèm fullName người bán")
        void topSellers_defaultLimit() {
            List<UserReputationScore> scores = List.of(
                    buildScore(1L, "98.00", 50, 48, 2, 0, "25000000"),
                    buildScore(2L, "95.00", 30, 28, 1, 1, "15000000")
            );
            when(reputationRepository.findTopSellers(10)).thenReturn(scores);
            when(userQueryService.findById(1L)).thenReturn(Optional.of(buildUser(1L, "Top Seller 1")));
            when(userQueryService.findById(2L)).thenReturn(Optional.of(buildUser(2L, "Top Seller 2")));

            Model model = new ConcurrentModel();
            String viewName = controller.topSellers(10, model);

            assertThat(viewName).isEqualTo("reputation/top");
            assertThat(model.getAttribute("limit")).isEqualTo(10);

            @SuppressWarnings("unchecked")
            List<ReputationResponse> sellers = (List<ReputationResponse>) model.getAttribute("sellers");
            assertThat(sellers).hasSize(2);
            assertThat(sellers.get(0).getUserId()).isEqualTo(1L);
            assertThat(sellers.get(0).getFullName()).isEqualTo("Top Seller 1");
            assertThat(sellers.get(0).getScore()).isEqualByComparingTo(new BigDecimal("98.00"));
            assertThat(sellers.get(1).getUserId()).isEqualTo(2L);
            assertThat(sellers.get(1).getFullName()).isEqualTo("Top Seller 2");
            assertThat(sellers.get(1).getScore()).isEqualByComparingTo(new BigDecimal("95.00"));

            verify(reputationRepository).findTopSellers(10);
            verify(userQueryService).findById(1L);
            verify(userQueryService).findById(2L);
        }

        @Test
        @DisplayName("Truyền limit=5 → gọi findTopSellers(5)")
        void topSellers_customLimit() {
            when(reputationRepository.findTopSellers(5)).thenReturn(
                    List.of(buildScore(1L, "98.00", 50, 48, 2, 0, "25000000"))
            );
            when(userQueryService.findById(1L)).thenReturn(Optional.of(buildUser(1L, "Top Seller 1")));

            Model model = new ConcurrentModel();
            String viewName = controller.topSellers(5, model);

            assertThat(viewName).isEqualTo("reputation/top");
            assertThat(model.getAttribute("limit")).isEqualTo(5);

            verify(reputationRepository).findTopSellers(5);
            verify(userQueryService).findById(1L);
        }

        @Test
        @DisplayName("Limit=200 → bị chặn về 100")
        void topSellers_clampUpperBound() {
            when(reputationRepository.findTopSellers(100)).thenReturn(Collections.emptyList());

            Model model = new ConcurrentModel();
            controller.topSellers(200, model);

            assertThat(model.getAttribute("limit")).isEqualTo(100);
            verify(reputationRepository).findTopSellers(100);
            verify(reputationRepository, never()).findTopSellers(200);
        }

        @Test
        @DisplayName("Limit=-5 → bị chặn về 1")
        void topSellers_clampLowerBound() {
            when(reputationRepository.findTopSellers(1)).thenReturn(Collections.emptyList());

            Model model = new ConcurrentModel();
            controller.topSellers(-5, model);

            assertThat(model.getAttribute("limit")).isEqualTo(1);
            verify(reputationRepository).findTopSellers(1);
        }

        @Test
        @DisplayName("Danh sách rỗng → vẫn trả view thành công, sellers rỗng")
        void topSellers_emptyList() {
            when(reputationRepository.findTopSellers(10)).thenReturn(Collections.emptyList());

            Model model = new ConcurrentModel();
            String viewName = controller.topSellers(10, model);

            assertThat(viewName).isEqualTo("reputation/top");
            @SuppressWarnings("unchecked")
            List<ReputationResponse> sellers = (List<ReputationResponse>) model.getAttribute("sellers");
            assertThat(sellers).isEmpty();
        }

        @Test
        @DisplayName("User trong top không tìm thấy trong UserQueryService → fallback 'Thành viên #id'")
        void topSellers_userNotFound_setsFallbackFullName() {
            when(reputationRepository.findTopSellers(10)).thenReturn(
                    List.of(buildScore(99L, "99.00", 10, 10, 0, 0, "5000000"))
            );
            when(userQueryService.findById(99L)).thenReturn(Optional.empty());

            Model model = new ConcurrentModel();
            controller.topSellers(10, model);

            @SuppressWarnings("unchecked")
            List<ReputationResponse> sellers = (List<ReputationResponse>) model.getAttribute("sellers");
            assertThat(sellers).hasSize(1);
            assertThat(sellers.get(0).getFullName()).isEqualTo("Thành viên #99");
        }
    }
}
