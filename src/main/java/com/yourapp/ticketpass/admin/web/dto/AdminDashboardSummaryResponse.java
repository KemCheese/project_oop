package com.yourapp.ticketpass.admin.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AdminDashboardSummaryResponse {

    private final LocalDate fromDate;
    private final LocalDate toDate;
    private final long releasedCount;
    private final long forfeitedCount;
    private final long liquidationCount;
    private final BigDecimal totalGmv;
    private final BigDecimal totalCommission;
    private final BigDecimal totalForfeitCut;
    @Builder.Default
    private final BigDecimal totalLiquidationFee = BigDecimal.ZERO;
    private final BigDecimal totalPlatformRevenue;
    private final BigDecimal totalTaxWithheld;
    private final BigDecimal liveHeldEscrowAmount;
    private final List<TicketCategoryBreakdownDto> categoryBreakdowns;
    private final List<RevenueLedgerRowDto> ledgerRows;

    public List<TicketCategoryBreakdownDto> getCategoryBreakdowns() {
        return categoryBreakdowns != null ? List.copyOf(categoryBreakdowns) : List.of();
    }

    public List<RevenueLedgerRowDto> getLedgerRows() {
        return ledgerRows != null ? List.copyOf(ledgerRows) : List.of();
    }
}
