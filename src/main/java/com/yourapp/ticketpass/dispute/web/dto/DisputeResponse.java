package com.yourapp.ticketpass.dispute.web.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.yourapp.ticketpass.dispute.domain.DisputeReason;
import com.yourapp.ticketpass.dispute.domain.DisputeStatus;
import com.yourapp.ticketpass.shared.event.DisputeOutcome;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class DisputeResponse {

    private Long id;

    private Long ticketId;

    private Long buyerId;

    private String buyerName;

    private Long sellerId;

    private String sellerName;

    private DisputeStatus status;

    private DisputeReason reason;

    private String description;

    private Instant filedAt;

    private Instant windowClosesAt;

    private Instant sellerResponseDeadline;

    private String sellerResponse;

    private DisputeOutcome outcome;

    private BigDecimal totalDeposit;

    private BigDecimal founderCut;

    private BigDecimal buyerCompensation;

    private List<DisputeEvidenceResponse> evidences;
}
