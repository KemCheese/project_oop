package com.yourapp.ticketpass.dispute.domain;

import java.util.List;
import java.util.Optional;

/**
 * Cổng (port) truy cập dữ liệu cho {@link DisputeEvidence}.
 *
 * <p>Vì {@link Dispute} không có tập hợp {@code @OneToMany} (theo nguyên tắc kiến trúc số 1),
 * đây là con đường duy nhất để lấy danh sách bằng chứng của một khiếu nại.</p>
 *
 * <p>Lớp hiện thực nằm ở {@code dispute.infrastructure.DisputeEvidenceRepositoryImpl}.</p>
 *
 * <p>Module: dispute</p>
 *
 * <p>Chủ sở hữu: Người B</p>
 */
public interface DisputeEvidenceRepository {

    /**
     * Lưu mới hoặc cập nhật một mẩu bằng chứng.
     *
     * @param evidence bằng chứng cần lưu
     * @return bản ghi sau khi lưu, đã có {@code id}
     */
    DisputeEvidence save(DisputeEvidence evidence);

    /**
     * Lấy toàn bộ bằng chứng của một khiếu nại.
     *
     * @param disputeId ID của khiếu nại
     * @return danh sách bằng chứng, rỗng nếu chưa có bằng chứng nào
     */
    List<DisputeEvidence> findByDisputeId(Long disputeId);

    /**
     * Tìm một mẩu bằng chứng theo khoá chính.
     *
     * @param id khoá chính
     * @return bằng chứng tương ứng, hoặc {@link Optional#empty()} nếu không tồn tại
     */
    Optional<DisputeEvidence> findById(Long id);

    /**
     * Xoá một mẩu bằng chứng.
     *
     * <p>Chỉ xoá bản ghi trong cơ sở dữ liệu. Việc xoá tệp vật lý tương ứng phải được thực hiện
     * riêng qua {@code FileStorageService.delete(storageKey)}.</p>
     *
     * @param id khoá chính của bằng chứng cần xoá
     */
    void deleteById(Long id);
}
