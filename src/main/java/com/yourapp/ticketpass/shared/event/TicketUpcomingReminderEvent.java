package com.yourapp.ticketpass.shared.event;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Sự kiện domain: Đến hạn nhắc người mua sử dụng vé trước giờ sự kiện bắt đầu
 * (mặc định 24 giờ, hoặc nhắc sớm ngay khi Seller khóa cọc nếu vé được mua sát giờ &lt;= 24h).
 *
 * <p>Module: shared</p>
 * <p>Chủ sở hữu: Người C (Dev C)</p>
 */
@Getter
@AllArgsConstructor
public class TicketUpcomingReminderEvent {

    private final Long ticketId;
    private final Long sellerId;
    private final Long buyerId;
    private final String ticketTitle;
    private final String venue;
    private final Instant eventStartAt;
    private final Instant eventEndAt;
    private final boolean lastMinutePurchase;
    private final Instant occurredAt;
}
