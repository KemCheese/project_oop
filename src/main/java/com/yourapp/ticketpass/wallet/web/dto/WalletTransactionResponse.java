package com.yourapp.ticketpass.wallet.web.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import com.yourapp.ticketpass.wallet.domain.WalletTransaction;
import com.yourapp.ticketpass.wallet.domain.WalletTransactionType;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class WalletTransactionResponse {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss dd/MM/yyyy")
            .withZone(ZoneId.systemDefault());

    private Long id;
    private Long walletId;
    private BigDecimal amount;
    private BigDecimal balanceBefore;
    private BigDecimal balanceAfter;
    private WalletTransactionType type;
    private String typeDisplayName;
    private boolean credit;
    private String sign;
    private String referenceId;
    private String description;
    private String status;
    private Instant createdAt;
    private String formattedCreatedAt;

    public static WalletTransactionResponse fromDomain(WalletTransaction tx) {
        if (tx == null) {
            return null;
        }
        return WalletTransactionResponse.builder()
                .id(tx.getId())
                .walletId(tx.getWalletId())
                .amount(tx.getAmount())
                .balanceBefore(tx.getBalanceBefore())
                .balanceAfter(tx.getBalanceAfter())
                .type(tx.getType())
                .typeDisplayName(tx.getType() != null ? tx.getType().getDisplayName() : "")
                .credit(tx.getType() != null && tx.getType().isCredit())
                .sign(tx.getType() != null ? tx.getType().getSign() : "")
                .referenceId(tx.getReferenceId())
                .description(tx.getDescription())
                .status(tx.getStatus())
                .createdAt(tx.getCreatedAt())
                .formattedCreatedAt(tx.getCreatedAt() != null ? FORMATTER.format(tx.getCreatedAt()) : "")
                .build();
    }
}
