package com.yourapp.ticketpass.user.web.dto;

import com.yourapp.ticketpass.ticket.domain.TicketStatus;
import com.yourapp.ticketpass.ticket.domain.TicketType;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * DTO đại diện cho một vé trong danh sách theo dõi (Watchlist) của người dùng.
 *
 * <p>Được dùng để render trong Tab "Vé đã theo dõi" tại trang Hồ sơ cá nhân (/profile).</p>
 */
public record StarredTicketItemResponse(
    Long ticketId,
    String title,
    String eventName,
    String venue,
    String seatInfo,
    TicketType ticketType,
    TicketStatus status,
    BigDecimal salePrice,
    BigDecimal originalPrice,
    Instant eventStartAt,
    String thumbnailUrl
) {}
