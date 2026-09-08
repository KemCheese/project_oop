package com.yourapp.ticketpass.dispute.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Cổng (port) truy cập dữ liệu cho {@link Dispute}.
 *
 * <p>Interface này thuộc tầng domain và không biết gì về JPA hay Spring Data. Lớp hiện thực
 * nằm ở {@code dispute.infrastructure.DisputeRepositoryImpl}.</p>
 *
 * <p>Module: dispute</p>
 *
 * <p>Chủ sở hữu: Người B</p>
 */
public interface DisputeRepository {

    /**
     * Lưu mới hoặc cập nhật một khiếu nại.
     *
     * @param dispute khiếu nại cần lưu
     * @return bản ghi sau khi lưu, đã có {@code id} và {@code version} mới nhất
     */
    Dispute save(Dispute dispute);

    /**
     * Tìm khiếu nại theo khoá chính.
     *
     * @param id khoá chính
     * @return khiếu nại tương ứng, hoặc {@link Optional#empty()} nếu không tồn tại
     */
    Optional<Dispute> findById(Long id);

    /**
     * Tìm khiếu nại của một vé.
     *
     * @param ticketId ID của vé
     * @return khiếu nại tương ứng, hoặc {@link Optional#empty()} nếu vé chưa bị khiếu nại
     */
    Optional<Dispute> findByTicketId(Long ticketId);

    /**
     * Liệt kê các khiếu nại do một người mua mở.
     *
     * @param buyerId ID người mua
     * @return danh sách khiếu nại, rỗng nếu không có
     */
    List<Dispute> findByBuyerId(Long buyerId);

    /**
     * Liệt kê các khiếu nại nhắm vào một người bán.
     *
     * @param sellerId ID người bán
     * @return danh sách khiếu nại, rỗng nếu không có
     */
    List<Dispute> findBySellerId(Long sellerId);

    /**
     * Liệt kê các khiếu nại theo trạng thái; dùng cho trang xử lý của admin.
     *
     * @param status trạng thái cần lọc
     * @return danh sách khiếu nại, rỗng nếu không có
     */
    List<Dispute> findByStatus(DisputeStatus status);

    /**
     * Tìm các khiếu nại mà người bán đã quá hạn phản hồi.
     *
     * <p>Dùng bởi job định kỳ {@code AutoCompleteScheduler} để chuyển hồ sơ sang
     * {@link DisputeStatus#EXPIRED}.</p>
     *
     * @param before mốc thời gian so sánh, thường là {@code Instant.now()}
     * @return danh sách khiếu nại đang chờ phản hồi mà đã quá {@code sellerResponseDeadline}
     */
    List<Dispute> findOverdueSellerResponses(Instant before);

    /**
     * Kiểm tra một vé có khiếu nại nào đang ở một trong các trạng thái cho trước hay không.
     *
     * <p>Dùng ở hai chỗ: chặn người mua mở khiếu nại trùng lặp, và chặn job tự động hoàn tất
     * giao dịch của một vé đang có tranh chấp chưa xử lý xong.</p>
     *
     * @param ticketId ID của vé
     * @param statuses danh sách trạng thái cần kiểm tra
     * @return {@code true} nếu tồn tại ít nhất một khiếu nại khớp điều kiện
     */
    boolean existsByTicketIdAndStatusIn(Long ticketId, List<DisputeStatus> statuses);
}
