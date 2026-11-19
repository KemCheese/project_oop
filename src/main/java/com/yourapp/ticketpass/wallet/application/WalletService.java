package com.yourapp.ticketpass.wallet.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.yourapp.ticketpass.shared.exception.BusinessRuleViolationException;
import com.yourapp.ticketpass.wallet.domain.Wallet;
import com.yourapp.ticketpass.wallet.domain.WalletRepository;
import com.yourapp.ticketpass.wallet.domain.WalletTransaction;
import com.yourapp.ticketpass.wallet.domain.WalletTransactionRepository;
import com.yourapp.ticketpass.wallet.domain.WalletTransactionType;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service điều phối nghiệp vụ ví điện tử và sổ cái kế toán (Ledger).
 *
 * <p>
 * <b>Nguyên tắc toàn vẹn giao dịch:</b>
 * </p>
 * <ul>
 * <li>Mọi thao tác thay đổi số dư (nạp, trừ, cộng) đều chạy trong ranh giới
 * {@code @Transactional}.</li>
 * <li>Mọi thao tác trừ tiền (debit) bắt buộc sử dụng
 * {@link WalletRepository#findByUserIdWithLock(Long)}
 * để khóa bi quan hàng dữ liệu ({@code SELECT ... FOR UPDATE}), chống triệt để
 * Race Condition.</li>
 * <li>Mỗi lần số dư thay đổi, một bản ghi {@link WalletTransaction} bất biến
 * tương ứng bắt buộc
 * được lưu vào sổ cái trong cùng transaction.</li>
 * </ul>
 *
 * <p>
 * Module: wallet
 * </p>
 * <p>
 * Chủ sở hữu: Người C (Dev C)
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WalletService {

    private final WalletRepository walletRepository;
    private final WalletTransactionRepository walletTransactionRepository;

    /**
     * Tra cứu ví của người dùng, tự động khởi tạo ví mới nếu chưa tồn tại
     * (Idempotent).
     *
     * @param userId ID người dùng
     * @return thực thể ví
     */
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

    /**
     * Lấy thông tin ví của người dùng (chỉ đọc).
     *
     * @param userId ID người dùng
     * @return thực thể ví
     */
    @Transactional(readOnly = true)
    public Wallet getWalletByUserId(Long userId) {
        return walletRepository.findByUserId(userId)
                .orElseGet(() -> new Wallet(userId, BigDecimal.ZERO));
    }

    /**
     * Kiểm tra xem người dùng có đủ số dư cho giao dịch hay không.
     *
     * @param userId ID người dùng
     * @param amount số tiền cần kiểm tra
     * @return true nếu đủ tiền, false nếu không đủ hoặc chưa có ví
     */
    @Transactional(readOnly = true)
    public boolean hasSufficientBalance(Long userId, BigDecimal amount) {
        if (userId == null || amount == null) {
            return false;
        }
        return walletRepository.findByUserId(userId)
                .map(wallet -> wallet.hasSufficientBalance(amount))
                .orElse(false);
    }

    /**
     * Nạp tiền vào ví điện tử (Quick Top-up / Sandbox Top-up).
     *
     * @param userId      ID người dùng nạp tiền
     * @param amount      số tiền cần nạp
     * @param description mô tả nạp tiền
     * @return thực thể ví sau khi nạp
     */
    /**
     * Mức nạp tiền tối thiểu vào ví điện tử: 10.000 VNĐ.
     */
    public static final BigDecimal MIN_TOP_UP_AMOUNT = new BigDecimal("10000");

    @Transactional
    public Wallet topUp(Long userId, BigDecimal amount, String description) {
        if (amount == null || amount.compareTo(MIN_TOP_UP_AMOUNT) < 0) {
            throw new BusinessRuleViolationException("Số tiền nạp tối thiểu là 10.000 VNĐ.");
        }

        // Lấy ví kèm khóa bi quan
        Wallet wallet = walletRepository.findByUserIdWithLock(userId)
                .orElseGet(() -> walletRepository.save(new Wallet(userId, BigDecimal.ZERO)));

        BigDecimal before = wallet.getBalance();
        wallet.credit(amount);
        wallet = walletRepository.save(wallet);

        String refId = "TOPUP_" + userId + "_" + Instant.now().toEpochMilli();
        String desc = (description != null && !description.isBlank())
                ? description
                : "Nạp tiền vào ví điện tử TicketPass";

        WalletTransaction tx = new WalletTransaction(
                wallet.getId(), amount, before, wallet.getBalance(),
                WalletTransactionType.TOP_UP, refId, desc);
        walletTransactionRepository.save(tx);

        log.info("[WalletService] Nạp tiền thành công: userId={}, amount={}, balanceBefore={}, balanceAfter={}",
                userId, amount, before, wallet.getBalance());
        return wallet;
    }

    /**
     * Trừ tiền khỏi ví (Debit) — Áp dụng Khóa Bi Quan chống Race Condition.
     *
     * @param userId      ID người dùng bị trừ tiền
     * @param amount      số tiền cần trừ
     * @param type        loại giao dịch (ESCROW_PAY, DEPOSIT_LOCK...)
     * @param referenceId mã đối soát nghiệp vụ (ticketId, escrowId, depositId...)
     * @param description mô tả giao dịch
     * @return thực thể ví sau khi trừ tiền
     */
    @Transactional
    public Wallet debit(Long userId, BigDecimal amount, WalletTransactionType type,
            String referenceId, String description) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessRuleViolationException("Số tiền trừ phải lớn hơn 0.");
        }

        // Khóa bi quan hàng ví trong CSDL: SELECT ... FOR UPDATE
        Wallet wallet = walletRepository.findByUserIdWithLock(userId)
                .orElseGet(() -> walletRepository.save(new Wallet(userId, BigDecimal.ZERO)));

        BigDecimal before = wallet.getBalance();
        wallet.debit(amount); // Ném InsufficientBalanceException nếu không đủ tiền
        wallet = walletRepository.save(wallet);

        WalletTransaction tx = new WalletTransaction(
                wallet.getId(), amount, before, wallet.getBalance(),
                type, referenceId, description);
        walletTransactionRepository.save(tx);

        log.info(
                "[WalletService] Trừ tiền thành công: userId={}, amount={}, type={}, balanceBefore={}, balanceAfter={}",
                userId, amount, type, before, wallet.getBalance());
        return wallet;
    }

    /**
     * Cộng tiền vào ví (Credit) — Dùng cho Giải ngân, Hoàn cọc, Hoàn tiền khiếu
     * nại.
     *
     * @param userId      ID người dùng nhận tiền
     * @param amount      số tiền được cộng
     * @param type        loại giao dịch (PAYOUT_RECEIVE, DEPOSIT_REFUND,
     *                    ESCROW_REFUND...)
     * @param referenceId mã đối soát nghiệp vụ
     * @param description mô tả giao dịch
     * @return thực thể ví sau khi cộng tiền
     */
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
                type, referenceId, description);
        walletTransactionRepository.save(tx);

        log.info(
                "[WalletService] Cộng tiền thành công: userId={}, amount={}, type={}, balanceBefore={}, balanceAfter={}",
                userId, amount, type, before, wallet.getBalance());
        return wallet;
    }

    /**
     * Lấy danh sách lịch sử biến động số dư của người dùng.
     *
     * @param userId ID người dùng
     * @param limit  số lượng bản ghi tối đa
     * @return danh sách các giao dịch sổ cái
     */
    @Transactional(readOnly = true)
    public List<WalletTransaction> getTransactionHistory(Long userId, int limit) {
        if (userId == null) {
            return Collections.emptyList();
        }
        return walletRepository.findByUserId(userId)
                .map(wallet -> walletTransactionRepository.findByWalletIdOrderByCreatedAtDesc(wallet.getId(), limit))
                .orElse(Collections.emptyList());
    }
}
