package com.yourapp.ticketpass.wallet.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum WalletTransactionType {

    TOP_UP("Nạp tiền vào ví", true),

    ESCROW_PAY("Thanh toán ký quỹ mua vé", false),

    DEPOSIT_LOCK("Khóa cọc bảo đảm bán vé", false),

    PAYOUT_RECEIVE("Nhận tiền bán vé thành công", true),

    DEPOSIT_REFUND("Hoàn trả tiền cọc bảo lãnh", true),

    ESCROW_REFUND("Hoàn tiền ký quỹ vé", true),

    COMPENSATION_RECEIVE("Nhận tiền ", true),

    LIQUIDATION_FEE("Phí thanh lý tài khoản bị khóa", false),

    LIQUIDATION_PAYOUT("Thanh lý hoàn trả số dư về tài khoản ngân hàng", false);

    private final String displayName;
    private final boolean credit;

    public boolean isCredit() {
        return credit;
    }

    public String getSign() {
        return credit ? "+" : "-";
    }
}
