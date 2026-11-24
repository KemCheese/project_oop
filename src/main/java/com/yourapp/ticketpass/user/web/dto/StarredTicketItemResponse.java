package com.yourapp.ticketpass.user.web.dto;

import com.yourapp.ticketpass.ticket.domain.TicketStatus;
import com.yourapp.ticketpass.ticket.domain.TicketType;
import java.math.BigDecimal;
import java.time.Instant;

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
