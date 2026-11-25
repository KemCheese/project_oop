package com.yourapp.ticketpass.admin.application;

import com.yourapp.ticketpass.payment.application.RefundBuyerUseCase;
import com.yourapp.ticketpass.payment.application.ReleaseFundsUseCase;
import com.yourapp.ticketpass.payment.domain.Deposit;
import com.yourapp.ticketpass.payment.domain.DepositRepository;
import com.yourapp.ticketpass.payment.domain.DepositStatus;
import com.yourapp.ticketpass.payment.domain.EscrowStatus;
import com.yourapp.ticketpass.payment.domain.EscrowTransaction;
import com.yourapp.ticketpass.payment.domain.EscrowTransactionRepository;
import com.yourapp.ticketpass.shared.event.ReputationDepletedEvent;
import com.yourapp.ticketpass.shared.event.TicketAutoCancelledDueToBanEvent;
import com.yourapp.ticketpass.shared.event.UserAccountModeratedEvent;
import com.yourapp.ticketpass.ticket.application.TicketStateMachineService;
import com.yourapp.ticketpass.ticket.domain.Ticket;
import com.yourapp.ticketpass.ticket.domain.TicketRepository;
import com.yourapp.ticketpass.ticket.domain.TicketStatus;
import com.yourapp.ticketpass.user.application.AccountSecurityService;
import com.yourapp.ticketpass.user.domain.User;
import com.yourapp.ticketpass.user.domain.UserRepository;
import com.yourapp.ticketpass.user.domain.UserRole;
import com.yourapp.ticketpass.wallet.application.WalletService;
import com.yourapp.ticketpass.wallet.domain.AccountLiquidation;
import com.yourapp.ticketpass.wallet.domain.AccountLiquidationRepository;
import java.time.Instant;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountLiquidationOrchestrator {

    private static final Set<TicketStatus> UNOPENED_QR_STATUSES = EnumSet.of(
            TicketStatus.LISTED,
            TicketStatus.PAYMENT_ESCROWED,
            TicketStatus.DEPOSIT_LOCKED
    );

    private final UserRepository userRepository;
    private final TicketRepository ticketRepository;
    private final TicketStateMachineService ticketStateMachineService;
    private final EscrowTransactionRepository escrowTransactionRepository;
    private final DepositRepository depositRepository;
    private final RefundBuyerUseCase refundBuyerUseCase;
    private final ReleaseFundsUseCase releaseFundsUseCase;
    private final WalletService walletService;
    private final AccountLiquidationRepository accountLiquidationRepository;
    private final AccountSecurityService accountSecurityService;
    private final ApplicationEventPublisher eventPublisher;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onReputationDepleted(ReputationDepletedEvent event) {
        if (event == null || event.getUserId() == null) {
            return;
        }
        String triggerReason = event.getDescription() != null && !event.getDescription().isBlank()
                ? event.getDescription()
                : "Điểm uy tín giảm về 0.";
        executeZeroReputationLiquidation(event.getUserId(), triggerReason);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onUserAccountModerated(UserAccountModeratedEvent event) {
        if (event == null || event.getUserId() == null || event.getAdminId() == null) {
            return;
        }
        if (!"BANNED".equals(event.getTargetStatusName())) {
            return;
        }
        List<Ticket> sellerTickets = ticketRepository.findBySellerId(event.getUserId());
        if (sellerTickets == null || sellerTickets.isEmpty()) {
            return;
        }
        for (Ticket ticket : sellerTickets) {
            if (ticket != null && ticket.getId() != null && ticket.getStatus() == TicketStatus.LISTED) {
                ticketStateMachineService.transition(ticket.getId(), TicketStatus.CANCELLED);
            }
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AccountLiquidation executeZeroReputationLiquidation(Long userId, String triggerReason) {
        if (userId == null) {
            return null;
        }

        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            log.warn("[AccountLiquidation] Không tìm thấy userId={}, bỏ qua thanh lý.", userId);
            return null;
        }

        User user = userOpt.get();
        if (user.getRole() == UserRole.ADMIN) {
            log.warn("[AccountLiquidation] Bỏ qua auto-ban đối với tài khoản ADMIN userId={}.", userId);
            return null;
        }

        String triggerKey = "ZERO_REPUTATION_" + userId;
        Optional<AccountLiquidation> existing = accountLiquidationRepository.findByTriggerKey(triggerKey);
        if (existing.isPresent() || user.isPermanentlyBanned()) {
            log.info("[AccountLiquidation] userId={} đã được thanh lý/khóa vĩnh viễn trước đó, bỏ qua gọi trùng.", userId);
            return existing.orElse(null);
        }

        int cancelledCount = cancelUnopenedTransactions(userId);

        String liquidationReason = "Khóa vĩnh viễn & thanh lý tự động do điểm uy tín về 0 (" + triggerReason + ")";
        AccountLiquidation liquidation = walletService.liquidateAndFreeze(
                userId,
                triggerKey,
                user.getBankName(),
                user.getBankAccountNumber(),
                user.getBankAccountHolder(),
                cancelledCount,
                liquidationReason);

        String banReason = String.format(
                "Điểm uy tín giảm về 0 — tài khoản bị khóa vĩnh viễn. Đã hủy %d giao dịch chưa mở QR và thanh lý ví "
                        + "(Số dư gốc: %,.0f đ, Phí thanh lý: %,.0f đ, Thực chuyển: %,.0f đ).",
                cancelledCount,
                liquidation.getGrossBalance(),
                liquidation.getFeeAmount(),
                liquidation.getNetPayoutAmount());
        accountSecurityService.permanentlyBanForZeroReputation(userId, banReason);

        log.warn("[AccountLiquidation] Hoàn tất khóa vĩnh viễn & thanh lý cho userId={}: cancelledTickets={}, gross={}, fee={}, net={}",
                userId, cancelledCount, liquidation.getGrossBalance(), liquidation.getFeeAmount(), liquidation.getNetPayoutAmount());
        return liquidation;
    }

    private int cancelUnopenedTransactions(Long bannedUserId) {
        Map<Long, Ticket> candidateTickets = new LinkedHashMap<>();

        List<Ticket> sellerTickets = ticketRepository.findBySellerId(bannedUserId);
        if (sellerTickets != null) {
            for (Ticket t : sellerTickets) {
                if (t != null && t.getId() != null) {
                    candidateTickets.putIfAbsent(t.getId(), t);
                }
            }
        }

        List<Ticket> buyerTickets = ticketRepository.findByBuyerId(bannedUserId);
        if (buyerTickets != null) {
            for (Ticket t : buyerTickets) {
                if (t != null && t.getId() != null) {
                    candidateTickets.putIfAbsent(t.getId(), t);
                }
            }
        }

        int cancelledCount = 0;
        Instant now = Instant.now();

        for (Ticket ticket : candidateTickets.values()) {
            TicketStatus status = ticket.getStatus();
            if (status == null || !UNOPENED_QR_STATUSES.contains(status)) {
                continue;
            }

            Long ticketId = ticket.getId();
            boolean escrowRefunded = false;
            boolean depositRefunded = false;

            Optional<EscrowTransaction> escrowOpt = escrowTransactionRepository.findByTicketId(ticketId);
            if (escrowOpt.isPresent()) {
                EscrowTransaction escrow = escrowOpt.get();
                if (escrow.getStatus() == EscrowStatus.HELD) {
                    refundBuyerUseCase.refundFull(
                            ticketId,
                            "Hủy giao dịch tự động do tài khoản đối tác bị khóa vĩnh viễn (điểm uy tín = 0)");
                    escrowRefunded = true;
                } else if (escrow.getStatus() == EscrowStatus.PENDING) {
                    escrow.setStatus(EscrowStatus.FAILED);
                    escrow.setFailureReason("Tự động đóng phiên thanh toán do tài khoản đối tác bị khóa vĩnh viễn.");
                    escrowTransactionRepository.save(escrow);
                }
            }

            Optional<Deposit> depositOpt = depositRepository.findByTicketId(ticketId);
            if (depositOpt.isPresent()) {
                Deposit deposit = depositOpt.get();
                if (deposit.getStatus() == DepositStatus.LOCKED) {
                    releaseFundsUseCase.refundSellerDeposit(ticketId);
                    depositRefunded = true;
                } else if (deposit.getStatus() == DepositStatus.PENDING) {
                    deposit.setStatus(DepositStatus.FAILED);
                    deposit.setForfeitReason("Tự động đóng phiên cọc do tài khoản bị khóa vĩnh viễn.");
                    depositRepository.save(deposit);
                }
            }

            ticketStateMachineService.transition(ticketId, TicketStatus.CANCELLED);
            cancelledCount++;

            Long counterpartyUserId;
            String counterpartyRole;
            if (bannedUserId.equals(ticket.getSellerId())) {
                counterpartyUserId = ticket.getBuyerId();
                counterpartyRole = "BUYER";
            } else {
                counterpartyUserId = ticket.getSellerId();
                counterpartyRole = "SELLER";
            }

            if (counterpartyUserId != null && !counterpartyUserId.equals(bannedUserId) && eventPublisher != null) {
                eventPublisher.publishEvent(new TicketAutoCancelledDueToBanEvent(
                        ticketId,
                        bannedUserId,
                        counterpartyUserId,
                        counterpartyRole,
                        ticket.getTitle(),
                        escrowRefunded,
                        depositRefunded,
                        now
                ));
            }
        }

        return cancelledCount;
    }
}
