package com.yourapp.ticketpass.reputation.web.dto;

import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ReputationResponse {

    private Long userId;

    private String fullName;

    private BigDecimal score;

    private int scoreInt;

    private String tierName;

    private String tierBadgeColor;

    private String transactionLimitDescription;

    private Integer totalTransactions;

    private Integer successfulTransactions;

    private Integer disputesLost;

    private Integer disputesWon;

    private Instant lastTransactionAt;
}
