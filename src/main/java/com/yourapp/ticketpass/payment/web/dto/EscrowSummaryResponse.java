package com.yourapp.ticketpass.payment.web.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.yourapp.ticketpass.payment.domain.EscrowStatus;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class EscrowSummaryResponse {

    private Long id;

    private Long ticketId;

    private EscrowStatus status;

    private BigDecimal salePrice;

    private BigDecimal buyerCommission;

    private BigDecimal sellerCommission;

    private BigDecimal taxWithheld;

    private BigDecimal sellerNetPayout;

    private Instant heldAt;

    private Instant releasedAt;
}
