package com.yourapp.ticketpass.shared.event;

import java.math.BigDecimal;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Sự kiện: Người bán đã nạp và khóa cọc 50% thành công (DEPOSIT_LOCKED / TRANSFERRED).
 * Mã bí mật đã mở, người mua cần nhận thông báo để vào kiểm tra và nhận vé.
 *
 * <p>Module: shared</p>
 * <p>Chủ sở hữu: Người C (Dev C)</p>
 */
@Getter
@AllArgsConstructor
public class TicketDepositLockedEvent {
    private final Long ticketId;
    private final Long sellerId;
    private final Long buyerId;
    private final String ticketTitle;
    private final BigDecimal depositAmount;
    private final Instant occurredAt;
}
