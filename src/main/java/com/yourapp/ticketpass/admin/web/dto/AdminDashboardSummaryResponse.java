package com.yourapp.ticketpass.admin.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

/**
 * Dữ liệu tổng hợp cho trang Admin Financial Dashboard ({@code GET /admin/dashboard}).
 *
 * <p>Tách bạch rõ 3 nhóm số liệu theo ADR-0009:</p>
 * <ul>
 *   <li>Số liệu doanh thu trong kỳ lọc (Cash-basis theo {@code releasedAt}/{@code forfeitedAt}).</li>
 *   <li>Thuế khấu trừ giữ hộ ({@code totalTaxWithheld}) hiển thị tách biệt khỏi doanh thu sàn.</li>
 *   <li>Snapshot tiền ký quỹ đang giữ hộ ({@code liveHeldEscrowAmount}) tại thời điểm hiện tại.</li>
 * </ul>
 *
 * <p>Module: admin</p>
 * <p>Chủ sở hữu: Người C (Dev C)</p>
 */
@Getter
@Builder
public class AdminDashboardSummaryResponse {

    private final LocalDate fromDate;
    private final LocalDate toDate;
    private final long releasedCount;
    private final long forfeitedCount;
    private final BigDecimal totalGmv;
    private final BigDecimal totalCommission;
    private final BigDecimal totalForfeitCut;
    private final BigDecimal totalPlatformRevenue;
    private final BigDecimal totalTaxWithheld;
    private final BigDecimal liveHeldEscrowAmount;
    private final List<TicketCategoryBreakdownDto> categoryBreakdowns;
    private final List<RevenueLedgerRowDto> ledgerRows;

    /** Bảo đảm danh sách bất biến khi truy xuất từ tầng Web. */
    public List<TicketCategoryBreakdownDto> getCategoryBreakdowns() {
        return categoryBreakdowns != null ? List.copyOf(categoryBreakdowns) : List.of();
    }

    /** Bảo đảm danh sách sổ cái bất biến khi truy xuất từ tầng Web. */
    public List<RevenueLedgerRowDto> getLedgerRows() {
        return ledgerRows != null ? List.copyOf(ledgerRows) : List.of();
    }
}
