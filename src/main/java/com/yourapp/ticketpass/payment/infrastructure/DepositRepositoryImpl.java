package com.yourapp.ticketpass.payment.infrastructure;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.yourapp.ticketpass.payment.domain.Deposit;
import com.yourapp.ticketpass.payment.domain.DepositRepository;
import com.yourapp.ticketpass.payment.domain.DepositStatus;

import lombok.RequiredArgsConstructor;

/**
 * Hiện thực cổng {@link DepositRepository} bằng Spring Data JPA.
 *
 * <p>Lớp này chỉ uỷ quyền từng lời gọi xuống {@link DepositJpaRepository}, không chứa logic
 * nghiệp vụ, giữ cho tầng {@code payment.application} độc lập với Spring Data.</p>
 *
 * <p>Module: payment</p>
 *
 * <p>Chủ sở hữu: Người B</p>
 */
@Repository
@RequiredArgsConstructor
public class DepositRepositoryImpl implements DepositRepository {

    /** Repository Spring Data thực hiện truy vấn xuống cơ sở dữ liệu. */
    private final DepositJpaRepository jpaRepository;

    @Override
    public Deposit save(Deposit deposit) {
        return jpaRepository.save(deposit);
    }

    @Override
    public Optional<Deposit> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public Optional<Deposit> findByTicketId(Long ticketId) {
        return jpaRepository.findByTicketId(ticketId);
    }

    @Override
    public List<Deposit> findBySellerId(Long sellerId) {
        return jpaRepository.findBySellerId(sellerId);
    }

    @Override
    public List<Deposit> findByStatus(DepositStatus status) {
        return jpaRepository.findByStatus(status);
    }
}
