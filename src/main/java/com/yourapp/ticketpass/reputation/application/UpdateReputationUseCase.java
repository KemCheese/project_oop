package com.yourapp.ticketpass.reputation.application;

import com.yourapp.ticketpass.reputation.domain.ReputationRepository;
import com.yourapp.ticketpass.reputation.domain.UserReputationScore;
import com.yourapp.ticketpass.shared.event.DisputeResolvedEvent;
import com.yourapp.ticketpass.shared.event.TicketTransactionCompletedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Cập nhật điểm uy tín người dùng dựa trên các sự kiện nghiệp vụ của sàn.
 *
 * <p><b>Nguyên tắc 2 - Observer pattern:</b> module {@code payment} và {@code dispute}
 * <b>KHÔNG</b> gọi trực tiếp module này. Khi một giao dịch hoàn tất hoặc một tranh chấp
 * được phân xử, các module đó chỉ publish sự kiện qua {@code ApplicationEventPublisher};
 * lớp này đăng ký lắng nghe và tự cập nhật điểm. Nhờ vậy có thể thêm bớt hệ quả của một sự
 * kiện (gửi email, ghi log kiểm toán, tính điểm) mà không phải sửa code của module phát
 * sinh sự kiện.</p>
 *
 * <p><b>Vì sao dùng {@code AFTER_COMMIT}:</b> điểm uy tín chỉ được phép thay đổi khi
 * transaction tiền tệ đã commit thành công. Nếu lắng nghe đồng bộ trong cùng transaction,
 * một lỗi khi giải ngân sẽ rollback tiền nhưng điểm có thể đã bị thay đổi sai lệch, hoặc
 * ngược lại lỗi khi tính điểm lại làm hỏng cả giao dịch tiền. Tách bằng
 * {@link TransactionPhase#AFTER_COMMIT} giữ cho hai việc độc lập.</p>
 *
 * <p><b>Quy tắc cộng/trừ điểm ĐỀ XUẤT</b> (Prompt 2 hiện thực, thang 0-100):</p>
 * <ul>
 *   <li>Giao dịch hoàn tất: <b>+2</b> điểm, không vượt trần 100.</li>
 *   <li>Thua tranh chấp: <b>-15</b> điểm.</li>
 *   <li>Thắng tranh chấp: <b>+1</b> điểm.</li>
 *   <li>Huỷ listing sau khi người mua đã thanh toán: <b>-10</b> điểm.</li>
 *   <li>Điểm không bao giờ xuống dưới sàn 0.</li>
 * </ul>
 *
 * <p>Module: reputation</p>
 *
 * Chủ sở hữu: Người C
 */
@Service
@RequiredArgsConstructor
public class UpdateReputationUseCase {

    private final ReputationRepository reputationRepository;

    /**
     * Lắng nghe sự kiện giao dịch vé hoàn tất để cộng điểm cho cả người bán và người mua.
     *
     * @param event sự kiện do module payment publish sau khi giải ngân xong
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTicketTransactionCompleted(TicketTransactionCompletedEvent event) {
        throw new UnsupportedOperationException(
                "TODO [Người C] Prompt 2: với cả event.getSellerId() và event.getBuyerId(), gọi "
                        + "getOrCreate(userId) rồi cộng 2 điểm (chặn trần 100.00), tăng "
                        + "totalTransactions và successfulTransactions, cộng event.getSalePrice() vào "
                        + "totalGmv của người bán, đặt lastTransactionAt = event.getOccurredAt(), lưu qua "
                        + "reputationRepository.save; bắt OptimisticLockingFailureException để thử lại "
                        + "vì nhiều event có thể cùng ghi một bản ghi.");
    }

    /**
     * Lắng nghe sự kiện tranh chấp được phân xử để cộng/trừ điểm hai bên.
     *
     * @param event sự kiện do module dispute publish sau khi admin ra quyết định
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDisputeResolved(DisputeResolvedEvent event) {
        throw new UnsupportedOperationException(
                "TODO [Người C] Prompt 2: đọc event.getOutcome(); với BUYER_WINS thì người bán "
                        + "-15 điểm và tăng disputesLost, người mua +1 điểm và tăng disputesWon; với "
                        + "SELLER_WINS thì đảo lại; với WITHDRAWN thì không đổi điểm mà chỉ tăng "
                        + "disputesOpenedAgainst của bên bị khiếu nại. Áp sàn 0.00 và trần 100.00, "
                        + "lưu qua reputationRepository.save.");
    }

    /**
     * Lấy bản ghi điểm của một người dùng, tạo mới nếu chưa có.
     *
     * @param userId id thuần của người dùng
     * @return bản ghi điểm hiện có hoặc bản ghi vừa khởi tạo
     */
    public UserReputationScore getOrCreate(Long userId) {
        throw new UnsupportedOperationException(
                "TODO [Người C] Prompt 2: gọi reputationRepository.findByUserId(userId); nếu chưa có "
                        + "thì tạo UserReputationScore mới với score = new BigDecimal(\"100.00\"), các bộ "
                        + "đếm = 0, totalGmv = BigDecimal.ZERO rồi save và trả về.");
    }

    /**
     * Ghi nhận việc người bán huỷ vé đang rao sau khi người mua đã thanh toán.
     *
     * @param sellerId id thuần của người bán
     */
    public void recordCancelledListing(Long sellerId) {
        throw new UnsupportedOperationException(
                "TODO [Người C] Prompt 2: gọi getOrCreate(sellerId), trừ 10 điểm với sàn 0.00, "
                        + "tăng cancelledListings thêm 1 rồi lưu qua reputationRepository.save.");
    }
}
