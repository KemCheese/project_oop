package com.yourapp.ticketpass.wallet.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Phân loại biến động số dư trong sổ cái kế toán (Ledger).
 *
 * <p>Module: wallet</p>
 * <p>Chủ sở hữu: Người C (Dev C)</p>
 */
@Getter
@RequiredArgsConstructor
public enum WalletTransactionType {

    /** Nạp tiền vào ví (Sandbox quick top-up hoặc chuyển khoản). */
    TOP_UP("Nạp tiền vào ví", true),

    /** Người mua thanh toán nạp tiền ký quỹ (Escrow 100%). */
    ESCROW_PAY("Thanh toán ký quỹ mua vé", false),

    /** Người bán nộp cọc bảo lãnh 50%. */
    DEPOSIT_LOCK("Khóa cọc bảo đảm bán vé", false),

    /** Người bán nhận tiền giải ngân bán vé (+ cọc hoàn lại). */
    PAYOUT_RECEIVE("Nhận tiền bán vé thành công", true),

    /** Hoàn cọc 50% cho người bán khi giao dịch thành công. */
    DEPOSIT_REFUND("Hoàn trả tiền cọc bảo lãnh", true),

    /** Hoàn trả 100% tiền ký quỹ cho người mua (hủy giao dịch / hết hạn cọc). */
    ESCROW_REFUND("Hoàn tiền ký quỹ vé", true),

    /** Người mua nhận tiền bồi thường từ cọc của người bán khi thắng khiếu nại. */
    COMPENSATION_RECEIVE("Nhận tiền bồi thường khiếu nại", true);

    private final String displayName;
    private final boolean credit;

    /**
     * Kiểm tra xem loại giao dịch này là cộng tiền (credit) hay trừ tiền (debit).
     *
     * @return true nếu là cộng tiền, false nếu là trừ tiền
     */
    public boolean isCredit() {
        return credit;
    }

    /**
     * Ký tự đại diện dấu cộng hoặc trừ hiển thị trên giao diện.
     *
     * @return "+" hoặc "-"
     */
    public String getSign() {
        return credit ? "+" : "-";
    }
}
