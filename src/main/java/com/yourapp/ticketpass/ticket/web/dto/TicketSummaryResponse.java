package com.yourapp.ticketpass.ticket.web.dto;

import com.yourapp.ticketpass.ticket.domain.TicketStatus;
import com.yourapp.ticketpass.ticket.domain.TicketType;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class TicketSummaryResponse {

    private Long id;

    private TicketType ticketType;

    private TicketStatus status;

    private String title;

    private String eventName;

    private Instant eventStartAt;

    private Instant eventEndAt;

    private BigDecimal salePrice;

    private String thumbnailUrl;
}
