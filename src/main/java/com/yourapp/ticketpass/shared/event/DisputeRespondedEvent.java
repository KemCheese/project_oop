package com.yourapp.ticketpass.shared.event;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Sự kiện: Người bán đã nộp giải trình cho khiếu nại.
 * Người mua cần nhận thông báo để theo dõi tiến độ phân xử.
 *
 * <p>Module: shared</p>
 * <p>Chủ sở hữu: Người C (Dev C)</p>
 */
@Getter
@AllArgsConstructor
public class DisputeRespondedEvent {
    private final Long disputeId;
    private final Long ticketId;
    private final Long sellerId;
    private final Long buyerId;
    private final String ticketTitle;
    private final Instant occurredAt;
}
