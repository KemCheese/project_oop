package com.yourapp.ticketpass.wallet.infrastructure;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.yourapp.ticketpass.wallet.domain.WalletTransaction;

/**
 * Spring Data JPA Repository cho thực thể {@link WalletTransaction}.
 *
 * <p>Module: wallet</p>
 * <p>Chủ sở hữu: Người C (Dev C)</p>
 */
public interface WalletTransactionJpaRepository extends JpaRepository<WalletTransaction, Long> {

    List<WalletTransaction> findByWalletIdOrderByCreatedAtDesc(Long walletId);

    List<WalletTransaction> findByWalletIdOrderByCreatedAtDesc(Long walletId, Pageable pageable);
}
