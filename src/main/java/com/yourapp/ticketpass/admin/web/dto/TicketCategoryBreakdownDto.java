package com.yourapp.ticketpass.admin.web.dto;

import com.yourapp.ticketpass.ticket.domain.TicketType;
import java.math.BigDecimal;
import lombok.Builder;
import lombok.Getter;

/**
 * Thống kê vòng đời và tỷ lệ giao dịch theo từng loại vé ({@code MOVIE}, {@code CONCERT}, {@code BUS}).
 *
 * <p>Tách biệt nhóm <b>trong kỳ lọc</b> (lọc theo {@code completedAt}) và nhóm
 * <b>snapshot live tức thời</b> (toàn sàn) để không gây lệch Cohort vs Cash-basis.</p>
 *
 * <p>Module: admin</p>
 * <p>Chủ sở hữu: Người C (Dev C)</p>
 */
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

