package com.yourapp.ticketpass.wallet.web.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.yourapp.ticketpass.wallet.domain.Wallet;
import com.yourapp.ticketpass.wallet.domain.WalletStatus;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class WalletResponse {
    private Long id;
    private Long userId;
    private BigDecimal balance;
    private WalletStatus status;
    private String formattedAccountNumber;
    private Instant createdAt;

    public static WalletResponse fromDomain(Wallet wallet) {
        if (wallet == null) {
            return null;
        }
        String accountNum = String.format("TP-%04d-%04d", (wallet.getUserId() * 17) % 10000, wallet.getId() % 10000);
        return WalletResponse.builder()
                .id(wallet.getId())
                .userId(wallet.getUserId())
                .balance(wallet.getBalance())
                .status(wallet.getStatus())
                .formattedAccountNumber(accountNum)
                .createdAt(wallet.getCreatedAt())
                .build();
    }
}
