package com.yourapp.ticketpass.admin.web.dto;

import com.yourapp.ticketpass.ticket.domain.TicketType;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Builder;
import lombok.Getter;

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

    public String getReferenceCode() {
        if (referenceId == null) {
            return "";
        }
        String prefix;
        if ("DEPOSIT_FORFEITED".equals(sourceType)) {
            prefix = "DEP-";
        } else if ("ACCOUNT_LIQUIDATION_FEE".equals(sourceType)) {
            prefix = "LIQ-";
        } else {
            prefix = "ESC-";
        }
        return prefix + referenceId;
    }
}
