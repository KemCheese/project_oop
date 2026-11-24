package com.yourapp.ticketpass.admin.web.dto;

import com.yourapp.ticketpass.ticket.domain.TicketType;
import java.math.BigDecimal;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TicketCategoryBreakdownDto {

    private final TicketType ticketType;
    private final String displayName;

    private final long completedCount;
    private final long buyerWonDisputeCount;
    private final long cancelledCount;
    private final long periodTotalCount;

    private final BigDecimal completionRatePercent;
    private final BigDecimal disputeRatePercent;
    private final BigDecimal cancelRatePercent;

    private final long liveListedCount;
    private final long liveInProgressCount;
    private final long liveDisputeReviewCount;
}
