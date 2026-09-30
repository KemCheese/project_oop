package com.yourapp.ticketpass.reputation.application;

import com.yourapp.ticketpass.reputation.domain.ReputationRepository;
import com.yourapp.ticketpass.reputation.domain.UserReputationScore;
import com.yourapp.ticketpass.shared.event.DisputeOutcome;
import com.yourapp.ticketpass.shared.event.DisputeResolvedEvent;
import com.yourapp.ticketpass.shared.event.TicketTransactionCompletedEvent;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Cập nhật điểm uy tín người dùng dựa trên các sự kiện nghiệp vụ của sàn.
 *
 * <p><b>Nguyên tắc 2 - Observer pattern:</b> module {@code payment} và {@code dispute}
 * <b>KHÔNG</b> gọi trực tiếp module này. Khi một giao dịch hoàn tất hoặc một tranh chấp
 * được phân xử, các module đó chỉ publish sự kiện qua {@code ApplicationEventPublisher};
 * lớp này đăng ký lắng nghe và tự cập nhật điểm. Nhờ vậy có thể thêm bớt hệ quả của một sự
 * kiện (gửi email, ghi log kiểm toán, tính điểm) mà không phải sửa code của module phát
 * sinh sự kiện.</p>
 *
 * <p><b>Vì sao dùng {@code AFTER_COMMIT}:</b> điểm uy tín chỉ được phép thay đổi khi
 * transaction tiền tệ đã commit thành công. Nếu lắng nghe đồng bộ trong cùng transaction,
 * một lỗi khi giải ngân sẽ rollback tiền nhưng điểm có thể đã bị thay đổi sai lệch, hoặc
 * ngược lại lỗi khi tính điểm lại làm hỏng cả giao dịch tiền. Tách bằng
 * {@link TransactionPhase#AFTER_COMMIT} giữ cho hai việc độc lập.</p>
 *
 * <p><b>Quy tắc cộng/trừ điểm ĐỀ XUẤT</b> (Prompt 2 hiện thực, thang 0-100):</p>
 * <ul>
 *   <li>Giao dịch hoàn tất: <b>+2</b> điểm, không vượt trần 100.</li>
 *   <li>Thua tranh chấp: <b>-15</b> điểm.</li>
 *   <li>Thắng tranh chấp: <b>+1</b> điểm.</li>
 *   <li>Huỷ listing sau khi người mua đã thanh toán: <b>-10</b> điểm.</li>
 *   <li>Điểm không bao giờ xuống dưới sàn 0.</li>
 * </ul>
 *
 * <p>Module: reputation</p>
 *
 * Chủ sở hữu: Người C
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UpdateReputationUseCase {

    private static final BigDecimal MAX_SCORE = new BigDecimal("100.00");
    private static final BigDecimal MIN_SCORE = new BigDecimal("0.00");
    private static final BigDecimal INITIAL_SCORE = new BigDecimal("100.00");
    private static final BigDecimal POINTS_TRANSACTION_COMPLETED = new BigDecimal("2");
    private static final BigDecimal POINTS_DISPUTE_WON = BigDecimal.ONE;
    private static final BigDecimal POINTS_DISPUTE_LOST = new BigDecimal("15");
    private static final BigDecimal POINTS_LISTING_CANCELLED = new BigDecimal("10");
    private static final int MAX_RETRY = 3;

    private final ReputationRepository reputationRepository;

    /**
     * Lắng nghe sự kiện giao dịch vé hoàn tất để cộng điểm cho cả người bán và người mua.
     *
     * @param event sự kiện do module payment publish sau khi giải ngân xong
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTicketTransactionCompleted(TicketTransactionCompletedEvent event) {
        if (event == null) {
            log.warn("Received null TicketTransactionCompletedEvent, ignoring");
            return;
        }

        log.info("Processing TicketTransactionCompletedEvent for ticketId={}, sellerId={}, buyerId={}",
                event.getTicketId(), event.getSellerId(), event.getBuyerId());

        Instant occurredAt = event.getOccurredAt() != null ? event.getOccurredAt() : Instant.now();

        // 1. Cập nhật uy tín người bán: +2 điểm, tăng transactions, cộng GMV, ghi nhận lastTransactionAt
        if (event.getSellerId() != null) {
            updateWithRetry(event.getSellerId(), rep -> {
                rep.setScore(clampScore(getScoreOrDefault(rep).add(POINTS_TRANSACTION_COMPLETED)));
                rep.setTotalTransactions(getOrDefault(rep.getTotalTransactions()) + 1);
                rep.setSuccessfulTransactions(getOrDefault(rep.getSuccessfulTransactions()) + 1);
                BigDecimal currentGmv = rep.getTotalGmv() != null ? rep.getTotalGmv() : BigDecimal.ZERO;
                BigDecimal salePrice = event.getSalePrice() != null ? event.getSalePrice() : BigDecimal.ZERO;
                rep.setTotalGmv(currentGmv.add(salePrice).setScale(2, RoundingMode.HALF_UP));
                rep.setLastTransactionAt(occurredAt);
            });
        }

        // 2. Cập nhật uy tín người mua: +2 điểm, tăng transactions, ghi nhận lastTransactionAt (không cộng GMV)
        if (event.getBuyerId() != null) {
            updateWithRetry(event.getBuyerId(), rep -> {
                rep.setScore(clampScore(getScoreOrDefault(rep).add(POINTS_TRANSACTION_COMPLETED)));
                rep.setTotalTransactions(getOrDefault(rep.getTotalTransactions()) + 1);
                rep.setSuccessfulTransactions(getOrDefault(rep.getSuccessfulTransactions()) + 1);
                rep.setLastTransactionAt(occurredAt);
            });
        }
    }

    /**
     * Lắng nghe sự kiện tranh chấp được phân xử để cộng/trừ điểm hai bên.
     *
     * @param event sự kiện do module dispute publish sau khi admin ra quyết định
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDisputeResolved(DisputeResolvedEvent event) {
        if (event == null || event.getOutcome() == null) {
            log.warn("Received null DisputeResolvedEvent or outcome, ignoring");
            return;
        }

        log.info("Processing DisputeResolvedEvent for disputeId={}, outcome={}, sellerId={}, buyerId={}",
                event.getDisputeId(), event.getOutcome(), event.getSellerId(), event.getBuyerId());

        switch (event.getOutcome()) {
            case BUYER_WINS -> {
                // Người mua thắng: seller -15 điểm, tăng disputesLost; buyer +1 điểm, tăng disputesWon
                if (event.getSellerId() != null) {
                    updateWithRetry(event.getSellerId(), rep -> {
                        rep.setScore(clampScore(getScoreOrDefault(rep).subtract(POINTS_DISPUTE_LOST)));
                        rep.setDisputesLost(getOrDefault(rep.getDisputesLost()) + 1);
                    });
                }
                if (event.getBuyerId() != null) {
                    updateWithRetry(event.getBuyerId(), rep -> {
                        rep.setScore(clampScore(getScoreOrDefault(rep).add(POINTS_DISPUTE_WON)));
                        rep.setDisputesWon(getOrDefault(rep.getDisputesWon()) + 1);
                    });
                }
            }
            case SELLER_WINS -> {
                // Người bán thắng: seller +1 điểm, tăng disputesWon; buyer -15 điểm, tăng disputesLost
                if (event.getSellerId() != null) {
                    updateWithRetry(event.getSellerId(), rep -> {
                        rep.setScore(clampScore(getScoreOrDefault(rep).add(POINTS_DISPUTE_WON)));
                        rep.setDisputesWon(getOrDefault(rep.getDisputesWon()) + 1);
                    });
                }
                if (event.getBuyerId() != null) {
                    updateWithRetry(event.getBuyerId(), rep -> {
                        rep.setScore(clampScore(getScoreOrDefault(rep).subtract(POINTS_DISPUTE_LOST)));
                        rep.setDisputesLost(getOrDefault(rep.getDisputesLost()) + 1);
                    });
                }
            }
            case WITHDRAWN -> {
                // Rút đơn khiếu nại: không đổi điểm, chỉ tăng disputesOpenedAgainst cho seller
                if (event.getSellerId() != null) {
                    updateWithRetry(event.getSellerId(), rep -> {
                        rep.setDisputesOpenedAgainst(getOrDefault(rep.getDisputesOpenedAgainst()) + 1);
                    });
                }
            }
        }
    }

    /**
     * Lấy bản ghi điểm của một người dùng, tạo mới nếu chưa có.
     *
     * @param userId id thuần của người dùng
     * @return bản ghi điểm hiện có hoặc bản ghi vừa khởi tạo
     */
    @Transactional
    public UserReputationScore getOrCreate(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null");
        }
        return reputationRepository.findByUserId(userId)
                .orElseGet(() -> {
                    UserReputationScore newScore = new UserReputationScore();
                    newScore.setUserId(userId);
                    newScore.setScore(INITIAL_SCORE);
                    newScore.setTotalTransactions(0);
                    newScore.setSuccessfulTransactions(0);
                    newScore.setCancelledListings(0);
                    newScore.setDisputesOpenedAgainst(0);
                    newScore.setDisputesLost(0);
                    newScore.setDisputesWon(0);
                    newScore.setTotalGmv(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
                    return reputationRepository.save(newScore);
                });
    }

    /**
     * Ghi nhận việc người bán huỷ vé đang rao sau khi người mua đã thanh toán.
     *
     * @param sellerId id thuần của người bán
     */
    @Transactional
    public void recordCancelledListing(Long sellerId) {
        if (sellerId == null) {
            throw new IllegalArgumentException("sellerId must not be null");
        }
        log.info("Recording cancelled listing for sellerId={}", sellerId);
        updateWithRetry(sellerId, rep -> {
            rep.setScore(clampScore(getScoreOrDefault(rep).subtract(POINTS_LISTING_CANCELLED)));
            rep.setCancelledListings(getOrDefault(rep.getCancelledListings()) + 1);
        });
    }

    /**
     * Thực hiện cập nhật điểm uy tín kèm cơ chế retry xử lý OptimisticLockingFailureException.
     *
     * @param userId id người dùng cần cập nhật
     * @param updateAction lambda cập nhật các trường của entity
     */
    private void updateWithRetry(Long userId, Consumer<UserReputationScore> updateAction) {
        int attempts = 0;
        while (attempts < MAX_RETRY) {
            attempts++;
            try {
                UserReputationScore rep = getOrCreate(userId);
                updateAction.accept(rep);
                reputationRepository.save(rep);
                return;
            } catch (OptimisticLockingFailureException ex) {
                log.warn("Optimistic locking conflict when updating reputation for userId {} (attempt {}/{}): {}",
                        userId, attempts, MAX_RETRY, ex.getMessage());
                if (attempts >= MAX_RETRY) {
                    log.error("Failed to update reputation for userId {} after {} attempts due to concurrent modifications",
                            userId, MAX_RETRY, ex);
                    throw ex;
                }
            }
        }
    }

    private BigDecimal clampScore(BigDecimal score) {
        if (score == null) {
            return INITIAL_SCORE;
        }
        if (score.compareTo(MAX_SCORE) > 0) {
            return MAX_SCORE;
        }
        if (score.compareTo(MIN_SCORE) < 0) {
            return MIN_SCORE;
        }
        return score.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal getScoreOrDefault(UserReputationScore rep) {
        return rep.getScore() != null ? rep.getScore() : INITIAL_SCORE;
    }

    private int getOrDefault(Integer val) {
        return val != null ? val : 0;
    }
}
