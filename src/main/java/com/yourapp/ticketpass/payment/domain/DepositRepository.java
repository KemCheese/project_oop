package com.yourapp.ticketpass.payment.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Cổng (port) truy cập dữ liệu cho {@link Deposit}.
 *
 * <p>Interface này thuộc tầng domain và không biết gì về JPA hay Spring Data. Lớp hiện thực
 * nằm ở {@code payment.infrastructure.DepositRepositoryImpl} và chỉ uỷ quyền xuống
 * {@code DepositJpaRepository}.</p>
 *
 * <p>Module: payment</p>
 *
 * <p>Chủ sở hữu: Người B</p>
 */
public interface DepositRepository {

    /**
     * Lưu mới hoặc cập nhật một khoản cọc.
     *
     * @param deposit khoản cọc cần lưu
     * @return bản ghi sau khi lưu, đã có {@code id} và {@code version} mới nhất
     */
    Deposit save(Deposit deposit);

    /**
     * Tìm khoản cọc theo khoá chính.
     *
     * @param id khoá chính
     * @return khoản cọc tương ứng, hoặc {@link Optional#empty()} nếu không tồn tại
     */
    Optional<Deposit> findById(Long id);

    /**
     * Tìm khoản cọc của một vé. Mỗi vé chỉ có tối đa một khoản cọc đang hiệu lực.
     *
     * @param ticketId ID của vé
     * @return khoản cọc tương ứng, hoặc {@link Optional#empty()} nếu vé chưa khoá cọc
     */
    Optional<Deposit> findByTicketId(Long ticketId);

    /**
     * Liệt kê toàn bộ khoản cọc của một người bán.
     *
     * @param sellerId ID người bán
     * @return danh sách khoản cọc, rỗng nếu không có
     */
    List<Deposit> findBySellerId(Long sellerId);

    /**
     * Liệt kê các khoản cọc theo trạng thái, dùng cho đối soát và job định kỳ.
     *
     * @param status trạng thái cần lọc
     * @return danh sách khoản cọc, rỗng nếu không có
     */
    List<Deposit> findByStatus(DepositStatus status);

    /**
     * Lấy danh sách khoản cọc đã bị tịch thu ({@link DepositStatus#FORFEITED}) trong khoảng
     * nửa khoảng {@code [startInclusive, endExclusive)} tính theo {@code forfeitedAt}.
     *
     * @param startInclusive mốc bắt đầu (bao gồm)
     * @param endExclusive mốc kết thúc (không bao gồm)
     * @return danh sách khoản cọc sắp xếp mới nhất trước
     */
    List<Deposit> findForfeitedBetween(Instant startInclusive, Instant endExclusive);
}
