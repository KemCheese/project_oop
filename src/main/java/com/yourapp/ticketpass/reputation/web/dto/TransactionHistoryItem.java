package com.yourapp.ticketpass.reputation.web.dto;

import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class TransactionHistoryItem {

    private Long ticketId;

    private String eventName;

    private String title;

    private BigDecimal salePrice;

    private String role;

    private String outcomeLabel;

    private String outcomeColor;

    private Instant eventAt;

    private String rawStatus;
}
