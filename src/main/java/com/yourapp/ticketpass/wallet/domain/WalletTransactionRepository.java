package com.yourapp.ticketpass.wallet.domain;

import java.util.List;
import java.util.Optional;

public interface WalletTransactionRepository {

    WalletTransaction save(WalletTransaction transaction);

    Optional<WalletTransaction> findById(Long id);

    List<WalletTransaction> findByWalletIdOrderByCreatedAtDesc(Long walletId);

    List<WalletTransaction> findByWalletIdOrderByCreatedAtDesc(Long walletId, int limit);
}
