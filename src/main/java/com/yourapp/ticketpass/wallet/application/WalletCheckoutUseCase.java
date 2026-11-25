package com.yourapp.ticketpass.wallet.application;

import com.yourapp.ticketpass.payment.application.HoldFundsUseCase;
import com.yourapp.ticketpass.payment.application.LockDepositUseCase;
import com.yourapp.ticketpass.payment.domain.Deposit;
import com.yourapp.ticketpass.payment.domain.DepositRepository;
import com.yourapp.ticketpass.payment.domain.DepositStatus;
import com.yourapp.ticketpass.payment.domain.EscrowStatus;
import com.yourapp.ticketpass.payment.domain.EscrowTransaction;
import com.yourapp.ticketpass.payment.domain.EscrowTransactionRepository;
import com.yourapp.ticketpass.payment.domain.PaymentResult;
import com.yourapp.ticketpass.reputation.application.ReputationPolicyService;
import com.yourapp.ticketpass.shared.dto.ConflictingTicketInfo;
import com.yourapp.ticketpass.shared.exception.BusinessRuleViolationException;
import com.yourapp.ticketpass.shared.exception.ResourceNotFoundException;
import com.yourapp.ticketpass.ticket.application.TicketScheduleService;
import com.yourapp.ticketpass.ticket.domain.Ticket;
import com.yourapp.ticketpass.ticket.domain.TicketRepository;
import com.yourapp.ticketpass.ticket.domain.TicketStatus;
import com.yourapp.ticketpass.wallet.domain.Wallet;
import com.yourapp.ticketpass.wallet.domain.WalletStatus;
import com.yourapp.ticketpass.wallet.domain.WalletTransactionType;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class WalletCheckoutUseCase {

    private final WalletService walletService;
    private final HoldFundsUseCase holdFundsUseCase;
    private final LockDepositUseCase lockDepositUseCase;
    private final TicketRepository ticketRepository;
    private final EscrowTransactionRepository escrowTransactionRepository;
    private final DepositRepository depositRepository;
    private final TicketScheduleService ticketScheduleService;
    private final ReputationPolicyService reputationPolicyService;

    @Getter
    public static class CheckoutResult {

        public enum Status {
            SUCCESS,
            OVERLAP_WARNING
        }

        private final Status status;
        private final Long ticketId;
        private final BigDecimal chargedAmount;
        private final List<ConflictingTicketInfo> conflicts;

        private CheckoutResult(Status status, Long ticketId, BigDecimal chargedAmount,
                               List<ConflictingTicketInfo> conflicts) {
            this.status = status;
            this.ticketId = ticketId;
            this.chargedAmount = chargedAmount;
            this.conflicts = conflicts != null ? List.copyOf(conflicts) : Collections.emptyList();
        }

        public static CheckoutResult success(Long ticketId, BigDecimal chargedAmount) {
            return new CheckoutResult(Status.SUCCESS, ticketId, chargedAmount, Collections.emptyList());
        }

        public static CheckoutResult overlapWarning(Long ticketId, List<ConflictingTicketInfo> conflicts) {
            return new CheckoutResult(Status.OVERLAP_WARNING, ticketId, null, conflicts);
        }

        public boolean isOverlapWarning() {
            return this.status == Status.OVERLAP_WARNING;
        }
    }

    @Transactional
    public CheckoutResult payEscrowWithWallet(Long ticketId, Long buyerId, boolean confirmOverlap) {
        Ticket ticket = ticketRepository.findByIdWithLock(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket", ticketId));

        if (buyerId.equals(ticket.getSellerId())) {
            throw new BusinessRuleViolationException("Bạn không thể tự mua vé của chính mình.");
        }
        if (ticket.getStatus() != TicketStatus.LISTED) {
            throw new BusinessRuleViolationException(
                    "Vé #" + ticketId + " hiện không còn ở trạng thái mở bán (trạng thái hiện tại: "
                            + ticket.getStatus() + ").");
        }

        BigDecimal salePrice = ticket.getSalePrice();
        if (reputationPolicyService != null) {
            reputationPolicyService.assertCanTransact(buyerId, salePrice, "mua vé");
        }

        if (!confirmOverlap && ticketScheduleService != null) {
            List<ConflictingTicketInfo> conflicts = ticketScheduleService.findConflictingTickets(buyerId, ticket);
            if (conflicts != null && !conflicts.isEmpty()) {
                return CheckoutResult.overlapWarning(ticketId, conflicts);
            }
        }

        Wallet wallet = walletService.getOrCreateWallet(buyerId);
        assertWalletActive(wallet);
        if (!wallet.hasSufficientBalance(salePrice)) {
            throw new BusinessRuleViolationException(String.format(
                    "Số dư ví không đủ để thanh toán (Hiện có: %,.0f VNĐ, Cần: %,.0f VNĐ). Vui lòng nạp thêm tiền vào ví.",
                    wallet.getBalance(), salePrice));
        }

        EscrowTransaction tx = resolveOrInitPendingEscrow(ticket, buyerId, salePrice);
        if (tx.getStatus() != EscrowStatus.PENDING) {
            throw new BusinessRuleViolationException(
                    "Giao dịch ký quỹ #" + tx.getId() + " đang ở trạng thái " + tx.getStatus()
                            + ", không thể thanh toán lại.");
        }
        if (!buyerId.equals(tx.getBuyerId())) {
            throw new BusinessRuleViolationException("Vé này đang có người mua khác giữ phiên thanh toán.");
        }

        walletService.debit(buyerId, salePrice, WalletTransactionType.ESCROW_PAY,
                "ESCROW_" + tx.getId(), "Thanh toán ký quỹ vé #" + ticketId + " (" + ticket.getTitle() + ")");

        Map<String, String> callbackParams = new HashMap<>();
        callbackParams.put("referenceId", String.valueOf(tx.getId()));
        callbackParams.put("orderId", String.valueOf(tx.getId()));
        holdFundsUseCase.confirmHeld(null, callbackParams);

        return CheckoutResult.success(ticketId, salePrice);
    }

    @Transactional
    public CheckoutResult payDepositWithWallet(Long ticketId, Long sellerId) {
        Ticket ticket = ticketRepository.findByIdWithLock(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket", ticketId));

        if (!sellerId.equals(ticket.getSellerId())) {
            throw new BusinessRuleViolationException("Bạn không phải chủ sở hữu của vé này.");
        }
        if (ticket.getStatus() != TicketStatus.PAYMENT_ESCROWED) {
            throw new BusinessRuleViolationException(
                    "Vé #" + ticketId + " không ở trạng thái chờ người bán nạp cọc (hiện tại: "
                            + ticket.getStatus() + ").");
        }

        Deposit deposit = resolveOrInitPendingDeposit(ticket, sellerId);
        if (deposit.getStatus() != DepositStatus.PENDING) {
            throw new BusinessRuleViolationException(
                    "Khoản cọc #" + deposit.getId() + " đang ở trạng thái " + deposit.getStatus()
                            + ", không thể thanh toán lại.");
        }
        if (!sellerId.equals(deposit.getSellerId())) {
            throw new BusinessRuleViolationException("Bạn không phải người bán của khoản cọc này.");
        }

        BigDecimal depositAmount = deposit.getAmount();
        Wallet wallet = walletService.getOrCreateWallet(sellerId);
        assertWalletActive(wallet);
        if (!wallet.hasSufficientBalance(depositAmount)) {
            throw new BusinessRuleViolationException(String.format(
                    "Số dư ví không đủ để nộp cọc 50%% (Hiện có: %,.0f VNĐ, Cần: %,.0f VNĐ). Vui lòng nạp thêm tiền vào ví.",
                    wallet.getBalance(), depositAmount));
        }

        walletService.debit(sellerId, depositAmount, WalletTransactionType.DEPOSIT_LOCK,
                "DEPOSIT_" + deposit.getId(), "Khóa cọc bảo đảm 50% cho vé #" + ticketId + " (" + ticket.getTitle() + ")");

        Map<String, String> callbackParams = new HashMap<>();
        callbackParams.put("referenceId", String.valueOf(deposit.getId()));
        callbackParams.put("orderId", String.valueOf(deposit.getId()));
        lockDepositUseCase.confirmLocked(null, callbackParams);

        return CheckoutResult.success(ticketId, depositAmount);
    }

    @Transactional
    public CheckoutResult payFromTransfer(Long refId, String type, Long userId, boolean confirmOverlap) {
        if ("escrow".equals(type)) {
            EscrowTransaction escrow = escrowTransactionRepository.findById(refId)
                    .orElseThrow(() -> new ResourceNotFoundException("EscrowTransaction", refId));

            if (!userId.equals(escrow.getBuyerId())) {
                throw new BusinessRuleViolationException("Bạn không phải người mua của giao dịch này.");
            }
            if (escrow.getStatus() != EscrowStatus.PENDING) {
                throw new BusinessRuleViolationException(
                        "Giao dịch ký quỹ #" + refId + " đã ở trạng thái " + escrow.getStatus()
                                + ", không thể thanh toán lặp lại.");
            }

            Ticket ticket = ticketRepository.findByIdWithLock(escrow.getTicketId())
                    .orElseThrow(() -> new ResourceNotFoundException("Ticket", escrow.getTicketId()));
            if (ticket.getStatus() != TicketStatus.LISTED) {
                throw new BusinessRuleViolationException(
                        "Vé #" + ticket.getId() + " không còn ở trạng thái mở bán (hiện tại: "
                                + ticket.getStatus() + ").");
            }

            if (reputationPolicyService != null) {
                reputationPolicyService.assertCanTransact(userId, escrow.getSalePrice(), "mua vé");
            }

            if (!confirmOverlap && ticketScheduleService != null) {
                List<ConflictingTicketInfo> conflicts = ticketScheduleService.findConflictingTickets(userId, ticket);
                if (conflicts != null && !conflicts.isEmpty()) {
                    return CheckoutResult.overlapWarning(ticket.getId(), conflicts);
                }
            }

            Wallet wallet = walletService.getOrCreateWallet(userId);
            assertWalletActive(wallet);
            if (!wallet.hasSufficientBalance(escrow.getSalePrice())) {
                throw new BusinessRuleViolationException(String.format(
                        "Số dư ví không đủ (Hiện có: %,.0f VNĐ, Cần: %,.0f VNĐ). Vui lòng nạp tiền vào ví.",
                        wallet.getBalance(), escrow.getSalePrice()));
            }

            walletService.debit(userId, escrow.getSalePrice(), WalletTransactionType.ESCROW_PAY,
                    "ESCROW_" + refId, "Thanh toán ký quỹ vé #" + escrow.getTicketId());

            Map<String, String> callbackParams = new HashMap<>();
            callbackParams.put("referenceId", String.valueOf(refId));
            callbackParams.put("orderId", String.valueOf(refId));
            holdFundsUseCase.confirmHeld(null, callbackParams);

            return CheckoutResult.success(escrow.getTicketId(), escrow.getSalePrice());

        } else if ("deposit".equals(type)) {
            Deposit deposit = depositRepository.findById(refId)
                    .orElseThrow(() -> new ResourceNotFoundException("Deposit", refId));

            if (!userId.equals(deposit.getSellerId())) {
                throw new BusinessRuleViolationException("Bạn không phải người bán của giao dịch này.");
            }
            if (deposit.getStatus() != DepositStatus.PENDING) {
                throw new BusinessRuleViolationException(
                        "Khoản cọc #" + refId + " đã ở trạng thái " + deposit.getStatus()
                                + ", không thể thanh toán lặp lại.");
            }

            Ticket ticket = ticketRepository.findByIdWithLock(deposit.getTicketId())
                    .orElseThrow(() -> new ResourceNotFoundException("Ticket", deposit.getTicketId()));
            if (ticket.getStatus() != TicketStatus.PAYMENT_ESCROWED) {
                throw new BusinessRuleViolationException(
                        "Vé #" + ticket.getId() + " không còn ở trạng thái chờ khóa cọc (hiện tại: "
                                + ticket.getStatus() + ").");
            }

            Wallet wallet = walletService.getOrCreateWallet(userId);
            assertWalletActive(wallet);
            if (!wallet.hasSufficientBalance(deposit.getAmount())) {
                throw new BusinessRuleViolationException(String.format(
                        "Số dư ví không đủ (Hiện có: %,.0f VNĐ, Cần: %,.0f VNĐ). Vui lòng nạp tiền vào ví.",
                        wallet.getBalance(), deposit.getAmount()));
            }

            walletService.debit(userId, deposit.getAmount(), WalletTransactionType.DEPOSIT_LOCK,
                    "DEPOSIT_" + refId, "Khóa cọc 50% vé #" + deposit.getTicketId());

            Map<String, String> callbackParams = new HashMap<>();
            callbackParams.put("referenceId", String.valueOf(refId));
            callbackParams.put("orderId", String.valueOf(refId));
            lockDepositUseCase.confirmLocked(null, callbackParams);

            return CheckoutResult.success(deposit.getTicketId(), deposit.getAmount());
        }

        throw new BusinessRuleViolationException("Loại giao dịch không hợp lệ: " + type);
    }

    private void assertWalletActive(Wallet wallet) {
        if (wallet == null || wallet.getStatus() != WalletStatus.ACTIVE) {
            throw new BusinessRuleViolationException(
                    "Ví điện tử của bạn hiện đang bị khóa hoặc đóng băng, không thể thực hiện thanh toán.");
        }
    }

    private EscrowTransaction resolveOrInitPendingEscrow(Ticket ticket, Long buyerId, BigDecimal salePrice) {
        Optional<EscrowTransaction> existingOpt = escrowTransactionRepository.findByTicketId(ticket.getId());
        if (existingOpt.isPresent()) {
            EscrowTransaction existing = existingOpt.get();
            if (existing.getStatus() == EscrowStatus.PENDING && buyerId.equals(existing.getBuyerId())) {
                return existing;
            }
            if (existing.getStatus() == EscrowStatus.HELD) {
                throw new BusinessRuleViolationException("Giao dịch ký quỹ cho vé này đã được thanh toán trước đó.");
            }
        }
        PaymentResult initResult = holdFundsUseCase.initEscrowPayment(
                ticket.getId(), buyerId, ticket.getSellerId(), salePrice);
        if (initResult == null || !initResult.isSuccess()) {
            throw new BusinessRuleViolationException(
                    initResult != null && initResult.getMessage() != null
                            ? initResult.getMessage()
                            : "Không thể khởi tạo giao dịch ký quỹ.");
        }
        return escrowTransactionRepository.findByTicketId(ticket.getId())
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy Escrow vừa khởi tạo"));
    }

    private Deposit resolveOrInitPendingDeposit(Ticket ticket, Long sellerId) {
        Optional<Deposit> existingOpt = depositRepository.findByTicketId(ticket.getId());
        if (existingOpt.isPresent()) {
            Deposit existing = existingOpt.get();
            if (existing.getStatus() == DepositStatus.PENDING) {
                return existing;
            }
            if (existing.getStatus() == DepositStatus.LOCKED) {
                throw new BusinessRuleViolationException("Khoản cọc bảo đảm cho vé này đã được khóa trước đó.");
            }
            if (existing.getStatus() != DepositStatus.FAILED) {
                throw new BusinessRuleViolationException(
                        "Khoản cọc đang ở trạng thái " + existing.getStatus() + ", không thể nạp cọc lại.");
            }
        }
        PaymentResult initResult = lockDepositUseCase.initDepositPayment(
                ticket.getId(), sellerId, ticket.getSalePrice());
        if (initResult == null || !initResult.isSuccess()) {
            throw new BusinessRuleViolationException(
                    initResult != null && initResult.getMessage() != null
                            ? initResult.getMessage()
                            : "Không thể khởi tạo giao dịch cọc.");
        }
        return depositRepository.findByTicketId(ticket.getId())
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy Deposit vừa khởi tạo"));
    }
}
