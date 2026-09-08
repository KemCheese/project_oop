package com.yourapp.ticketpass.dispute.infrastructure;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.yourapp.ticketpass.dispute.domain.DisputeEvidence;
import com.yourapp.ticketpass.dispute.domain.DisputeEvidenceRepository;

import lombok.RequiredArgsConstructor;

/**
 * Hiện thực cổng {@link DisputeEvidenceRepository} bằng Spring Data JPA.
 *
 * <p>Lớp này chỉ uỷ quyền xuống {@link DisputeEvidenceJpaRepository}, không chứa logic nghiệp vụ.</p>
 *
 * <p>Module: dispute</p>
 *
 * <p>Chủ sở hữu: Người B</p>
 */
@Repository
@RequiredArgsConstructor
public class DisputeEvidenceRepositoryImpl implements DisputeEvidenceRepository {

    /** Repository Spring Data thực hiện truy vấn xuống cơ sở dữ liệu. */
    private final DisputeEvidenceJpaRepository jpaRepository;

    @Override
    public DisputeEvidence save(DisputeEvidence evidence) {
        return jpaRepository.save(evidence);
    }

    @Override
    public List<DisputeEvidence> findByDisputeId(Long disputeId) {
        return jpaRepository.findByDisputeId(disputeId);
    }

    @Override
    public Optional<DisputeEvidence> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public void deleteById(Long id) {
        jpaRepository.deleteById(id);
    }
}
