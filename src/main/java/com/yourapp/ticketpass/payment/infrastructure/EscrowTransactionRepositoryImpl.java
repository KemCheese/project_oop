package com.yourapp.ticketpass.payment.infrastructure;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.yourapp.ticketpass.payment.domain.EscrowStatus;
import com.yourapp.ticketpass.payment.domain.EscrowTransaction;
import com.yourapp.ticketpass.payment.domain.EscrowTransactionRepository;

import lombok.RequiredArgsConstructor;

/**
 * Hiện thực cổng {@link EscrowTransactionRepository} bằng Spring Data JPA.
 *
 * <p>Lớp này là bản lề giữa tầng domain và hạ tầng: nó chỉ uỷ quyền từng lời gọi xuống
 * {@link EscrowTransactionJpaRepository}, không chứa bất kỳ logic nghiệp vụ nào. Nhờ lớp mỏng
 * này, tầng {@code payment.application} chỉ biết tới interface trong {@code payment.domain} và
 * có thể được kiểm thử bằng một hiện thực giả trong bộ nhớ.</p>
 *
 * <p>Module: payment</p>
 *
 * <p>Chủ sở hữu: Người B</p>
 */
@Repository
@RequiredArgsConstructor
public class EscrowTransactionRepositoryImpl implements EscrowTransactionRepository {

    /** Repository Spring Data thực hiện truy vấn xuống cơ sở dữ liệu. */
    private final EscrowTransactionJpaRepository jpaRepository;

    @Override
    public EscrowTransaction save(EscrowTransaction tx) {
        return jpaRepository.save(tx);
    }

    @Override
    public Optional<EscrowTransaction> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public Optional<EscrowTransaction> findByTicketId(Long ticketId) {
        return jpaRepository.findFirstByTicketIdOrderByIdDesc(ticketId);
    }

    @Override
    public List<EscrowTransaction> findByBuyerId(Long buyerId) {
        return jpaRepository.findByBuyerId(buyerId);
    }

    @Override
    public List<EscrowTransaction> findBySellerId(Long sellerId) {
        return jpaRepository.findBySellerId(sellerId);
    }

    @Override
    public List<EscrowTransaction> findByStatus(EscrowStatus status) {
        return jpaRepository.findByStatus(status);
    }

    @Override
    public Optional<EscrowTransaction> findByGatewayTransactionId(String gatewayTransactionId) {
        return jpaRepository.findByGatewayTransactionId(gatewayTransactionId);
    }

    @Override
    public List<EscrowTransaction> findReleasedBetween(Instant startInclusive, Instant endExclusive) {
        if (startInclusive == null || endExclusive == null) {
            return List.of();
        }
        return jpaRepository.findReleasedBetween(startInclusive, endExclusive);
    }

    @Override
    public BigDecimal sumHeldAmount() {
        BigDecimal total = jpaRepository.sumHeldAmount();
        return total != null ? total : BigDecimal.ZERO;
    }
}
