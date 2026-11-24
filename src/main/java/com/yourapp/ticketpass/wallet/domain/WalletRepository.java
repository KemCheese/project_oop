package com.yourapp.ticketpass.wallet.domain;

import java.util.Optional;

public interface WalletRepository {

    Optional<Wallet> findById(Long id);

    Optional<Wallet> findByUserId(Long userId);

    Optional<Wallet> findByUserIdWithLock(Long userId);

    Wallet save(Wallet wallet);

    boolean existsByUserId(Long userId);
}
