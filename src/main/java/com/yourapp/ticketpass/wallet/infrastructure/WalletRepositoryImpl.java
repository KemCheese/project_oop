package com.yourapp.ticketpass.wallet.infrastructure;

import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.yourapp.ticketpass.wallet.domain.Wallet;
import com.yourapp.ticketpass.wallet.domain.WalletRepository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class WalletRepositoryImpl implements WalletRepository {

    private final WalletJpaRepository walletJpaRepository;

    @Override
    public Optional<Wallet> findById(Long id) {
        return walletJpaRepository.findById(id);
    }

    @Override
    public Optional<Wallet> findByUserId(Long userId) {
        return walletJpaRepository.findByUserId(userId);
    }

    @Override
    public Optional<Wallet> findByUserIdWithLock(Long userId) {
        return walletJpaRepository.findByUserIdWithLock(userId);
    }

    @Override
    public Wallet save(Wallet wallet) {
        return walletJpaRepository.save(wallet);
    }

    @Override
    public boolean existsByUserId(Long userId) {
        return walletJpaRepository.existsByUserId(userId);
    }
}
