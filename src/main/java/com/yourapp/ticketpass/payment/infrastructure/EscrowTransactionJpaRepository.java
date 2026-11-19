package com.yourapp.ticketpass.payment.infrastructure;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.yourapp.ticketpass.payment.domain.EscrowStatus;
import com.yourapp.ticketpass.payment.domain.EscrowTransaction;

/**
 * Repository Spring Data JPA cho {@link EscrowTransaction}.
 *
 * <p>Đây là chi tiết hạ tầng, chỉ được dùng bởi
 * {@link EscrowTransactionRepositoryImpl}. Tầng nghiệp vụ trong
 * {@code payment.application} không bao giờ tiêm trực tiếp interface này, mà tiêm cổng
 * {@code payment.domain.EscrowTransactionRepository} để không bị lệ thuộc vào Spring Data.</p>
 *
 * <p>Module: payment</p>
 *
 * <p>Chủ sở hữu: Người B</p>
 */
public interface EscrowTransactionJpaRepository extends JpaRepository<EscrowTransaction, Long> {

    /**
     * Tìm giao dịch ký quỹ mới nhất của một vé.
     *
     * @param ticketId ID của vé
     * @return giao dịch tương ứng nếu có
     */
    Optional<EscrowTransaction> findFirstByTicketIdOrderByIdDesc(Long ticketId);

    /**
     * Tìm các giao dịch ký quỹ theo người mua.
     *
     * @param buyerId ID người mua
     * @return danh sách giao dịch
     */
    List<EscrowTransaction> findByBuyerId(Long buyerId);

    /**
     * Tìm các giao dịch ký quỹ theo người bán.
     *
     * @param sellerId ID người bán
     * @return danh sách giao dịch
     */
    List<EscrowTransaction> findBySellerId(Long sellerId);

    /**
     * Tìm các giao dịch ký quỹ theo trạng thái.
     *
     * @param status trạng thái cần lọc
     * @return danh sách giao dịch
     */
    List<EscrowTransaction> findByStatus(EscrowStatus status);

    /**
     * Tìm giao dịch ký quỹ theo mã giao dịch của cổng thanh toán.
     *
     * @param gatewayTransactionId mã giao dịch của cổng thanh toán
     * @return giao dịch tương ứng nếu có
     */
    Optional<EscrowTransaction> findByGatewayTransactionId(String gatewayTransactionId);

    /**
     * Truy vấn các giao dịch đã giải ngân trong khoảng nửa khoảng [startInclusive, endExclusive).
     */
    @Query("SELECT e FROM EscrowTransaction e " +
           "WHERE e.status = com.yourapp.ticketpass.payment.domain.EscrowStatus.RELEASED " +
           "AND e.releasedAt >= :startInclusive AND e.releasedAt < :endExclusive " +
           "ORDER BY e.releasedAt DESC, e.id DESC")
    List<EscrowTransaction> findReleasedBetween(@Param("startInclusive") Instant startInclusive,
                                                @Param("endExclusive") Instant endExclusive);

    /**
     * Tổng số tiền đang thực giữ ở trạng thái HELD (loại trừ RELEASED để tránh amountHeld còn lưu snapshot).
     */
    @Query("SELECT COALESCE(SUM(e.amountHeld), 0.00) FROM EscrowTransaction e " +
           "WHERE e.status = com.yourapp.ticketpass.payment.domain.EscrowStatus.HELD")
    BigDecimal sumHeldAmount();
}
