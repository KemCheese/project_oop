package com.yourapp.ticketpass.wallet.domain;

import java.util.List;
import java.util.Optional;

/**
 * Cổng trừu tượng (Port) lưu trữ và truy vấn sổ cái biến động số dư.
 *
 * <p>Module: wallet</p>
 * <p>Chủ sở hữu: Người C (Dev C)</p>
 */
public interface WalletTransactionRepository {

    /**
     * Lưu bản ghi giao dịch sổ cái (Append-only).
     *
     * @param transaction bản ghi giao dịch
     * @return bản ghi đã lưu
     */
    WalletTransaction save(WalletTransaction transaction);

    /**
     * Tra cứu bản ghi theo ID.
     *
     * @param id ID giao dịch
     * @return {@link Optional} chứa bản ghi
     */
    Optional<WalletTransaction> findById(Long id);

    /**
     * Lấy toàn bộ lịch sử biến động số dư của một ví, sắp xếp mới nhất lên đầu.
     *
     * @param walletId ID ví
     * @return danh sách các giao dịch sổ cái
     */
    List<WalletTransaction> findByWalletIdOrderByCreatedAtDesc(Long walletId);

    /**
     * Lấy danh sách giao dịch gần nhất có giới hạn số lượng.
     *
     * @param walletId ID ví
     * @param limit    số lượng bản ghi tối đa
     * @return danh sách các giao dịch sổ cái gần nhất
     */
    List<WalletTransaction> findByWalletIdOrderByCreatedAtDesc(Long walletId, int limit);
}
