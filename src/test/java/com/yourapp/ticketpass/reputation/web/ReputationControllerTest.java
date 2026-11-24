package com.yourapp.ticketpass.reputation.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yourapp.ticketpass.reputation.application.ReputationPolicyService;
import com.yourapp.ticketpass.reputation.application.UpdateReputationUseCase;
import com.yourapp.ticketpass.reputation.domain.ReputationRepository;
import com.yourapp.ticketpass.reputation.domain.UserReputationScore;
import com.yourapp.ticketpass.reputation.web.dto.ReputationResponse;
import com.yourapp.ticketpass.reputation.web.dto.TransactionHistoryItem;
import com.yourapp.ticketpass.ticket.domain.Ticket;
import com.yourapp.ticketpass.ticket.domain.TicketRepository;
import com.yourapp.ticketpass.ticket.domain.TicketStatus;
import com.yourapp.ticketpass.user.application.UserQueryService;
import com.yourapp.ticketpass.user.domain.User;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;

@ExtendWith(MockitoExtension.class)
class ReputationControllerTest {

    @Mock
    private ReputationRepository reputationRepository;

    @Mock
    private UpdateReputationUseCase updateReputationUseCase;

    @Mock
    private UserQueryService userQueryService;

    @Mock
    private TicketRepository ticketRepository;

    private ReputationPolicyService reputationPolicyService;
    private ReputationController controller;

    @BeforeEach
    void setUp() {
        reputationPolicyService = ReputationPolicyService.withDefaultPolicies(reputationRepository);
        controller = new ReputationController(
                reputationRepository,
                updateReputationUseCase,
                userQueryService,
                ticketRepository,
                reputationPolicyService
        );
    }

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

    private Ticket buildTicket(Long id, Long sellerId, Long buyerId, String title, TicketStatus status) {
        Ticket t = new Ticket();
        t.setId(id);
        t.setSellerId(sellerId);
        t.setBuyerId(buyerId);
        t.setTitle(title);
        t.setEventName(title);
        t.setSalePrice(new BigDecimal("500000.00"));
        t.setStatus(status);
        t.setCompletedAt(Instant.parse("2026-11-20T10:00:00Z"));
        return t;
    }

    @Nested
    @DisplayName("GET /reputation/{userId}")
    class DetailTests {

        @Test
        @DisplayName("User điểm 4 -> map đúng bậc 'Tốt', hạn mức < 10tr và không còn attribute reputationHistory")
        void detail_existingUser_returnsViewWithTierAndNoReputationHistory() {
            UserReputationScore score = buildScore(1L, "4.00", 10, 8, 1, 1, "5000000");
            when(updateReputationUseCase.getOrCreate(1L)).thenReturn(score);
            when(userQueryService.findById(1L)).thenReturn(Optional.of(buildUser(1L, "Nguyễn Văn A")));

            Model model = new ConcurrentModel();
            String viewName = controller.detail(1L, model);

            assertThat(viewName).isEqualTo("reputation/detail");
            ReputationResponse response = (ReputationResponse) model.getAttribute("reputation");
            assertThat(response).isNotNull();
            assertThat(response.getUserId()).isEqualTo(1L);
            assertThat(response.getFullName()).isEqualTo("Nguyễn Văn A");
            assertThat(response.getScoreInt()).isEqualTo(4);
            assertThat(response.getTierName()).isEqualTo("Tốt");
            assertThat(response.getTransactionLimitDescription()).contains("10.000.000");
            assertThat(model.containsAttribute("reputationHistory")).isFalse();
        }

        @Test
        @DisplayName("User mới -> mặc định điểm 3 (Bình thường) và fallback tên 'Thành viên #id'")
        void detail_newUser_createsDefaultScore3() {
            UserReputationScore defaultScore = buildScore(999L, "3.00", 0, 0, 0, 0, "0");
            when(updateReputationUseCase.getOrCreate(999L)).thenReturn(defaultScore);
            when(userQueryService.findById(999L)).thenReturn(Optional.empty());

            Model model = new ConcurrentModel();
            String viewName = controller.detail(999L, model);

            assertThat(viewName).isEqualTo("reputation/detail");
            ReputationResponse response = (ReputationResponse) model.getAttribute("reputation");
            assertThat(response).isNotNull();
            assertThat(response.getFullName()).isEqualTo("Thành viên #999");
            assertThat(response.getScoreInt()).isEqualTo(3);
            assertThat(response.getTierName()).isEqualTo("Bình thường");
        }

        @Test
        @DisplayName("Fix nhãn lịch sử uy tín: RESOLVED_BUYER_WINS hiển thị 'Thua tranh chấp' (danger) cho SELLER và 'Thắng tranh chấp' (success) cho BUYER")
        void detail_disputeOutcomeLabels_distinguishSellerAndBuyerRole() {
            Long userId = 10L;
            when(updateReputationUseCase.getOrCreate(userId)).thenReturn(buildScore(userId, "2.00", 4, 2, 1, 1, "1000000"));
            when(userQueryService.findById(userId)).thenReturn(Optional.of(buildUser(userId, "User Test")));

            Ticket sellerLost = buildTicket(101L, userId, 20L, "Vé Seller Thua", TicketStatus.RESOLVED_BUYER_WINS);
            Ticket sellerWon = buildTicket(102L, userId, 21L, "Vé Seller Thắng", TicketStatus.RESOLVED_SELLER_WINS);
            when(ticketRepository.findBySellerId(userId)).thenReturn(List.of(sellerLost, sellerWon));

            Ticket buyerWon = buildTicket(201L, 30L, userId, "Vé Buyer Thắng", TicketStatus.RESOLVED_BUYER_WINS);
            Ticket buyerLost = buildTicket(202L, 31L, userId, "Vé Buyer Thua", TicketStatus.RESOLVED_SELLER_WINS);
            when(ticketRepository.findByBuyerId(userId)).thenReturn(List.of(buyerWon, buyerLost));

            Model model = new ConcurrentModel();
            controller.detail(userId, model);

            @SuppressWarnings("unchecked")
            List<TransactionHistoryItem> history = (List<TransactionHistoryItem>) model.getAttribute("history");
            assertThat(history).hasSize(4);

            TransactionHistoryItem itemSellerLost = history.stream().filter(i -> i.getTicketId().equals(101L)).findFirst().orElseThrow();
            assertThat(itemSellerLost.getRole()).isEqualTo("SELLER");
            assertThat(itemSellerLost.getOutcomeLabel()).isEqualTo("Thua tranh chấp");
            assertThat(itemSellerLost.getOutcomeColor()).isEqualTo("danger");

            TransactionHistoryItem itemSellerWon = history.stream().filter(i -> i.getTicketId().equals(102L)).findFirst().orElseThrow();
            assertThat(itemSellerWon.getRole()).isEqualTo("SELLER");
            assertThat(itemSellerWon.getOutcomeLabel()).isEqualTo("Thắng tranh chấp");
            assertThat(itemSellerWon.getOutcomeColor()).isEqualTo("success");

            TransactionHistoryItem itemBuyerWon = history.stream().filter(i -> i.getTicketId().equals(201L)).findFirst().orElseThrow();
            assertThat(itemBuyerWon.getRole()).isEqualTo("BUYER");
            assertThat(itemBuyerWon.getOutcomeLabel()).isEqualTo("Thắng tranh chấp");
            assertThat(itemBuyerWon.getOutcomeColor()).isEqualTo("success");

            TransactionHistoryItem itemBuyerLost = history.stream().filter(i -> i.getTicketId().equals(202L)).findFirst().orElseThrow();
            assertThat(itemBuyerLost.getRole()).isEqualTo("BUYER");
            assertThat(itemBuyerLost.getOutcomeLabel()).isEqualTo("Thua tranh chấp");
            assertThat(itemBuyerLost.getOutcomeColor()).isEqualTo("danger");
        }
    }

    @Nested
    @DisplayName("GET /reputation/top")
    class TopSellersTests {

        @Test
        @DisplayName("Top sellers hiển thị điểm nguyên 0-5 và đúng bậc uy tín")
        void topSellers_defaultLimit() {
            List<UserReputationScore> scores = List.of(
                    buildScore(1L, "5.00", 50, 48, 2, 0, "25000000"),
                    buildScore(2L, "4.00", 30, 28, 1, 1, "15000000")
            );
            when(reputationRepository.findTopSellers(10)).thenReturn(scores);
            when(userQueryService.findById(1L)).thenReturn(Optional.of(buildUser(1L, "Top Seller 1")));
            when(userQueryService.findById(2L)).thenReturn(Optional.of(buildUser(2L, "Top Seller 2")));

            Model model = new ConcurrentModel();
            String viewName = controller.topSellers(10, model);

            assertThat(viewName).isEqualTo("reputation/top");
            @SuppressWarnings("unchecked")
            List<ReputationResponse> sellers = (List<ReputationResponse>) model.getAttribute("sellers");
            assertThat(sellers).hasSize(2);
            assertThat(sellers.get(0).getScoreInt()).isEqualTo(5);
            assertThat(sellers.get(0).getTierName()).isEqualTo("Xuất Sắc");
            assertThat(sellers.get(1).getScoreInt()).isEqualTo(4);
            assertThat(sellers.get(1).getTierName()).isEqualTo("Tốt");
        }

        @Test
        @DisplayName("Limit được chặn chặt trong đoạn [1..100]")
        void topSellers_clampBounds() {
            when(reputationRepository.findTopSellers(100)).thenReturn(Collections.emptyList());
            when(reputationRepository.findTopSellers(1)).thenReturn(Collections.emptyList());

            Model m1 = new ConcurrentModel();
            controller.topSellers(200, m1);
            assertThat(m1.getAttribute("limit")).isEqualTo(100);
            verify(reputationRepository, never()).findTopSellers(200);

            Model m2 = new ConcurrentModel();
            controller.topSellers(-5, m2);
            assertThat(m2.getAttribute("limit")).isEqualTo(1);
        }
    }
}
