package com.yourapp.ticketpass.wallet.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.yourapp.ticketpass.shared.exception.BusinessRuleViolationException;
import com.yourapp.ticketpass.wallet.domain.AccountLiquidation;
import com.yourapp.ticketpass.wallet.domain.AccountLiquidationRepository;
import com.yourapp.ticketpass.wallet.domain.LiquidationStatus;
import com.yourapp.ticketpass.wallet.domain.Wallet;
import com.yourapp.ticketpass.wallet.domain.WalletRepository;
import com.yourapp.ticketpass.wallet.domain.WalletStatus;
import com.yourapp.ticketpass.wallet.domain.WalletTransaction;
import com.yourapp.ticketpass.wallet.domain.WalletTransactionRepository;
import com.yourapp.ticketpass.wallet.domain.WalletTransactionType;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class WalletService {

    public static final BigDecimal LIQUIDATION_FEE_RATE = new BigDecimal("0.05");

    public static final BigDecimal LIQUIDATION_MIN_FEE = new BigDecimal("10000.00");

    private final WalletRepository walletRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final AccountLiquidationRepository accountLiquidationRepository;

    @Autowired
    public WalletService(WalletRepository walletRepository,
                         WalletTransactionRepository walletTransactionRepository,
                         AccountLiquidationRepository accountLiquidationRepository) {
        this.walletRepository = walletRepository;
        this.walletTransactionRepository = walletTransactionRepository;
        this.accountLiquidationRepository = accountLiquidationRepository;
    }

    public WalletService(WalletRepository walletRepository,
                         WalletTransactionRepository walletTransactionRepository) {
        this(walletRepository, walletTransactionRepository, null);
    }

    @Transactional
    public Wallet getOrCreateWallet(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId không được để trống.");
        }
        return walletRepository.findByUserId(userId)
                .orElseGet(() -> {
                    log.info("[WalletService] Khởi tạo ví mới cho userId={}", userId);
                    Wallet newWallet = new Wallet(userId, BigDecimal.ZERO);
                    return walletRepository.save(newWallet);
                });
    }

    @Transactional(readOnly = true)
    public Wallet getWalletByUserId(Long userId) {
        return walletRepository.findByUserId(userId)
                .orElseGet(() -> new Wallet(userId, BigDecimal.ZERO));
    }

    @Transactional(readOnly = true)
    public boolean hasSufficientBalance(Long userId, BigDecimal amount) {
        if (userId == null || amount == null) {
            return false;
        }
        return walletRepository.findByUserId(userId)
                .map(wallet -> wallet.hasSufficientBalance(amount))
                .orElse(false);
    }

    public static final BigDecimal MIN_TOP_UP_AMOUNT = new BigDecimal("10000");

    @Transactional
    public Wallet topUp(Long userId, BigDecimal amount, String description) {
        if (amount == null || amount.compareTo(MIN_TOP_UP_AMOUNT) < 0) {
            throw new BusinessRuleViolationException("Số tiền nạp tối thiểu là 10.000 VNĐ.");
        }

        Wallet wallet = walletRepository.findByUserIdWithLock(userId)
                .orElseGet(() -> walletRepository.save(new Wallet(userId, BigDecimal.ZERO)));

        if (wallet.getStatus() != WalletStatus.ACTIVE) {
            throw new BusinessRuleViolationException("Ví đang bị đóng băng, không thể nạp thêm tiền.");
        }

        BigDecimal before = wallet.getBalance();
        wallet.credit(amount);
        wallet = walletRepository.save(wallet);

        String refId = "TOPUP_" + userId + "_" + Instant.now().toEpochMilli();
        String desc = truncateDescription((description != null && !description.isBlank())
                ? description
                : "Nạp tiền vào ví điện tử TicketPass");

        WalletTransaction tx = new WalletTransaction(
                wallet.getId(), amount, before, wallet.getBalance(),
                WalletTransactionType.TOP_UP, refId, desc);
        walletTransactionRepository.save(tx);

        log.info("[WalletService] Nạp tiền thành công: userId={}, amount={}, balanceBefore={}, balanceAfter={}",
                userId, amount, before, wallet.getBalance());
        return wallet;
    }

    @Transactional
    public Wallet debit(Long userId, BigDecimal amount, WalletTransactionType type,
            String referenceId, String description) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessRuleViolationException("Số tiền trừ phải lớn hơn 0.");
        }

        Wallet wallet = walletRepository.findByUserIdWithLock(userId)
                .orElseGet(() -> walletRepository.save(new Wallet(userId, BigDecimal.ZERO)));

        BigDecimal before = wallet.getBalance();
        wallet.debit(amount);
        wallet = walletRepository.save(wallet);

        WalletTransaction tx = new WalletTransaction(
                wallet.getId(), amount, before, wallet.getBalance(),
                type, referenceId, truncateDescription(description));
        walletTransactionRepository.save(tx);

        log.info(
                "[WalletService] Trừ tiền thành công: userId={}, amount={}, type={}, balanceBefore={}, balanceAfter={}",
                userId, amount, type, before, wallet.getBalance());
        return wallet;
    }

    @Transactional
    public Wallet credit(Long userId, BigDecimal amount, WalletTransactionType type,
            String referenceId, String description) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessRuleViolationException("Số tiền cộng phải lớn hơn 0.");
        }

        Wallet wallet = walletRepository.findByUserIdWithLock(userId)
                .orElseGet(() -> walletRepository.save(new Wallet(userId, BigDecimal.ZERO)));

        BigDecimal before = wallet.getBalance();
        wallet.credit(amount);
        wallet = walletRepository.save(wallet);

        WalletTransaction tx = new WalletTransaction(
                wallet.getId(), amount, before, wallet.getBalance(),
                type, referenceId, truncateDescription(description));
        walletTransactionRepository.save(tx);

        log.info(
                "[WalletService] Cộng tiền thành công: userId={}, amount={}, type={}, balanceBefore={}, balanceAfter={}",
                userId, amount, type, before, wallet.getBalance());
        return wallet;
    }

    @Transactional(readOnly = true)
    public List<WalletTransaction> getTransactionHistory(Long userId, int limit) {
        if (userId == null) {
            return Collections.emptyList();
        }
        return walletRepository.findByUserId(userId)
                .map(wallet -> walletTransactionRepository.findByWalletIdOrderByCreatedAtDesc(wallet.getId(), limit))
                .orElse(Collections.emptyList());
    }

    @Transactional
    public AccountLiquidation liquidateAndFreeze(Long userId,
                                                 String triggerKey,
                                                 String bankName,
                                                 String bankAccountNumber,
                                                 String bankAccountHolder,
                                                 int cancelledTicketCount,
                                                 String reason) {
        if (userId == null || triggerKey == null || triggerKey.isBlank()) {
            throw new IllegalArgumentException("userId và triggerKey không được để trống.");
        }

        Wallet wallet = walletRepository.findByUserIdWithLock(userId)
                .orElseGet(() -> walletRepository.save(new Wallet(userId, BigDecimal.ZERO)));

        if (accountLiquidationRepository != null) {
            Optional<AccountLiquidation> existing = accountLiquidationRepository.findByTriggerKey(triggerKey);
            if (existing.isPresent()) {
                log.info("[WalletService] Thanh lý triggerKey={} đã thực hiện trước đó, bỏ qua gọi trùng.", triggerKey);
                return existing.get();
            }
        }

        BigDecimal grossBalance = wallet.liquidateAndFreeze().setScale(2, RoundingMode.HALF_UP);
        wallet = walletRepository.save(wallet);

        BigDecimal zero = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal feeAmount;
        BigDecimal netPayout;
        LiquidationStatus liquidationStatus;

        boolean hasBankInfo = isNonBlank(bankName) && isNonBlank(bankAccountNumber) && isNonBlank(bankAccountHolder);
        String maskedAccount = maskBankAccount(bankAccountNumber);

        if (grossBalance.compareTo(BigDecimal.ZERO) <= 0) {
            feeAmount = zero;
            netPayout = zero;
            liquidationStatus = LiquidationStatus.NO_BALANCE;
        } else {
            BigDecimal rawFivePercent = grossBalance.multiply(LIQUIDATION_FEE_RATE)
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal candidateFee = rawFivePercent.max(LIQUIDATION_MIN_FEE);
            feeAmount = grossBalance.min(candidateFee).setScale(2, RoundingMode.HALF_UP);
            netPayout = grossBalance.subtract(feeAmount).setScale(2, RoundingMode.HALF_UP);
            liquidationStatus = hasBankInfo ? LiquidationStatus.TRANSFERRED : LiquidationStatus.PENDING_BANK_INFO;

            if (feeAmount.compareTo(BigDecimal.ZERO) > 0) {
                WalletTransaction feeTx = new WalletTransaction(
                        wallet.getId(),
                        feeAmount,
                        grossBalance,
                        netPayout,
                        WalletTransactionType.LIQUIDATION_FEE,
                        triggerKey,
                        "Phí thanh lý tài khoản bị khóa vĩnh viễn do điểm uy tín = 0 (max 5%, 10.000đ)");
                walletTransactionRepository.save(feeTx);
            }

            if (netPayout.compareTo(BigDecimal.ZERO) > 0) {
                String payoutDesc = hasBankInfo
                        ? String.format("Tự động chuyển hoàn số dư còn lại về %s (%s - %s)",
                                bankName.trim(), maskedAccount, bankAccountHolder.trim())
                        : "Thanh lý số dư còn lại (chờ người dùng bổ sung tài khoản ngân hàng để đối soát chi trả)";
                WalletTransaction payoutTx = new WalletTransaction(
                        wallet.getId(),
                        netPayout,
                        netPayout,
                        zero,
                        WalletTransactionType.LIQUIDATION_PAYOUT,
                        triggerKey,
                        truncateDescription(payoutDesc));
                walletTransactionRepository.save(payoutTx);
            }
        }

        AccountLiquidation liquidation = new AccountLiquidation();
        liquidation.setUserId(userId);
        liquidation.setTriggerKey(triggerKey);
        liquidation.setGrossBalance(grossBalance);
        liquidation.setFeeAmount(feeAmount);
        liquidation.setNetPayoutAmount(netPayout);
        liquidation.setStatus(liquidationStatus);
        liquidation.setBankName(hasBankInfo ? bankName.trim() : null);
        liquidation.setBankAccountNumberMasked(hasBankInfo ? maskedAccount : null);
        liquidation.setBankAccountHolder(hasBankInfo ? bankAccountHolder.trim() : null);
        liquidation.setCancelledTicketCount(Math.max(0, cancelledTicketCount));
        liquidation.setReason(truncateDescription(isNonBlank(reason)
                ? reason.trim()
                : "Tự động thanh lý số dư ví khi điểm uy tín giảm về 0."));
        liquidation.setLiquidatedAt(Instant.now());

        if (accountLiquidationRepository != null) {
            liquidation = accountLiquidationRepository.save(liquidation);
        }

        log.info("[WalletService] Đã thanh lý & đóng băng ví userId={}: gross={}, fee={}, net={}, status={}, cancelledTickets={}",
                userId, grossBalance, feeAmount, netPayout, liquidationStatus, cancelledTicketCount);
        return liquidation;
    }

    private boolean isNonBlank(String value) {
        return value != null && !value.isBlank();
    }

    private String truncateDescription(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= 500 ? trimmed : trimmed.substring(0, 500);
    }

    private String maskBankAccount(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.length() <= 4) {
            return "****";
        }
        return "*".repeat(trimmed.length() - 4) + trimmed.substring(trimmed.length() - 4);
    }
}
