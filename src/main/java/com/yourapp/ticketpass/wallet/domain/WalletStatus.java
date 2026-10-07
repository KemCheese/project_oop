package com.yourapp.ticketpass.wallet.domain;

/**
 * Trạng thái hoạt động của ví điện tử.
 *
 * <p>Module: wallet</p>
 * <p>Chủ sở hữu: Người C (Dev C)</p>
 */
public enum WalletStatus {
    /** Ví đang hoạt động bình thường, cho phép nạp, rút, thanh toán. */
    ACTIVE,

    /** Ví bị khóa do vi phạm hoặc tranh chấp, không cho phép trừ tiền. */
    LOCKED
}
