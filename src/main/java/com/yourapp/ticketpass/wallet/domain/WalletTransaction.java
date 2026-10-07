package com.yourapp.ticketpass.wallet.domain;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Thực thể Sổ Cái Giao Dịch (Wallet Ledger Transaction).
 *
 * <p><b>Tính chất bất biến (Append-only & Immutable):</b> Bản ghi này một khi đã tạo ra
 * sẽ không bao giờ bị sửa đổi hay xóa. Nó đóng vai trò là chứng từ kiểm toán (Audit Trail)
 * ghi nhận mọi sự kiện thay đổi số dư trong hệ thống.</p>
 *
 * <p>Module: wallet</p>
 * <p>Chủ sở hữu: Người C (Dev C)</p>
 */
@Entity
@Table(name = "wallet_transactions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WalletTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "wallet_id", nullable = false)
    private Long walletId;

    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "balance_before", nullable = false, precision = 15, scale = 2)
    private BigDecimal balanceBefore;

    @Column(name = "balance_after", nullable = false, precision = 15, scale = 2)
    private BigDecimal balanceAfter;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 50)
    private WalletTransactionType type;

    @Column(name = "reference_id", length = 100)
    private String referenceId;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "status", nullable = false, length = 30)
    private String status = "COMPLETED";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public WalletTransaction(Long walletId, BigDecimal amount, BigDecimal balanceBefore,
                             BigDecimal balanceAfter, WalletTransactionType type,
                             String referenceId, String description) {
        this.walletId = walletId;
        this.amount = amount;
        this.balanceBefore = balanceBefore;
        this.balanceAfter = balanceAfter;
        this.type = type;
        this.referenceId = referenceId;
        this.description = description;
        this.status = "COMPLETED";
        this.createdAt = Instant.now();
    }
}
