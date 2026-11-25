package com.yourapp.ticketpass.reputation.application;

import com.yourapp.ticketpass.reputation.domain.ReputationRepository;
import com.yourapp.ticketpass.reputation.domain.UserReputationScore;
import com.yourapp.ticketpass.shared.event.CompletionReason;
import com.yourapp.ticketpass.shared.event.DisputeResolvedEvent;
import com.yourapp.ticketpass.shared.event.ReputationDepletedEvent;
import com.yourapp.ticketpass.shared.event.TicketTransactionCompletedEvent;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
public class UpdateReputationUseCase {

    public static final BigDecimal MAX_SCORE = new BigDecimal("5.00");
    public static final BigDecimal MIN_SCORE = new BigDecimal("0.00");
    public static final BigDecimal INITIAL_SCORE = new BigDecimal("3.00");
    private static final BigDecimal POINTS_TRANSACTION_COMPLETED = BigDecimal.ONE;
    private static final BigDecimal POINTS_DISPUTE_WON = BigDecimal.ZERO;
    private static final BigDecimal POINTS_DISPUTE_LOST = new BigDecimal("2");
    private static final BigDecimal POINTS_LISTING_CANCELLED = BigDecimal.ONE;
    private static final BigDecimal POINTS_DEPOSIT_TIMEOUT = BigDecimal.ONE;
    private static final int MAX_RETRY = 3;

    public static final String REASON_TRANSACTION_COMPLETED = "TRANSACTION_COMPLETED";
    public static final String REASON_DISPUTE_WON = "DISPUTE_WON";
    public static final String REASON_DISPUTE_LOST = "DISPUTE_LOST";
    public static final String REASON_CANCELLED_LISTING = "CANCELLED_LISTING";
    public static final String REASON_DEPOSIT_TIMEOUT = "DEPOSIT_TIMEOUT";

    private final ReputationRepository reputationRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate requiresNewTxTemplate;

    @Autowired
    public UpdateReputationUseCase(ReputationRepository reputationRepository,
                                   ApplicationEventPublisher eventPublisher,
                                   @Autowired(required = false) PlatformTransactionManager transactionManager) {
        this.reputationRepository = reputationRepository;
        this.eventPublisher = eventPublisher;
        if (transactionManager != null) {
            TransactionTemplate tpl = new TransactionTemplate(transactionManager);
            tpl.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            this.requiresNewTxTemplate = tpl;
        } else {
            this.requiresNewTxTemplate = null;
        }
    }

    public UpdateReputationUseCase(ReputationRepository reputationRepository,
                                   ApplicationEventPublisher eventPublisher) {
        this(reputationRepository, eventPublisher, null);
    }

    public UpdateReputationUseCase(ReputationRepository reputationRepository) {
        this(reputationRepository, null, null);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTicketTransactionCompleted(TicketTransactionCompletedEvent event) {
        if (event == null) {
            log.warn("Received null TicketTransactionCompletedEvent, ignoring");
            return;
        }

        log.info("Processing TicketTransactionCompletedEvent for ticketId={}, sellerId={}, buyerId={}, reason={}",
                event.getTicketId(), event.getSellerId(), event.getBuyerId(), event.getCompletionReason());

        Instant occurredAt = event.getOccurredAt() != null ? event.getOccurredAt() : Instant.now();

        if (event.getCompletionReason() == CompletionReason.DISPUTE_RESOLVED_SELLER_WINS) {
            if (event.getSellerId() != null) {
                updateMetadataOnlyWithRetry(event.getSellerId(), rep -> {
                    rep.setTotalTransactions(getOrDefault(rep.getTotalTransactions()) + 1);
                    rep.setSuccessfulTransactions(getOrDefault(rep.getSuccessfulTransactions()) + 1);
                    BigDecimal currentGmv = rep.getTotalGmv() != null ? rep.getTotalGmv() : BigDecimal.ZERO;
                    BigDecimal salePrice = event.getSalePrice() != null ? event.getSalePrice() : BigDecimal.ZERO;
                    rep.setTotalGmv(currentGmv.add(salePrice).setScale(2, RoundingMode.HALF_UP));
                    rep.setLastTransactionAt(occurredAt);
                });
            }
            return;
        }

        String ticketRef = event.getTicketId() != null ? "vé #" + event.getTicketId() : "giao dịch";

        if (event.getSellerId() != null) {
            applyScoreChangeWithRetry(
                    event.getSellerId(),
                    POINTS_TRANSACTION_COMPLETED,
                    REASON_TRANSACTION_COMPLETED,
                    "Hoàn tất bán " + ticketRef + " thành công (+1 điểm)",
                    event.getTicketId(),
                    null,
                    occurredAt,
                    rep -> {
                        rep.setTotalTransactions(getOrDefault(rep.getTotalTransactions()) + 1);
                        rep.setSuccessfulTransactions(getOrDefault(rep.getSuccessfulTransactions()) + 1);
                        BigDecimal currentGmv = rep.getTotalGmv() != null ? rep.getTotalGmv() : BigDecimal.ZERO;
                        BigDecimal salePrice = event.getSalePrice() != null ? event.getSalePrice() : BigDecimal.ZERO;
                        rep.setTotalGmv(currentGmv.add(salePrice).setScale(2, RoundingMode.HALF_UP));
                        rep.setLastTransactionAt(occurredAt);
                    });
        }

        if (event.getBuyerId() != null) {
            applyScoreChangeWithRetry(
                    event.getBuyerId(),
                    POINTS_TRANSACTION_COMPLETED,
                    REASON_TRANSACTION_COMPLETED,
                    "Hoàn tất mua " + ticketRef + " thành công (+1 điểm)",
                    event.getTicketId(),
                    null,
                    occurredAt,
                    rep -> {
                        rep.setTotalTransactions(getOrDefault(rep.getTotalTransactions()) + 1);
                        rep.setSuccessfulTransactions(getOrDefault(rep.getSuccessfulTransactions()) + 1);
                        rep.setLastTransactionAt(occurredAt);
                    });
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDisputeResolved(DisputeResolvedEvent event) {
        if (event == null || event.getOutcome() == null) {
            log.warn("Received null DisputeResolvedEvent or outcome, ignoring");
            return;
        }

        log.info("Processing DisputeResolvedEvent for disputeId={}, outcome={}, sellerId={}, buyerId={}",
                event.getDisputeId(), event.getOutcome(), event.getSellerId(), event.getBuyerId());

        Instant occurredAt = event.getOccurredAt() != null ? event.getOccurredAt() : Instant.now();
        String ticketRef = event.getTicketId() != null ? "vé #" + event.getTicketId() : "tranh chấp";

        switch (event.getOutcome()) {
            case BUYER_WINS -> {
                if (event.getSellerId() != null) {
                    applyScoreChangeWithRetry(
                            event.getSellerId(),
                            POINTS_DISPUTE_LOST.negate(),
                            REASON_DISPUTE_LOST,
                            "Thua tranh chấp " + ticketRef + " (-2 điểm)",
                            event.getTicketId(),
                            event.getDisputeId(),
                            occurredAt,
                            rep -> rep.setDisputesLost(getOrDefault(rep.getDisputesLost()) + 1));
                }
                if (event.getBuyerId() != null) {
                    applyScoreChangeWithRetry(
                            event.getBuyerId(),
                            POINTS_DISPUTE_WON,
                            REASON_DISPUTE_WON,
                            "Thắng tranh chấp " + ticketRef + " (giữ nguyên điểm)",
                            event.getTicketId(),
                            event.getDisputeId(),
                            occurredAt,
                            rep -> rep.setDisputesWon(getOrDefault(rep.getDisputesWon()) + 1));
                }
            }
            case SELLER_WINS -> {
                if (event.getSellerId() != null) {
                    applyScoreChangeWithRetry(
                            event.getSellerId(),
                            POINTS_DISPUTE_WON,
                            REASON_DISPUTE_WON,
                            "Thắng tranh chấp " + ticketRef + " (giữ nguyên điểm)",
                            event.getTicketId(),
                            event.getDisputeId(),
                            occurredAt,
                            rep -> rep.setDisputesWon(getOrDefault(rep.getDisputesWon()) + 1));
                }
                if (event.getBuyerId() != null) {
                    applyScoreChangeWithRetry(
                            event.getBuyerId(),
                            POINTS_DISPUTE_LOST.negate(),
                            REASON_DISPUTE_LOST,
                            "Thua tranh chấp " + ticketRef + " (-2 điểm)",
                            event.getTicketId(),
                            event.getDisputeId(),
                            occurredAt,
                            rep -> rep.setDisputesLost(getOrDefault(rep.getDisputesLost()) + 1));
                }
            }
            case WITHDRAWN -> {
                if (event.getSellerId() != null) {
                    updateMetadataOnlyWithRetry(event.getSellerId(), rep ->
                            rep.setDisputesOpenedAgainst(getOrDefault(rep.getDisputesOpenedAgainst()) + 1));
                }
            }
        }
    }

    @Transactional
    public UserReputationScore getOrCreate(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null");
        }
        return findOrCreateInsideCurrentTx(userId);
    }

    public void recordCancelledListing(Long sellerId, Long ticketId) {
        if (sellerId == null) {
            throw new IllegalArgumentException("sellerId must not be null");
        }
        String ticketRef = ticketId != null ? "vé #" + ticketId : "vé đang bán";
        log.info("Recording cancelled listing (-1) for sellerId={}, ticketId={}", sellerId, ticketId);
        applyScoreChangeWithRetry(
                sellerId,
                POINTS_LISTING_CANCELLED.negate(),
                REASON_CANCELLED_LISTING,
                "Hủy " + ticketRef + " sau khi người mua đã thanh toán (-1 điểm)",
                ticketId,
                null,
                Instant.now(),
                rep -> rep.setCancelledListings(getOrDefault(rep.getCancelledListings()) + 1));
    }

    public void recordCancelledListing(Long sellerId) {
        recordCancelledListing(sellerId, null);
    }

    public void recordDepositTimeout(Long sellerId, Long ticketId) {
        if (sellerId == null) {
            throw new IllegalArgumentException("sellerId must not be null");
        }
        String ticketRef = ticketId != null ? "vé #" + ticketId : "giao dịch";
        log.warn("[Reputation] Áp chế tài trừ 1 điểm uy tín đối với sellerId={} do quá hạn nạp cọc 30 phút (ticketId={})",
                sellerId, ticketId);
        applyScoreChangeWithRetry(
                sellerId,
                POINTS_DEPOSIT_TIMEOUT.negate(),
                REASON_DEPOSIT_TIMEOUT,
                "Quá hạn nạp cọc 50% (30 phút) cho " + ticketRef + " (-1 điểm)",
                ticketId,
                null,
                Instant.now(),
                rep -> rep.setCancelledListings(getOrDefault(rep.getCancelledListings()) + 1));
    }

    public void recordDepositTimeout(Long sellerId) {
        recordDepositTimeout(sellerId, null);
    }

    private void applyScoreChangeWithRetry(Long userId,
                                           BigDecimal rawDelta,
                                           String reasonCode,
                                           String description,
                                           Long ticketId,
                                           Long disputeId,
                                           Instant occurredAt,
                                           Consumer<UserReputationScore> extraMutation) {
        int attempts = 0;
        while (attempts < MAX_RETRY) {
            attempts++;
            try {
                runInNewTransaction(() -> applyScoreChangeOnce(
                        userId, rawDelta, reasonCode, description, ticketId, disputeId, occurredAt, extraMutation));
                return;
            } catch (OptimisticLockingFailureException | DataIntegrityViolationException ex) {
                log.warn("Concurrent conflict when updating reputation for userId {} (attempt {}/{}): {}",
                        userId, attempts, MAX_RETRY, ex.getMessage());
                if (attempts >= MAX_RETRY) {
                    log.error("Failed to update reputation for userId {} after {} attempts due to concurrent modifications",
                            userId, MAX_RETRY, ex);
                    throw ex;
                }
            }
        }
    }

    private void applyScoreChangeOnce(Long userId,
                                      BigDecimal rawDelta,
                                      String reasonCode,
                                      String description,
                                      Long ticketId,
                                      Long disputeId,
                                      Instant occurredAt,
                                      Consumer<UserReputationScore> extraMutation) {
        UserReputationScore rep = findOrCreateInsideCurrentTx(userId);
        BigDecimal scoreBefore = getScoreOrDefault(rep).setScale(2, RoundingMode.HALF_UP);
        BigDecimal scoreAfter = clampScore(scoreBefore.add(rawDelta));
        rep.setScore(scoreAfter);
        if (extraMutation != null) {
            extraMutation.accept(rep);
        }
        reputationRepository.save(rep);

        if (scoreBefore.compareTo(BigDecimal.ZERO) > 0
                && scoreAfter.compareTo(BigDecimal.ZERO) == 0
                && eventPublisher != null) {
            log.warn("[Reputation] userId={} giảm điểm từ {} về 0 — phát ReputationDepletedEvent!",
                    userId, scoreBefore);
            eventPublisher.publishEvent(new ReputationDepletedEvent(
                    userId,
                    scoreBefore,
                    scoreAfter,
                    reasonCode,
                    description,
                    ticketId,
                    disputeId,
                    occurredAt != null ? occurredAt : Instant.now()
            ));
        }
    }

    private void updateMetadataOnlyWithRetry(Long userId, Consumer<UserReputationScore> updateAction) {
        int attempts = 0;
        while (attempts < MAX_RETRY) {
            attempts++;
            try {
                runInNewTransaction(() -> {
                    UserReputationScore rep = findOrCreateInsideCurrentTx(userId);
                    updateAction.accept(rep);
                    reputationRepository.save(rep);
                });
                return;
            } catch (OptimisticLockingFailureException | DataIntegrityViolationException ex) {
                log.warn("Concurrent conflict when updating reputation metadata for userId {} (attempt {}/{}): {}",
                        userId, attempts, MAX_RETRY, ex.getMessage());
                if (attempts >= MAX_RETRY) {
                    throw ex;
                }
            }
        }
    }

    private void runInNewTransaction(Runnable action) {
        if (requiresNewTxTemplate != null) {
            requiresNewTxTemplate.executeWithoutResult(status -> action.run());
        } else {
            action.run();
        }
    }

    private UserReputationScore findOrCreateInsideCurrentTx(Long userId) {
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
