package com.yourapp.ticketpass.shared.event;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Sự kiện: Người mua mở khiếu nại vé.
 * Người bán cần nhận thông báo để vào nộp giải trình trong 24 giờ.
 *
 * <p>Module: shared</p>
 * <p>Chủ sở hữu: Người C (Dev C)</p>
 */
@Getter
@AllArgsConstructor
public class DisputeOpenedEvent {
    private final Long disputeId;
    private final Long ticketId;
    private final Long sellerId;
    private final Long buyerId;
    private final String ticketTitle;
    private final String reason;
    private final Instant occurredAt;
}
