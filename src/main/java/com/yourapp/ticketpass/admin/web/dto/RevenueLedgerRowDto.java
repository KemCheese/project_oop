package com.yourapp.ticketpass.admin.web.dto;

import com.yourapp.ticketpass.ticket.domain.TicketType;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Builder;
import lombok.Getter;

/**
 * Một dòng sổ cái (ledger) ghi nhận doanh thu sàn trong kỳ lọc trên Admin Dashboard
 * và khi xuất file CSV.
 *
 * <p>Gồm hai loại nguồn: {@code ESCROW_RELEASED} (GMV = salePrice, PlatformRevenue = totalCommission,
 * Tax = taxWithheld) và {@code DEPOSIT_FORFEITED} (GMV = 0, PlatformRevenue = founderCut, Tax = 0).</p>
 *
 * <p>Module: admin</p>
 * <p>Chủ sở hữu: Người C (Dev C)</p>
 */
@Getter
@Builder
public class RevenueLedgerRowDto {

    private final String sourceType;
    private final String sourceLabel;
    private final Instant occurredAt;
    private final String occurredAtDisplay;
    private final Long referenceId;
    private final Long ticketId;
    private final String ticketTitle;
    private final TicketType ticketType;
    private final BigDecimal gmv;
    private final BigDecimal platformRevenue;
    private final BigDecimal taxWithheld;
    private final String note;

    /**
     * Trả về mã chứng từ chuẩn hóa ({@code ESC-<id>} hoặc {@code DEP-<id>}) để dùng thống nhất
     * giữa giao diện Dashboard và file CSV xuất ra.
     */
    public String getReferenceCode() {
        if (referenceId == null) {
            return "";
        }
        String prefix = "DEPOSIT_FORFEITED".equals(sourceType) ? "DEP-" : "ESC-";
        return prefix + referenceId;
    }
}
