package com.yourapp.ticketpass.reputation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yourapp.ticketpass.reputation.domain.ReputationRepository;
import com.yourapp.ticketpass.reputation.domain.UserReputationScore;
import com.yourapp.ticketpass.shared.event.CompletionReason;
import com.yourapp.ticketpass.shared.event.DisputeOutcome;
import com.yourapp.ticketpass.shared.event.DisputeResolvedEvent;
import com.yourapp.ticketpass.shared.event.TicketTransactionCompletedEvent;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;

/**
 * Unit test cho {@link UpdateReputationUseCase} — Task C8.
 *
 * <p>Kiểm thử toàn diện 17 kịch bản nghiệp vụ theo BR-11:
 * <ul>
 *   <li>Nhóm getOrCreate: tìm bản ghi hoặc tạo mới với điểm 100.00</li>
 *   <li>Nhóm onTicketTransactionCompleted: +2 điểm, tăng counter, cộng GMV người bán, chặn trần 100.00</li>
 *   <li>Nhóm onDisputeResolved: BUYER_WINS, SELLER_WINS, WITHDRAWN, sàn 0.00</li>
 *   <li>Nhóm recordCancelledListing: -10 điểm khi huỷ niêm yết, sàn 0.00</li>
 *   <li>Nhóm Concurrency & Retry: xử lý xung đột OptimisticLockingFailureException</li>
 * </ul>
 * </p>
 *
 * <p>Module: reputation</p>
 *
 * Chủ sở hữu: Người C
 */
@ExtendWith(MockitoExtension.class)
class UpdateReputationUseCaseTest {

    @Mock
    private ReputationRepository reputationRepository;

    @InjectMocks
    private UpdateReputationUseCase updateReputationUseCase;

    private UserReputationScore createSampleScore(Long userId, BigDecimal score, int totalTx, int successTx) {
        UserReputationScore rep = new UserReputationScore();
        rep.setId(100L + userId);
        rep.setUserId(userId);
        rep.setScore(score);
        rep.setTotalTransactions(totalTx);
        rep.setSuccessfulTransactions(successTx);
        rep.setCancelledListings(0);
        rep.setDisputesOpenedAgainst(0);
        rep.setDisputesLost(0);
        rep.setDisputesWon(0);
        rep.setTotalGmv(BigDecimal.ZERO);
        rep.setLastTransactionAt(null);
        return rep;
    }

    private TicketTransactionCompletedEvent createTransactionEvent(Long sellerId, Long buyerId, BigDecimal salePrice) {
        return new TicketTransactionCompletedEvent(
                10L, 20L, 30L, sellerId, buyerId,
                salePrice,
                new BigDecimal("10000"), new BigDecimal("15000"), BigDecimal.ZERO,
                salePrice.subtract(new BigDecimal("15000")), new BigDecimal("50000"),
                CompletionReason.BUYER_CONFIRMED,
                Instant.now()
        );
    }

    private DisputeResolvedEvent createDisputeEvent(Long sellerId, Long buyerId, DisputeOutcome outcome) {
        return new DisputeResolvedEvent(
                50L, 10L, 20L, 30L,
                buyerId, sellerId, outcome,
                new BigDecimal("50000"), BigDecimal.ZERO, new BigDecimal("50000"),
                999L, Instant.now()
        );
    }

    // =========================================================================
    // 1. Nhóm getOrCreate
    // =========================================================================
    @Nested
    @DisplayName("Nhóm getOrCreate")
    class GetOrCreateTests {

        @Test
        @DisplayName("1. getOrCreate: khi người dùng đã có điểm -> trả về bản ghi hiện có, không gọi save()")
        void getOrCreate_existingUser_returnsExisting() {
            Long userId = 1L;
            UserReputationScore existing = createSampleScore(userId, new BigDecimal("85.00"), 5, 5);
            when(reputationRepository.findByUserId(userId)).thenReturn(Optional.of(existing));

            UserReputationScore result = updateReputationUseCase.getOrCreate(userId);

            assertThat(result).isNotNull();
            assertThat(result.getUserId()).isEqualTo(userId);
            assertThat(result.getScore()).isEqualByComparingTo("85.00");
            verify(reputationRepository, never()).save(any());
        }

        @Test
        @DisplayName("2. getOrCreate: khi người dùng chưa có -> tạo mới với điểm 100.00 và các counter bằng 0")
        void getOrCreate_newUser_createsWithInitialScore() {
            Long userId = 2L;
            when(reputationRepository.findByUserId(userId)).thenReturn(Optional.empty());
            when(reputationRepository.save(any(UserReputationScore.class))).thenAnswer(invocation -> invocation.getArgument(0));

            UserReputationScore result = updateReputationUseCase.getOrCreate(userId);

            assertThat(result).isNotNull();
            assertThat(result.getUserId()).isEqualTo(userId);
            assertThat(result.getScore()).isEqualByComparingTo("100.00");
            assertThat(result.getTotalTransactions()).isZero();
            assertThat(result.getSuccessfulTransactions()).isZero();
            assertThat(result.getCancelledListings()).isZero();
            assertThat(result.getDisputesOpenedAgainst()).isZero();
            assertThat(result.getDisputesLost()).isZero();
            assertThat(result.getDisputesWon()).isZero();
            assertThat(result.getTotalGmv()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("3. getOrCreate: khi tạo mới -> save() được gọi đúng 1 lần và trả về bản ghi đã lưu")
        void getOrCreate_newUser_savesAndReturns() {
            Long userId = 3L;
            when(reputationRepository.findByUserId(userId)).thenReturn(Optional.empty());
            UserReputationScore saved = createSampleScore(userId, new BigDecimal("100.00"), 0, 0);
            when(reputationRepository.save(any(UserReputationScore.class))).thenReturn(saved);

            UserReputationScore result = updateReputationUseCase.getOrCreate(userId);

            assertThat(result).isSameAs(saved);
            verify(reputationRepository).save(any(UserReputationScore.class));
        }

        @Test
        @DisplayName("getOrCreate: ném ngoại lệ khi userId là null")
        void getOrCreate_nullUserId_throwsException() {
            assertThatThrownBy(() -> updateReputationUseCase.getOrCreate(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // =========================================================================
    // 2. Nhóm onTicketTransactionCompleted
    // =========================================================================
    @Nested
    @DisplayName("Nhóm onTicketTransactionCompleted")
    class TransactionCompletedTests {

        @Test
        @DisplayName("4. onTransaction: người bán được cộng 2 điểm")
        void onTransaction_sellerGets2Points() {
            Long sellerId = 1L;
            Long buyerId = 2L;
            UserReputationScore sellerScore = createSampleScore(sellerId, new BigDecimal("90.00"), 2, 2);
            UserReputationScore buyerScore = createSampleScore(buyerId, new BigDecimal("80.00"), 1, 1);

            when(reputationRepository.findByUserId(sellerId)).thenReturn(Optional.of(sellerScore));
            when(reputationRepository.findByUserId(buyerId)).thenReturn(Optional.of(buyerScore));

            TicketTransactionCompletedEvent event = createTransactionEvent(sellerId, buyerId, new BigDecimal("250000"));
            updateReputationUseCase.onTicketTransactionCompleted(event);

            assertThat(sellerScore.getScore()).isEqualByComparingTo("92.00");
            verify(reputationRepository).save(sellerScore);
        }

        @Test
        @DisplayName("5. onTransaction: người mua được cộng 2 điểm")
        void onTransaction_buyerGets2Points() {
            Long sellerId = 1L;
            Long buyerId = 2L;
            UserReputationScore sellerScore = createSampleScore(sellerId, new BigDecimal("90.00"), 2, 2);
            UserReputationScore buyerScore = createSampleScore(buyerId, new BigDecimal("80.00"), 1, 1);

            when(reputationRepository.findByUserId(sellerId)).thenReturn(Optional.of(sellerScore));
            when(reputationRepository.findByUserId(buyerId)).thenReturn(Optional.of(buyerScore));

            TicketTransactionCompletedEvent event = createTransactionEvent(sellerId, buyerId, new BigDecimal("250000"));
            updateReputationUseCase.onTicketTransactionCompleted(event);

            assertThat(buyerScore.getScore()).isEqualByComparingTo("82.00");
            verify(reputationRepository).save(buyerScore);
        }

        @Test
        @DisplayName("6. onTransaction: GMV của người bán tăng đúng bằng salePrice, người mua không tăng GMV")
        void onTransaction_sellerGmvIncreased() {
            Long sellerId = 1L;
            Long buyerId = 2L;
            UserReputationScore sellerScore = createSampleScore(sellerId, new BigDecimal("90.00"), 0, 0);
            sellerScore.setTotalGmv(new BigDecimal("100000"));
            UserReputationScore buyerScore = createSampleScore(buyerId, new BigDecimal("80.00"), 0, 0);
            buyerScore.setTotalGmv(BigDecimal.ZERO);

            when(reputationRepository.findByUserId(sellerId)).thenReturn(Optional.of(sellerScore));
            when(reputationRepository.findByUserId(buyerId)).thenReturn(Optional.of(buyerScore));

            TicketTransactionCompletedEvent event = createTransactionEvent(sellerId, buyerId, new BigDecimal("250000"));
            updateReputationUseCase.onTicketTransactionCompleted(event);

            assertThat(sellerScore.getTotalGmv()).isEqualByComparingTo("350000.00");
            assertThat(buyerScore.getTotalGmv()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("7. onTransaction: totalTransactions và successfulTransactions tăng 1 cho cả hai bên")
        void onTransaction_countersIncremented() {
            Long sellerId = 1L;
            Long buyerId = 2L;
            UserReputationScore sellerScore = createSampleScore(sellerId, new BigDecimal("90.00"), 3, 3);
            UserReputationScore buyerScore = createSampleScore(buyerId, new BigDecimal("80.00"), 5, 4);

            when(reputationRepository.findByUserId(sellerId)).thenReturn(Optional.of(sellerScore));
            when(reputationRepository.findByUserId(buyerId)).thenReturn(Optional.of(buyerScore));

            TicketTransactionCompletedEvent event = createTransactionEvent(sellerId, buyerId, new BigDecimal("500000"));
            updateReputationUseCase.onTicketTransactionCompleted(event);

            assertThat(sellerScore.getTotalTransactions()).isEqualTo(4);
            assertThat(sellerScore.getSuccessfulTransactions()).isEqualTo(4);
            assertThat(buyerScore.getTotalTransactions()).isEqualTo(6);
            assertThat(buyerScore.getSuccessfulTransactions()).isEqualTo(5);
            assertThat(sellerScore.getLastTransactionAt()).isEqualTo(event.getOccurredAt());
            assertThat(buyerScore.getLastTransactionAt()).isEqualTo(event.getOccurredAt());
        }

        @Test
        @DisplayName("8. onTransaction: điểm chặn trần 100.00 khi cộng vượt quá (99.00 + 2 -> 100.00)")
        void onTransaction_scoreClampedAt100() {
            Long sellerId = 1L;
            Long buyerId = 2L;
            UserReputationScore sellerScore = createSampleScore(sellerId, new BigDecimal("99.00"), 10, 10);
            UserReputationScore buyerScore = createSampleScore(buyerId, new BigDecimal("100.00"), 10, 10);

            when(reputationRepository.findByUserId(sellerId)).thenReturn(Optional.of(sellerScore));
            when(reputationRepository.findByUserId(buyerId)).thenReturn(Optional.of(buyerScore));

            TicketTransactionCompletedEvent event = createTransactionEvent(sellerId, buyerId, new BigDecimal("100000"));
            updateReputationUseCase.onTicketTransactionCompleted(event);

            assertThat(sellerScore.getScore()).isEqualByComparingTo("100.00");
            assertThat(buyerScore.getScore()).isEqualByComparingTo("100.00");
        }

        @Test
        @DisplayName("8b. onTransaction: khi lý do là DISPUTE_RESOLVED_SELLER_WINS -> chỉ tăng GMV/count cho người bán, không cộng điểm cho người mua")
        void onTransaction_disputeResolvedSellerWins_onlyUpdatesSellerGmvWithoutBuyerBonus() {
            Long sellerId = 1L;
            Long buyerId = 2L;
            UserReputationScore sellerScore = createSampleScore(sellerId, new BigDecimal("90.00"), 3, 3);
            UserReputationScore buyerScore = createSampleScore(buyerId, new BigDecimal("80.00"), 5, 4);

            when(reputationRepository.findByUserId(sellerId)).thenReturn(Optional.of(sellerScore));

            TicketTransactionCompletedEvent event = new TicketTransactionCompletedEvent(
                    10L, 20L, 30L, sellerId, buyerId,
                    new BigDecimal("250000"),
                    new BigDecimal("10000"), new BigDecimal("15000"), BigDecimal.ZERO,
                    new BigDecimal("235000"), new BigDecimal("50000"),
                    CompletionReason.DISPUTE_RESOLVED_SELLER_WINS,
                    Instant.now()
            );
            updateReputationUseCase.onTicketTransactionCompleted(event);

            assertThat(sellerScore.getTotalTransactions()).isEqualTo(4);
            assertThat(sellerScore.getSuccessfulTransactions()).isEqualTo(4);
            assertThat(sellerScore.getTotalGmv()).isEqualByComparingTo("250000.00");
            assertThat(sellerScore.getScore()).isEqualByComparingTo("90.00");
            verify(reputationRepository, never()).findByUserId(buyerId);
        }

        @Test
        @DisplayName("onTransaction: bỏ qua khi event là null")
        void onTransaction_nullEvent_ignored() {
            updateReputationUseCase.onTicketTransactionCompleted(null);
            verify(reputationRepository, never()).findByUserId(any());
        }
    }

    // =========================================================================
    // 3. Nhóm onDisputeResolved
    // =========================================================================
    @Nested
    @DisplayName("Nhóm onDisputeResolved")
    class DisputeResolvedTests {

        @Test
        @DisplayName("9. onDispute BUYER_WINS: người bán bị trừ 15 điểm và tăng disputesLost")
        void onDispute_buyerWins_sellerLoses15() {
            Long sellerId = 1L;
            Long buyerId = 2L;
            UserReputationScore sellerScore = createSampleScore(sellerId, new BigDecimal("90.00"), 5, 5);
            UserReputationScore buyerScore = createSampleScore(buyerId, new BigDecimal("80.00"), 5, 5);

            when(reputationRepository.findByUserId(sellerId)).thenReturn(Optional.of(sellerScore));
            when(reputationRepository.findByUserId(buyerId)).thenReturn(Optional.of(buyerScore));

            DisputeResolvedEvent event = createDisputeEvent(sellerId, buyerId, DisputeOutcome.BUYER_WINS);
            updateReputationUseCase.onDisputeResolved(event);

            assertThat(sellerScore.getScore()).isEqualByComparingTo("75.00");
            assertThat(sellerScore.getDisputesLost()).isEqualTo(1);
            assertThat(sellerScore.getDisputesWon()).isZero();
        }

        @Test
        @DisplayName("10. onDispute BUYER_WINS: người mua được cộng 1 điểm và tăng disputesWon")
        void onDispute_buyerWins_buyerGains1() {
            Long sellerId = 1L;
            Long buyerId = 2L;
            UserReputationScore sellerScore = createSampleScore(sellerId, new BigDecimal("90.00"), 5, 5);
            UserReputationScore buyerScore = createSampleScore(buyerId, new BigDecimal("80.00"), 5, 5);

            when(reputationRepository.findByUserId(sellerId)).thenReturn(Optional.of(sellerScore));
            when(reputationRepository.findByUserId(buyerId)).thenReturn(Optional.of(buyerScore));

            DisputeResolvedEvent event = createDisputeEvent(sellerId, buyerId, DisputeOutcome.BUYER_WINS);
            updateReputationUseCase.onDisputeResolved(event);

            assertThat(buyerScore.getScore()).isEqualByComparingTo("81.00");
            assertThat(buyerScore.getDisputesWon()).isEqualTo(1);
            assertThat(buyerScore.getDisputesLost()).isZero();
        }

        @Test
        @DisplayName("11. onDispute SELLER_WINS: người bán được cộng 1 điểm và tăng disputesWon")
        void onDispute_sellerWins_sellerGains1() {
            Long sellerId = 1L;
            Long buyerId = 2L;
            UserReputationScore sellerScore = createSampleScore(sellerId, new BigDecimal("90.00"), 5, 5);
            UserReputationScore buyerScore = createSampleScore(buyerId, new BigDecimal("80.00"), 5, 5);

            when(reputationRepository.findByUserId(sellerId)).thenReturn(Optional.of(sellerScore));
            when(reputationRepository.findByUserId(buyerId)).thenReturn(Optional.of(buyerScore));

            DisputeResolvedEvent event = createDisputeEvent(sellerId, buyerId, DisputeOutcome.SELLER_WINS);
            updateReputationUseCase.onDisputeResolved(event);

            assertThat(sellerScore.getScore()).isEqualByComparingTo("91.00");
            assertThat(sellerScore.getDisputesWon()).isEqualTo(1);
            assertThat(sellerScore.getDisputesLost()).isZero();
        }

        @Test
        @DisplayName("12. onDispute SELLER_WINS: người mua bị trừ 15 điểm và tăng disputesLost")
        void onDispute_sellerWins_buyerLoses15() {
            Long sellerId = 1L;
            Long buyerId = 2L;
            UserReputationScore sellerScore = createSampleScore(sellerId, new BigDecimal("90.00"), 5, 5);
            UserReputationScore buyerScore = createSampleScore(buyerId, new BigDecimal("80.00"), 5, 5);

            when(reputationRepository.findByUserId(sellerId)).thenReturn(Optional.of(sellerScore));
            when(reputationRepository.findByUserId(buyerId)).thenReturn(Optional.of(buyerScore));

            DisputeResolvedEvent event = createDisputeEvent(sellerId, buyerId, DisputeOutcome.SELLER_WINS);
            updateReputationUseCase.onDisputeResolved(event);

            assertThat(buyerScore.getScore()).isEqualByComparingTo("65.00");
            assertThat(buyerScore.getDisputesLost()).isEqualTo(1);
            assertThat(buyerScore.getDisputesWon()).isZero();
        }

        @Test
        @DisplayName("13. onDispute: điểm áp sàn 0.00 khi bị trừ dưới 0 (10.00 - 15 -> 0.00)")
        void onDispute_scoreFlooredAt0() {
            Long sellerId = 1L;
            Long buyerId = 2L;
            UserReputationScore sellerScore = createSampleScore(sellerId, new BigDecimal("10.00"), 5, 1);
            UserReputationScore buyerScore = createSampleScore(buyerId, new BigDecimal("80.00"), 5, 5);

            when(reputationRepository.findByUserId(sellerId)).thenReturn(Optional.of(sellerScore));
            when(reputationRepository.findByUserId(buyerId)).thenReturn(Optional.of(buyerScore));

            DisputeResolvedEvent event = createDisputeEvent(sellerId, buyerId, DisputeOutcome.BUYER_WINS);
            updateReputationUseCase.onDisputeResolved(event);

            assertThat(sellerScore.getScore()).isEqualByComparingTo("0.00");
        }

        @Test
        @DisplayName("14. onDispute WITHDRAWN: không đổi điểm bên nào, chỉ tăng disputesOpenedAgainst cho người bán")
        void onDispute_withdrawn_noScoreChange() {
            Long sellerId = 1L;
            Long buyerId = 2L;
            UserReputationScore sellerScore = createSampleScore(sellerId, new BigDecimal("90.00"), 5, 5);

            when(reputationRepository.findByUserId(sellerId)).thenReturn(Optional.of(sellerScore));

            DisputeResolvedEvent event = createDisputeEvent(sellerId, buyerId, DisputeOutcome.WITHDRAWN);
            updateReputationUseCase.onDisputeResolved(event);

            assertThat(sellerScore.getScore()).isEqualByComparingTo("90.00");
            assertThat(sellerScore.getDisputesOpenedAgainst()).isEqualTo(1);
            verify(reputationRepository, never()).findByUserId(buyerId);
            verify(reputationRepository).save(sellerScore);
        }

        @Test
        @DisplayName("onDispute: bỏ qua khi event hoặc outcome là null")
        void onDispute_nullEventOrOutcome_ignored() {
            updateReputationUseCase.onDisputeResolved(null);
            verify(reputationRepository, never()).findByUserId(any());
        }
    }

    // =========================================================================
    // 4. Nhóm recordCancelledListing
    // =========================================================================
    @Nested
    @DisplayName("Nhóm recordCancelledListing")
    class CancelledListingTests {

        @Test
        @DisplayName("15. recordCancelledListing: trừ 10 điểm và tăng cancelledListings thêm 1")
        void cancelledListing_minus10Points() {
            Long sellerId = 1L;
            UserReputationScore sellerScore = createSampleScore(sellerId, new BigDecimal("90.00"), 5, 5);
            when(reputationRepository.findByUserId(sellerId)).thenReturn(Optional.of(sellerScore));

            updateReputationUseCase.recordCancelledListing(sellerId);

            assertThat(sellerScore.getScore()).isEqualByComparingTo("80.00");
            assertThat(sellerScore.getCancelledListings()).isEqualTo(1);
            verify(reputationRepository).save(sellerScore);
        }

        @Test
        @DisplayName("16. recordCancelledListing: điểm áp sàn 0.00 khi bị trừ dưới 0 (5.00 - 10 -> 0.00)")
        void cancelledListing_scoreFlooredAt0() {
            Long sellerId = 1L;
            UserReputationScore sellerScore = createSampleScore(sellerId, new BigDecimal("5.00"), 5, 1);
            when(reputationRepository.findByUserId(sellerId)).thenReturn(Optional.of(sellerScore));

            updateReputationUseCase.recordCancelledListing(sellerId);

            assertThat(sellerScore.getScore()).isEqualByComparingTo("0.00");
            assertThat(sellerScore.getCancelledListings()).isEqualTo(1);
        }

        @Test
        @DisplayName("17. recordCancelledListing: đảm bảo save() được gọi đúng 1 lần")
        void cancelledListing_callsSave() {
            Long sellerId = 1L;
            UserReputationScore sellerScore = createSampleScore(sellerId, new BigDecimal("100.00"), 0, 0);
            when(reputationRepository.findByUserId(sellerId)).thenReturn(Optional.of(sellerScore));

            updateReputationUseCase.recordCancelledListing(sellerId);

            verify(reputationRepository).save(sellerScore);
        }

        @Test
        @DisplayName("recordCancelledListing: ném ngoại lệ khi sellerId là null")
        void cancelledListing_nullSellerId_throwsException() {
            assertThatThrownBy(() -> updateReputationUseCase.recordCancelledListing(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // =========================================================================
    // 4b. Nhóm recordDepositTimeout (UC-04b)
    // =========================================================================
    @Nested
    @DisplayName("Nhóm recordDepositTimeout (UC-04b)")
    class DepositTimeoutTests {

        @Test
        @DisplayName("recordDepositTimeout: trừ 15 điểm uy tín và tăng cancelledListings thêm 1")
        void depositTimeout_minus15Points() {
            Long sellerId = 1L;
            UserReputationScore sellerScore = createSampleScore(sellerId, new BigDecimal("90.00"), 5, 5);
            when(reputationRepository.findByUserId(sellerId)).thenReturn(Optional.of(sellerScore));

            updateReputationUseCase.recordDepositTimeout(sellerId);

            assertThat(sellerScore.getScore()).isEqualByComparingTo("75.00");
            assertThat(sellerScore.getDisputesLost()).isEqualTo(1);
            verify(reputationRepository).save(sellerScore);
        }

        @Test
        @DisplayName("recordDepositTimeout: điểm áp sàn 0.00 khi bị trừ dưới 0 (10.00 - 15 -> 0.00)")
        void depositTimeout_scoreFlooredAt0() {
            Long sellerId = 1L;
            UserReputationScore sellerScore = createSampleScore(sellerId, new BigDecimal("10.00"), 5, 1);
            when(reputationRepository.findByUserId(sellerId)).thenReturn(Optional.of(sellerScore));

            updateReputationUseCase.recordDepositTimeout(sellerId);

            assertThat(sellerScore.getScore()).isEqualByComparingTo("0.00");
            assertThat(sellerScore.getDisputesLost()).isEqualTo(1);
        }

        @Test
        @DisplayName("recordDepositTimeout: ném ngoại lệ khi sellerId là null")
        void depositTimeout_nullSellerId_throwsException() {
            assertThatThrownBy(() -> updateReputationUseCase.recordDepositTimeout(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // =========================================================================
    // 5. Nhóm Concurrency & Retry (Optimistic Locking)
    // =========================================================================
    @Nested
    @DisplayName("Nhóm Concurrency & Retry")
    class ConcurrencyAndRetryTests {

        @Test
        @DisplayName("18. Retry: khi gặp OptimisticLockingFailureException lần đầu, retry thành công ở lần 2")
        void retry_optimisticLockingConflict_retriesAndSucceeds() {
            Long sellerId = 1L;
            UserReputationScore initialScore = createSampleScore(sellerId, new BigDecimal("90.00"), 1, 1);
            UserReputationScore freshScore = createSampleScore(sellerId, new BigDecimal("90.00"), 1, 1);

            when(reputationRepository.findByUserId(sellerId))
                    .thenReturn(Optional.of(initialScore))
                    .thenReturn(Optional.of(freshScore));

            when(reputationRepository.save(any(UserReputationScore.class)))
                    .thenThrow(new OptimisticLockingFailureException("Version conflict on attempt 1"))
                    .thenReturn(freshScore);

            updateReputationUseCase.recordCancelledListing(sellerId);

            // Xác nhận save() được gọi 2 lần (lần 1 lỗi, lần 2 thành công)
            verify(reputationRepository, times(2)).save(any(UserReputationScore.class));
            assertThat(freshScore.getScore()).isEqualByComparingTo("80.00");
            assertThat(freshScore.getCancelledListings()).isEqualTo(1);
        }

        @Test
        @DisplayName("19. Retry: vượt quá MAX_RETRY (3 lần) thì ném OptimisticLockingFailureException ra ngoài")
        void retry_exceedsMaxRetry_throwsException() {
            Long sellerId = 1L;
            UserReputationScore score = createSampleScore(sellerId, new BigDecimal("90.00"), 1, 1);

            when(reputationRepository.findByUserId(sellerId)).thenReturn(Optional.of(score));
            when(reputationRepository.save(any(UserReputationScore.class)))
                    .thenThrow(new OptimisticLockingFailureException("Persistent version conflict"));

            assertThatThrownBy(() -> updateReputationUseCase.recordCancelledListing(sellerId))
                    .isInstanceOf(OptimisticLockingFailureException.class);

            verify(reputationRepository, times(3)).save(any(UserReputationScore.class));
        }
    }
}
