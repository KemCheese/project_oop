package com.yourapp.ticketpass.dispute.infrastructure;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.yourapp.ticketpass.dispute.domain.Dispute;
import com.yourapp.ticketpass.dispute.domain.DisputeStatus;

/**
 * Repository Spring Data JPA cho {@link Dispute}.
 *
 * <p>Đây là chi tiết hạ tầng, chỉ được dùng bởi {@link DisputeRepositoryImpl}. Tầng nghiệp vụ
 * tiêm cổng {@code dispute.domain.DisputeRepository} thay vì interface này.</p>
 *
 * <p>Module: dispute</p>
 *
 * <p>Chủ sở hữu: Người B</p>
 */
public interface DisputeJpaRepository extends JpaRepository<Dispute, Long> {

    /**
     * Tìm khiếu nại của một vé.
     *
     * @param ticketId ID của vé
     * @return khiếu nại tương ứng nếu có
     */
    Optional<Dispute> findByTicketId(Long ticketId);

    /**
     * Tìm các khiếu nại do một người mua mở.
     *
     * @param buyerId ID người mua
     * @return danh sách khiếu nại
     */
    List<Dispute> findByBuyerId(Long buyerId);

    /**
     * Tìm các khiếu nại nhắm vào một người bán.
     *
     * @param sellerId ID người bán
     * @return danh sách khiếu nại
     */
    List<Dispute> findBySellerId(Long sellerId);

    /**
     * Tìm các khiếu nại theo trạng thái.
     *
     * @param status trạng thái cần lọc
     * @return danh sách khiếu nại
     */
    List<Dispute> findByStatus(DisputeStatus status);

    /**
     * Tìm các khiếu nại ở một trạng thái mà đã quá hạn phản hồi của người bán.
     *
     * <p>Truy vấn nền tảng cho {@code DisputeRepository.findOverdueSellerResponses(Instant)}.</p>
     *
     * @param status                 trạng thái cần lọc, thường là {@code AWAITING_SELLER_RESPONSE}
     * @param sellerResponseDeadline mốc thời gian so sánh
     * @return danh sách khiếu nại quá hạn
     */
    List<Dispute> findByStatusAndSellerResponseDeadlineBefore(DisputeStatus status, Instant sellerResponseDeadline);

    /**
     * Kiểm tra một vé có khiếu nại nào ở một trong các trạng thái cho trước hay không.
     *
     * @param ticketId ID của vé
     * @param statuses danh sách trạng thái cần kiểm tra
     * @return {@code true} nếu tồn tại ít nhất một khiếu nại khớp điều kiện
     */
    boolean existsByTicketIdAndStatusIn(Long ticketId, List<DisputeStatus> statuses);
}
