package com.yourapp.ticketpass.payment.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Cổng (port) truy cập dữ liệu cho {@link EscrowTransaction}.
 *
 * <p>Interface này thuộc tầng domain và không biết gì về JPA hay Spring Data. Lớp hiện thực
 * nằm ở {@code payment.infrastructure.EscrowTransactionRepositoryImpl} và chỉ uỷ quyền
 * xuống {@code EscrowTransactionJpaRepository}. Nhờ vậy tầng nghiệp vụ có thể được kiểm thử
 * bằng một hiện thực trong bộ nhớ, không cần cơ sở dữ liệu thật.</p>
 *
 * <p>Module: payment</p>
 *
 * <p>Chủ sở hữu: Người B</p>
 */
public interface EscrowTransactionRepository {

    /**
     * Lưu mới hoặc cập nhật một giao dịch ký quỹ.
     *
     * @param tx giao dịch cần lưu
     * @return bản ghi sau khi lưu, đã có {@code id} và {@code version} mới nhất
     */
    EscrowTransaction save(EscrowTransaction tx);

    /**
     * Tìm giao dịch ký quỹ theo khoá chính.
     *
     * @param id khoá chính
     * @return giao dịch tương ứng, hoặc {@link Optional#empty()} nếu không tồn tại
     */
    Optional<EscrowTransaction> findById(Long id);

    /**
     * Tìm giao dịch ký quỹ của một vé. Mỗi vé chỉ có tối đa một escrow đang hiệu lực.
     *
     * @param ticketId ID của vé
     * @return giao dịch tương ứng, hoặc {@link Optional#empty()} nếu vé chưa có escrow
     */
    Optional<EscrowTransaction> findByTicketId(Long ticketId);

    /**
     * Liệt kê các giao dịch ký quỹ mà một người dùng đóng vai người mua.
     *
     * @param buyerId ID người mua
     * @return danh sách giao dịch, rỗng nếu không có
     */
    List<EscrowTransaction> findByBuyerId(Long buyerId);

    /**
     * Liệt kê các giao dịch ký quỹ mà một người dùng đóng vai người bán.
     *
     * @param sellerId ID người bán
     * @return danh sách giao dịch, rỗng nếu không có
     */
    List<EscrowTransaction> findBySellerId(Long sellerId);

    /**
     * Liệt kê các giao dịch ký quỹ theo trạng thái, dùng cho đối soát và job định kỳ.
     *
     * @param status trạng thái cần lọc
     * @return danh sách giao dịch, rỗng nếu không có
     */
    List<EscrowTransaction> findByStatus(EscrowStatus status);

    /**
     * Tìm giao dịch ký quỹ theo mã giao dịch của cổng thanh toán.
     *
     * <p>Dùng khi xử lý IPN: cổng thanh toán chỉ gửi mã giao dịch của nó, sàn phải tra lại
     * bản ghi nội bộ tương ứng.</p>
     *
     * @param gatewayTransactionId mã giao dịch của cổng thanh toán
     * @return giao dịch tương ứng, hoặc {@link Optional#empty()} nếu không tìm thấy
     */
    Optional<EscrowTransaction> findByGatewayTransactionId(String gatewayTransactionId);

    /**
     * Lấy danh sách giao dịch ký quỹ đã giải ngân ({@link EscrowStatus#RELEASED}) trong khoảng
     * nửa khoảng {@code [startInclusive, endExclusive)} tính theo {@code releasedAt}.
     *
     * @param startInclusive mốc bắt đầu (bao gồm)
     * @param endExclusive mốc kết thúc (không bao gồm)
     * @return danh sách giao dịch sắp xếp mới nhất trước
     */
    List<EscrowTransaction> findReleasedBetween(Instant startInclusive, Instant endExclusive);

    /**
     * Tính tổng số tiền ký quỹ đang thực giữ hộ ({@link EscrowStatus#HELD}) toàn sàn.
     *
     * @return tổng số tiền đang giữ, trả về {@link BigDecimal#ZERO} nếu không có giao dịch HELD
     */
    BigDecimal sumHeldAmount();
}
