package com.yourapp.ticketpass.wallet.infrastructure;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import com.yourapp.ticketpass.wallet.domain.WalletTransaction;
import com.yourapp.ticketpass.wallet.domain.WalletTransactionRepository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class WalletTransactionRepositoryImpl implements WalletTransactionRepository {

    private final WalletTransactionJpaRepository walletTransactionJpaRepository;

    @Override
    public WalletTransaction save(WalletTransaction transaction) {
        return walletTransactionJpaRepository.save(transaction);
    }

    @Override
    public Optional<WalletTransaction> findById(Long id) {
        return walletTransactionJpaRepository.findById(id);
    }

    @Override
    public List<WalletTransaction> findByWalletIdOrderByCreatedAtDesc(Long walletId) {
        return walletTransactionJpaRepository.findByWalletIdOrderByCreatedAtDesc(walletId);
    }

    @Override
    public List<WalletTransaction> findByWalletIdOrderByCreatedAtDesc(Long walletId, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 500));
        return walletTransactionJpaRepository.findByWalletIdOrderByCreatedAtDesc(walletId, PageRequest.of(0, safeLimit));
    }
}
