package com.yourapp.ticketpass.shared.event;

import java.math.BigDecimal;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Sự kiện: Người mua đã nạp tiền ký quỹ thành công (PAYMENT_ESCROWED).
 * Người bán cần nhận thông báo để vào đóng cọc 50%.
 *
 * <p>Module: shared</p>
 * <p>Chủ sở hữu: Người C (Dev C)</p>
 */
@Getter
@AllArgsConstructor
public class TicketEscrowPaidEvent {
    private final Long ticketId;
    private final Long sellerId;
    private final Long buyerId;
    private final String ticketTitle;
    private final BigDecimal salePrice;
    private final Instant occurredAt;
}
