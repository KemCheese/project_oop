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
import com.yourapp.ticketpass.shared.event.ReputationDepletedEvent;
import com.yourapp.ticketpass.shared.event.TicketTransactionCompletedEvent;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.OptimisticLockingFailureException;

@ExtendWith(MockitoExtension.class)
class UpdateReputationUseCaseTest {

    @Mock
    private ReputationRepository reputationRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

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

    @Nested
    @DisplayName("Nhóm getOrCreate (Thang 0-5, khởi tạo 3.00)")
    class GetOrCreateTests {

        @Test
        @DisplayName("1. getOrCreate: khi người dùng đã có điểm -> trả về bản ghi hiện có, không gọi save()")
        void getOrCreate_existingUser_returnsExisting() {
            Long userId = 1L;
            UserReputationScore existing = createSampleScore(userId, new BigDecimal("4.00"), 5, 5);
            when(reputationRepository.findByUserId(userId)).thenReturn(Optional.of(existing));

            UserReputationScore result = updateReputationUseCase.getOrCreate(userId);

            assertThat(result).isNotNull();
            assertThat(result.getUserId()).isEqualTo(userId);
            assertThat(result.getScore()).isEqualByComparingTo("4.00");
            verify(reputationRepository, never()).save(any());
        }

        @Test
        @DisplayName("2. getOrCreate: khi người dùng chưa có -> tạo mới với điểm 3.00 (Bình thường) và counter = 0")
        void getOrCreate_newUser_createsWithInitialScore() {
            Long userId = 2L;
            when(reputationRepository.findByUserId(userId)).thenReturn(Optional.empty());
            when(reputationRepository.save(any(UserReputationScore.class))).thenAnswer(i -> i.getArgument(0));

            UserReputationScore result = updateReputationUseCase.getOrCreate(userId);

            assertThat(result).isNotNull();
            assertThat(result.getUserId()).isEqualTo(userId);
            assertThat(result.getScore()).isEqualByComparingTo("3.00");
            assertThat(result.getTotalTransactions()).isZero();
            assertThat(result.getSuccessfulTransactions()).isZero();
            assertThat(result.getCancelledListings()).isZero();
            assertThat(result.getDisputesOpenedAgainst()).isZero();
            assertThat(result.getDisputesLost()).isZero();
            assertThat(result.getDisputesWon()).isZero();
            assertThat(result.getTotalGmv()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("3. getOrCreate: ném ngoại lệ khi userId là null")
        void getOrCreate_nullUserId_throwsException() {
            assertThatThrownBy(() -> updateReputationUseCase.getOrCreate(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("Nhóm onTicketTransactionCompleted (+1 điểm, trần 5.00)")
    class TransactionCompletedTests {

        @Test
        @DisplayName("4. onTransaction: Seller và Buyer đều được +1 điểm và tăng GMV Seller")
        void onTransaction_bothPartiesGain1AndUpdateCounters() {
            Long sellerId = 1L;
            Long buyerId = 2L;
            UserReputationScore sellerScore = createSampleScore(sellerId, new BigDecimal("3.00"), 2, 2);
            sellerScore.setTotalGmv(new BigDecimal("100000"));
            UserReputationScore buyerScore = createSampleScore(buyerId, new BigDecimal("2.00"), 1, 1);

            when(reputationRepository.findByUserId(sellerId)).thenReturn(Optional.of(sellerScore));
            when(reputationRepository.findByUserId(buyerId)).thenReturn(Optional.of(buyerScore));

            TicketTransactionCompletedEvent event = createTransactionEvent(sellerId, buyerId, new BigDecimal("250000"));
            updateReputationUseCase.onTicketTransactionCompleted(event);

            assertThat(sellerScore.getScore()).isEqualByComparingTo("4.00");
            assertThat(sellerScore.getTotalTransactions()).isEqualTo(3);
            assertThat(sellerScore.getSuccessfulTransactions()).isEqualTo(3);
            assertThat(sellerScore.getTotalGmv()).isEqualByComparingTo("350000.00");
            assertThat(sellerScore.getLastTransactionAt()).isEqualTo(event.getOccurredAt());

            assertThat(buyerScore.getScore()).isEqualByComparingTo("3.00");
            assertThat(buyerScore.getTotalTransactions()).isEqualTo(2);
            assertThat(buyerScore.getSuccessfulTransactions()).isEqualTo(2);
            assertThat(buyerScore.getTotalGmv()).isEqualByComparingTo(BigDecimal.ZERO);

            verify(reputationRepository, times(2)).save(any(UserReputationScore.class));
            verify(eventPublisher, never()).publishEvent(any(ReputationDepletedEvent.class));
        }

        @Test
        @DisplayName("5. onTransaction: điểm chặn trần 5.00 (5.00 + 1 -> 5.00)")
        void onTransaction_scoreClampedAt5() {
            Long sellerId = 1L;
            Long buyerId = 2L;
            UserReputationScore sellerScore = createSampleScore(sellerId, new BigDecimal("5.00"), 10, 10);
            UserReputationScore buyerScore = createSampleScore(buyerId, new BigDecimal("4.00"), 5, 5);

            when(reputationRepository.findByUserId(sellerId)).thenReturn(Optional.of(sellerScore));
            when(reputationRepository.findByUserId(buyerId)).thenReturn(Optional.of(buyerScore));

            updateReputationUseCase.onTicketTransactionCompleted(
                    createTransactionEvent(sellerId, buyerId, new BigDecimal("100000")));

            assertThat(sellerScore.getScore()).isEqualByComparingTo("5.00");
            assertThat(buyerScore.getScore()).isEqualByComparingTo("5.00");
            verify(reputationRepository, times(2)).save(any(UserReputationScore.class));
        }

        @Test
        @DisplayName("6. onTransaction: DISPUTE_RESOLVED_SELLER_WINS chỉ tăng GMV/count Seller, không cộng điểm")
        void onTransaction_disputeResolvedSellerWins_onlyUpdatesSellerGmv() {
            Long sellerId = 1L;
            Long buyerId = 2L;
            UserReputationScore sellerScore = createSampleScore(sellerId, new BigDecimal("4.00"), 3, 3);
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
            assertThat(sellerScore.getScore()).isEqualByComparingTo("4.00");
            verify(reputationRepository, never()).findByUserId(buyerId);
        }

        @Test
        @DisplayName("onTransaction: bỏ qua khi event null")
        void onTransaction_nullEvent_ignored() {
            updateReputationUseCase.onTicketTransactionCompleted(null);
            verify(reputationRepository, never()).findByUserId(any());
        }
    }

    @Nested
    @DisplayName("Nhóm onDisputeResolved (Thua -2, Thắng +0, chạm 0 phát ReputationDepletedEvent)")
    class DisputeResolvedTests {

        @Test
        @DisplayName("7. BUYER_WINS: Seller -2 điểm (4 -> 2), Buyer giữ nguyên điểm (+0, 3 -> 3)")
        void onDispute_buyerWins_sellerLoses2AndBuyerKeepsScore() {
            Long sellerId = 1L;
            Long buyerId = 2L;
            UserReputationScore sellerScore = createSampleScore(sellerId, new BigDecimal("4.00"), 5, 5);
            UserReputationScore buyerScore = createSampleScore(buyerId, new BigDecimal("3.00"), 5, 5);

            when(reputationRepository.findByUserId(sellerId)).thenReturn(Optional.of(sellerScore));
            when(reputationRepository.findByUserId(buyerId)).thenReturn(Optional.of(buyerScore));

            updateReputationUseCase.onDisputeResolved(createDisputeEvent(sellerId, buyerId, DisputeOutcome.BUYER_WINS));

            assertThat(sellerScore.getScore()).isEqualByComparingTo("2.00");
            assertThat(sellerScore.getDisputesLost()).isEqualTo(1);
            assertThat(buyerScore.getScore()).isEqualByComparingTo("3.00");
            assertThat(buyerScore.getDisputesWon()).isEqualTo(1);

            verify(reputationRepository, times(2)).save(any(UserReputationScore.class));
            verify(eventPublisher, never()).publishEvent(any(ReputationDepletedEvent.class));
        }

        @Test
        @DisplayName("8. SELLER_WINS: Buyer -2 điểm (4 -> 2), Seller giữ nguyên điểm (+0, 3 -> 3)")
        void onDispute_sellerWins_buyerLoses2AndSellerKeepsScore() {
            Long sellerId = 1L;
            Long buyerId = 2L;
            UserReputationScore sellerScore = createSampleScore(sellerId, new BigDecimal("3.00"), 5, 5);
            UserReputationScore buyerScore = createSampleScore(buyerId, new BigDecimal("4.00"), 5, 5);

            when(reputationRepository.findByUserId(sellerId)).thenReturn(Optional.of(sellerScore));
            when(reputationRepository.findByUserId(buyerId)).thenReturn(Optional.of(buyerScore));

            updateReputationUseCase.onDisputeResolved(createDisputeEvent(sellerId, buyerId, DisputeOutcome.SELLER_WINS));

            assertThat(sellerScore.getScore()).isEqualByComparingTo("3.00");
            assertThat(sellerScore.getDisputesWon()).isEqualTo(1);
            assertThat(buyerScore.getScore()).isEqualByComparingTo("2.00");
            assertThat(buyerScore.getDisputesLost()).isEqualTo(1);
        }

        @Test
        @DisplayName("9. BUYER_WINS khi Seller đang 1.00 -> sàn 0.00 và PHÁT ReputationDepletedEvent")
        void onDispute_scoreDropsToZero_publishesReputationDepletedEvent() {
            Long sellerId = 1L;
            Long buyerId = 2L;
            UserReputationScore sellerScore = createSampleScore(sellerId, new BigDecimal("1.00"), 5, 1);
            UserReputationScore buyerScore = createSampleScore(buyerId, new BigDecimal("3.00"), 5, 5);

            when(reputationRepository.findByUserId(sellerId)).thenReturn(Optional.of(sellerScore));
            when(reputationRepository.findByUserId(buyerId)).thenReturn(Optional.of(buyerScore));

            updateReputationUseCase.onDisputeResolved(createDisputeEvent(sellerId, buyerId, DisputeOutcome.BUYER_WINS));

            assertThat(sellerScore.getScore()).isEqualByComparingTo("0.00");

            ArgumentCaptor<ReputationDepletedEvent> eventCaptor = ArgumentCaptor.forClass(ReputationDepletedEvent.class);
            verify(eventPublisher, times(1)).publishEvent(eventCaptor.capture());
            assertThat(eventCaptor.getValue().getUserId()).isEqualTo(sellerId);
            assertThat(eventCaptor.getValue().getScoreBefore()).isEqualByComparingTo("1.00");
            assertThat(eventCaptor.getValue().getScoreAfter()).isEqualByComparingTo("0.00");
            assertThat(eventCaptor.getValue().getReasonCode()).isEqualTo(UpdateReputationUseCase.REASON_DISPUTE_LOST);
            assertThat(eventCaptor.getValue().getDisputeId()).isEqualTo(50L);
        }

        @Test
        @DisplayName("10. Không phát lặp ReputationDepletedEvent nếu điểm trước đó đã ở 0.00")
        void onDispute_alreadyAtZero_doesNotRepublishDepletedEvent() {
            Long sellerId = 1L;
            Long buyerId = 2L;
            UserReputationScore sellerScore = createSampleScore(sellerId, new BigDecimal("0.00"), 5, 1);
            UserReputationScore buyerScore = createSampleScore(buyerId, new BigDecimal("3.00"), 5, 5);

            when(reputationRepository.findByUserId(sellerId)).thenReturn(Optional.of(sellerScore));
            when(reputationRepository.findByUserId(buyerId)).thenReturn(Optional.of(buyerScore));

            updateReputationUseCase.onDisputeResolved(createDisputeEvent(sellerId, buyerId, DisputeOutcome.BUYER_WINS));

            assertThat(sellerScore.getScore()).isEqualByComparingTo("0.00");
            verify(eventPublisher, never()).publishEvent(any(ReputationDepletedEvent.class));
        }

        @Test
        @DisplayName("11. WITHDRAWN: không đổi điểm, chỉ tăng disputesOpenedAgainst cho Seller")
        void onDispute_withdrawn_noScoreChange() {
            Long sellerId = 1L;
            Long buyerId = 2L;
            UserReputationScore sellerScore = createSampleScore(sellerId, new BigDecimal("4.00"), 5, 5);
            when(reputationRepository.findByUserId(sellerId)).thenReturn(Optional.of(sellerScore));

            updateReputationUseCase.onDisputeResolved(createDisputeEvent(sellerId, buyerId, DisputeOutcome.WITHDRAWN));

            assertThat(sellerScore.getScore()).isEqualByComparingTo("4.00");
            assertThat(sellerScore.getDisputesOpenedAgainst()).isEqualTo(1);
            verify(reputationRepository, never()).findByUserId(buyerId);
            verify(reputationRepository).save(sellerScore);
        }

        @Test
        @DisplayName("onDispute: bỏ qua khi event null")
        void onDispute_nullEventOrOutcome_ignored() {
            updateReputationUseCase.onDisputeResolved(null);
            verify(reputationRepository, never()).findByUserId(any());
        }
    }

    @Nested
    @DisplayName("Nhóm recordCancelledListing & recordDepositTimeout (-1 điểm)")
    class PenaltyTests {

        @Test
        @DisplayName("12. recordCancelledListing: trừ 1 điểm (4 -> 3) và tăng cancelledListings")
        void cancelledListing_minus1Point() {
            Long sellerId = 1L;
            UserReputationScore sellerScore = createSampleScore(sellerId, new BigDecimal("4.00"), 5, 5);
            when(reputationRepository.findByUserId(sellerId)).thenReturn(Optional.of(sellerScore));

            updateReputationUseCase.recordCancelledListing(sellerId, 77L);

            assertThat(sellerScore.getScore()).isEqualByComparingTo("3.00");
            assertThat(sellerScore.getCancelledListings()).isEqualTo(1);
            verify(reputationRepository).save(sellerScore);
            verify(eventPublisher, never()).publishEvent(any(ReputationDepletedEvent.class));
        }

        @Test
        @DisplayName("13. recordCancelledListing từ 1.00 -> 0.00 phát ReputationDepletedEvent với đúng reasonCode")
        void cancelledListing_reachesZero_publishesDepletedEvent() {
            Long sellerId = 1L;
            UserReputationScore sellerScore = createSampleScore(sellerId, new BigDecimal("1.00"), 5, 1);
            when(reputationRepository.findByUserId(sellerId)).thenReturn(Optional.of(sellerScore));

            updateReputationUseCase.recordCancelledListing(sellerId, 78L);

            assertThat(sellerScore.getScore()).isEqualByComparingTo("0.00");
            assertThat(sellerScore.getCancelledListings()).isEqualTo(1);
            ArgumentCaptor<ReputationDepletedEvent> captor = ArgumentCaptor.forClass(ReputationDepletedEvent.class);
            verify(eventPublisher, times(1)).publishEvent(captor.capture());
            assertThat(captor.getValue().getReasonCode()).isEqualTo(UpdateReputationUseCase.REASON_CANCELLED_LISTING);
            assertThat(captor.getValue().getTicketId()).isEqualTo(78L);
        }

        @Test
        @DisplayName("14. recordDepositTimeout: trừ 1 điểm (3 -> 2) và tăng cancelledListings")
        void depositTimeout_minus1Point() {
            Long sellerId = 1L;
            UserReputationScore sellerScore = createSampleScore(sellerId, new BigDecimal("3.00"), 5, 5);
            when(reputationRepository.findByUserId(sellerId)).thenReturn(Optional.of(sellerScore));

            updateReputationUseCase.recordDepositTimeout(sellerId, 101L);

            assertThat(sellerScore.getScore()).isEqualByComparingTo("2.00");
            assertThat(sellerScore.getCancelledListings()).isEqualTo(1);
            assertThat(sellerScore.getDisputesLost()).isEqualTo(0);
            verify(reputationRepository).save(sellerScore);
            verify(eventPublisher, never()).publishEvent(any(ReputationDepletedEvent.class));
        }

        @Test
        @DisplayName("15. recordDepositTimeout từ 1.00 -> 0.00 phát ReputationDepletedEvent với đúng reasonCode & ticketId")
        void depositTimeout_reachesZero_publishesDepletedEvent() {
            Long sellerId = 1L;
            UserReputationScore sellerScore = createSampleScore(sellerId, new BigDecimal("1.00"), 5, 1);
            when(reputationRepository.findByUserId(sellerId)).thenReturn(Optional.of(sellerScore));

            updateReputationUseCase.recordDepositTimeout(sellerId, 102L);

            assertThat(sellerScore.getScore()).isEqualByComparingTo("0.00");
            ArgumentCaptor<ReputationDepletedEvent> captor = ArgumentCaptor.forClass(ReputationDepletedEvent.class);
            verify(eventPublisher, times(1)).publishEvent(captor.capture());
            assertThat(captor.getValue().getReasonCode()).isEqualTo(UpdateReputationUseCase.REASON_DEPOSIT_TIMEOUT);
            assertThat(captor.getValue().getTicketId()).isEqualTo(102L);
        }

        @Test
        @DisplayName("recordCancelledListing & recordDepositTimeout: ném lỗi khi sellerId null")
        void nullSellerId_throwsException() {
            assertThatThrownBy(() -> updateReputationUseCase.recordCancelledListing(null))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> updateReputationUseCase.recordDepositTimeout(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("Nhóm Concurrency & Retry (Optimistic Locking)")
    class ConcurrencyAndRetryTests {

        @Test
        @DisplayName("16. Retry: gặp OptimisticLockingFailureException lần 1 -> retry thành công lần 2 (-1 điểm)")
        void retry_optimisticLockingConflict_retriesAndSucceeds() {
            Long sellerId = 1L;
            UserReputationScore initialScore = createSampleScore(sellerId, new BigDecimal("4.00"), 1, 1);
            UserReputationScore freshScore = createSampleScore(sellerId, new BigDecimal("4.00"), 1, 1);

            when(reputationRepository.findByUserId(sellerId))
                    .thenReturn(Optional.of(initialScore))
                    .thenReturn(Optional.of(freshScore));

            when(reputationRepository.save(any(UserReputationScore.class)))
                    .thenThrow(new OptimisticLockingFailureException("Version conflict on attempt 1"))
                    .thenReturn(freshScore);

            updateReputationUseCase.recordCancelledListing(sellerId);

            verify(reputationRepository, times(2)).save(any(UserReputationScore.class));
            assertThat(freshScore.getScore()).isEqualByComparingTo("3.00");
            assertThat(freshScore.getCancelledListings()).isEqualTo(1);
        }

        @Test
        @DisplayName("17. Retry: vượt quá MAX_RETRY (3 lần) thì ném OptimisticLockingFailureException ra ngoài")
        void retry_exceedsMaxRetry_throwsException() {
            Long sellerId = 1L;
            UserReputationScore score = createSampleScore(sellerId, new BigDecimal("4.00"), 1, 1);

            when(reputationRepository.findByUserId(sellerId)).thenReturn(Optional.of(score));
            when(reputationRepository.save(any(UserReputationScore.class)))
                    .thenThrow(new OptimisticLockingFailureException("Persistent version conflict"));

            assertThatThrownBy(() -> updateReputationUseCase.recordCancelledListing(sellerId))
                    .isInstanceOf(OptimisticLockingFailureException.class);

            verify(reputationRepository, times(3)).save(any(UserReputationScore.class));
        }
    }
}
