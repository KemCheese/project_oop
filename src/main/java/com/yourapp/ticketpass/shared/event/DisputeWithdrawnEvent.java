package com.yourapp.ticketpass.shared.event;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Sự kiện: Người mua chủ động rút khiếu nại.
 * Người bán cần nhận thông báo để biết giao dịch tiếp tục.
 *
 * <p>Module: shared</p>
 * <p>Chủ sở hữu: Người C (Dev C)</p>
 */
@Getter
@AllArgsConstructor
public class DisputeWithdrawnEvent {
    private final Long disputeId;
    private final Long ticketId;
    private final Long sellerId;
    private final Long buyerId;
    private final String ticketTitle;
    private final Instant occurredAt;
}
