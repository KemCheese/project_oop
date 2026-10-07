package com.yourapp.ticketpass.wallet.domain;

import java.util.Optional;

/**
 * Cổng trừu tượng (Port) truy xuất dữ liệu ví điện tử.
 *
 * <p>Tuân thủ Repository Pattern và Hexagonal Architecture: Interface này thuộc về tầng
 * {@code domain}, hoàn toàn không kế thừa {@code JpaRepository} để giữ cho domain độc lập
 * khỏi framework cơ sở dữ liệu.</p>
 *
 * <p>Module: wallet</p>
 * <p>Chủ sở hữu: Người C (Dev C)</p>
 */
public interface WalletRepository {

    /**
     * Tra cứu ví theo ID của ví.
     *
     * @param id ID ví
     * @return {@link Optional} chứa ví nếu tìm thấy
     */
    Optional<Wallet> findById(Long id);

    /**
     * Tra cứu ví theo ID người sở hữu (không khóa).
     *
     * @param userId ID người dùng
     * @return {@link Optional} chứa ví nếu tìm thấy
     */
    Optional<Wallet> findByUserId(Long userId);

    /**
     * Tra cứu ví theo ID người sở hữu kèm Khóa Bi Quan (Pessimistic Write Lock - SELECT ... FOR UPDATE).
     *
     * <p>Bắt buộc sử dụng phương thức này trước khi thực hiện mọi giao dịch trừ tiền (debit)
     * để chống Race Condition khi có nhiều yêu cầu thanh toán đồng thời.</p>
     *
     * @param userId ID người dùng
     * @return {@link Optional} chứa ví nếu tìm thấy
     */
    Optional<Wallet> findByUserIdWithLock(Long userId);

    /**
     * Lưu hoặc cập nhật thông tin ví.
     *
     * @param wallet thực thể ví cần lưu
     * @return thực thể ví sau khi đã lưu
     */
    Wallet save(Wallet wallet);

    /**
     * Kiểm tra người dùng đã có ví hay chưa.
     *
     * @param userId ID người dùng
     * @return true nếu đã có ví
     */
    boolean existsByUserId(Long userId);
}
