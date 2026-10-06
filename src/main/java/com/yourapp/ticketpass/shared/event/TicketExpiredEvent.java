package com.yourapp.ticketpass.shared.event;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Sự kiện: Vé đang rao bán (LISTED) hết hạn thời gian (eventStartAt <= now)
 * và được hệ thống tự động gỡ khỏi sàn.
 *
 * <p>Module: shared</p>
 * <p>Chủ sở hữu: Người C (Dev C)</p>
 */
@Getter
@AllArgsConstructor
public class TicketExpiredEvent {
    private final Long ticketId;
    private final Long sellerId;
    private final String ticketTitle;
    private final Instant expiredAt;
}
