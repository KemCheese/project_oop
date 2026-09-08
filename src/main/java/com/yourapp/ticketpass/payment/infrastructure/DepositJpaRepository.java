package com.yourapp.ticketpass.payment.infrastructure;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.yourapp.ticketpass.payment.domain.Deposit;
import com.yourapp.ticketpass.payment.domain.DepositStatus;

/**
 * Repository Spring Data JPA cho {@link Deposit}.
 *
 * <p>Đây là chi tiết hạ tầng, chỉ được dùng bởi {@link DepositRepositoryImpl}. Tầng nghiệp vụ
 * tiêm cổng {@code payment.domain.DepositRepository} thay vì interface này.</p>
 *
 * <p>Module: payment</p>
 *
 * <p>Chủ sở hữu: Người B</p>
 */
public interface DepositJpaRepository extends JpaRepository<Deposit, Long> {

    /**
     * Tìm khoản cọc của một vé.
     *
     * @param ticketId ID của vé
     * @return khoản cọc tương ứng nếu có
     */
    Optional<Deposit> findByTicketId(Long ticketId);

    /**
     * Tìm các khoản cọc theo người bán.
     *
     * @param sellerId ID người bán
     * @return danh sách khoản cọc
     */
    List<Deposit> findBySellerId(Long sellerId);

    /**
     * Tìm các khoản cọc theo trạng thái.
     *
     * @param status trạng thái cần lọc
     * @return danh sách khoản cọc
     */
    List<Deposit> findByStatus(DepositStatus status);
}
