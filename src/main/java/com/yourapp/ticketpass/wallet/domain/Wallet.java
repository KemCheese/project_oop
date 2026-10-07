package com.yourapp.ticketpass.wallet.domain;

import java.math.BigDecimal;
import java.time.Instant;

import com.yourapp.ticketpass.shared.exception.BusinessRuleViolationException;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Thực thể Ví Điện Tử (E-Wallet) đại diện cho tài khoản số dư của người dùng trên sàn TicketPass.
 *
 * <p><b>Nguyên tắc thiết kế OOP & Bảo mật:</b></p>
 * <ul>
 *   <li><b>Đóng gói dữ liệu (Encapsulation):</b> Thuộc tính {@code balance} không có public setter tự do.
 *       Mọi biến động số dư chỉ được thực hiện qua các phương thức nghiệp vụ tường minh
 *       {@link #credit(BigDecimal)} và {@link #debit(BigDecimal)}.</li>
 *   <li><b>Bảo vệ tương tranh (Concurrency Control):</b> Trường {@code version} phục vụ khóa lạc quan (Optimistic Lock).
 *       Khi kết hợp với Khóa bi quan ở tầng Repository ({@code PESSIMISTIC_WRITE}), đảm bảo số dư không bao giờ
 *       bị âm hoặc chi tiêu trùng lặp (Double-spending).</li>
 *   <li><b>Nguyên tắc Scalability 1:</b> Chỉ lưu {@code Long userId}, không dùng JPA relation sang {@code User}.</li>
 * </ul>
 *
 * <p>Module: wallet</p>
 * <p>Chủ sở hữu: Người C (Dev C)</p>
 */
@Entity
@Table(name = "wallets")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Wallet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(name = "balance", nullable = false, precision = 15, scale = 2)
    private BigDecimal balance = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Setter
    private WalletStatus status = WalletStatus.ACTIVE;

    @Version
    @Column(name = "version")
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public Wallet(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId không được để trống.");
        }
        this.userId = userId;
        this.balance = BigDecimal.ZERO;
        this.status = WalletStatus.ACTIVE;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public Wallet(Long userId, BigDecimal initialBalance) {
        this(userId);
        if (initialBalance != null && initialBalance.compareTo(BigDecimal.ZERO) > 0) {
            this.balance = initialBalance;
        }
    }

    /**
     * Nạp/Cộng tiền vào ví (Credit).
     *
     * @param amount số tiền cần cộng (phải lớn hơn 0)
     */
    public void credit(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessRuleViolationException("Số tiền cộng vào ví phải lớn hơn 0, nhận được: " + amount);
        }
        this.balance = this.balance.add(amount);
        this.updatedAt = Instant.now();
    }

    /**
     * Rút/Trừ tiền khỏi ví (Debit).
     *
     * @param amount số tiền cần trừ (phải lớn hơn 0)
     * @throws InsufficientBalanceException nếu số dư hiện tại nhỏ hơn số tiền cần trừ
     * @throws BusinessRuleViolationException nếu ví đang ở trạng thái không hoạt động
     */
    public void debit(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessRuleViolationException("Số tiền trừ khỏi ví phải lớn hơn 0, nhận được: " + amount);
        }
        if (this.status != WalletStatus.ACTIVE) {
            throw new BusinessRuleViolationException("Ví đang bị khóa, không thể thực hiện giao dịch trừ tiền.");
        }
        if (this.balance.compareTo(amount) < 0) {
            throw new InsufficientBalanceException(this.balance, amount);
        }
        this.balance = this.balance.subtract(amount);
        this.updatedAt = Instant.now();
    }

    /**
     * Kiểm tra nhanh xem ví có đủ số dư cho một khoản tiền nhất định hay không.
     *
     * @param amount số tiền cần kiểm tra
     * @return true nếu số dư lớn hơn hoặc bằng {@code amount} và ví đang ACTIVE
     */
    public boolean hasSufficientBalance(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        return this.status == WalletStatus.ACTIVE && this.balance.compareTo(amount) >= 0;
    }
}
