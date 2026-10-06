package com.yourapp.ticketpass.shared.event;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Sự kiện: Quá hạn nộp cọc 50%, vé bị hủy.
 * Cả người mua (được hoàn tiền) và người bán (bị phạt điểm) cần nhận thông báo.
 *
 * <p>Module: shared</p>
 * <p>Chủ sở hữu: Người C (Dev C)</p>
 */
@Getter
@AllArgsConstructor
public class DepositTimeoutEvent {
    private final Long ticketId;
    private final Long sellerId;
    private final Long buyerId;
    private final String ticketTitle;
    private final Instant occurredAt;
}
