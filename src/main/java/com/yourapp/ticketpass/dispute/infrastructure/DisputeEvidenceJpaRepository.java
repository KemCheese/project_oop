package com.yourapp.ticketpass.dispute.infrastructure;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.yourapp.ticketpass.dispute.domain.DisputeEvidence;

/**
 * Repository Spring Data JPA cho {@link DisputeEvidence}.
 *
 * <p>Đây là chi tiết hạ tầng, chỉ được dùng bởi {@link DisputeEvidenceRepositoryImpl}.</p>
 *
 * <p>Module: dispute</p>
 *
 * <p>Chủ sở hữu: Người B</p>
 */
public interface DisputeEvidenceJpaRepository extends JpaRepository<DisputeEvidence, Long> {

    /**
     * Lấy toàn bộ bằng chứng của một khiếu nại.
     *
     * @param disputeId ID của khiếu nại
     * @return danh sách bằng chứng
     */
    List<DisputeEvidence> findByDisputeId(Long disputeId);
}
