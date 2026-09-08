package com.yourapp.ticketpass.dispute.infrastructure;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.yourapp.ticketpass.dispute.domain.Dispute;
import com.yourapp.ticketpass.dispute.domain.DisputeRepository;
import com.yourapp.ticketpass.dispute.domain.DisputeStatus;

import lombok.RequiredArgsConstructor;

/**
 * Hiện thực cổng {@link DisputeRepository} bằng Spring Data JPA.
 *
 * <p>Lớp này chỉ uỷ quyền xuống {@link DisputeJpaRepository}, không chứa logic nghiệp vụ.
 * Riêng {@link #findOverdueSellerResponses(Instant)} là một phép ánh xạ tên: cổng domain dùng
 * tên theo ý nghĩa nghiệp vụ ("khiếu nại quá hạn phản hồi"), còn Spring Data cần tên method mô
 * tả đúng điều kiện truy vấn, nên ở đây gán sẵn trạng thái
 * {@link DisputeStatus#AWAITING_SELLER_RESPONSE}.</p>
 *
 * <p>Module: dispute</p>
 *
 * <p>Chủ sở hữu: Người B</p>
 */
@Repository
@RequiredArgsConstructor
public class DisputeRepositoryImpl implements DisputeRepository {

    /** Repository Spring Data thực hiện truy vấn xuống cơ sở dữ liệu. */
    private final DisputeJpaRepository jpaRepository;

    @Override
    public Dispute save(Dispute dispute) {
        return jpaRepository.save(dispute);
    }

    @Override
    public Optional<Dispute> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public Optional<Dispute> findByTicketId(Long ticketId) {
        return jpaRepository.findByTicketId(ticketId);
    }

    @Override
    public List<Dispute> findByBuyerId(Long buyerId) {
        return jpaRepository.findByBuyerId(buyerId);
    }

    @Override
    public List<Dispute> findBySellerId(Long sellerId) {
        return jpaRepository.findBySellerId(sellerId);
    }

    @Override
    public List<Dispute> findByStatus(DisputeStatus status) {
        return jpaRepository.findByStatus(status);
    }

    @Override
    public List<Dispute> findOverdueSellerResponses(Instant before) {
        return jpaRepository.findByStatusAndSellerResponseDeadlineBefore(DisputeStatus.AWAITING_SELLER_RESPONSE, before);
    }

    @Override
    public boolean existsByTicketIdAndStatusIn(Long ticketId, List<DisputeStatus> statuses) {
        return jpaRepository.existsByTicketIdAndStatusIn(ticketId, statuses);
    }
}
