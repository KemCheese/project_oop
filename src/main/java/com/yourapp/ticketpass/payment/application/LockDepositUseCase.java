package com.yourapp.ticketpass.payment.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.yourapp.ticketpass.payment.domain.Deposit;
import com.yourapp.ticketpass.payment.domain.DepositRepository;
import com.yourapp.ticketpass.payment.domain.DepositStatus;
import com.yourapp.ticketpass.payment.domain.PaymentGateway;
import com.yourapp.ticketpass.payment.domain.PaymentRequest;
import com.yourapp.ticketpass.payment.domain.PaymentResult;
import com.yourapp.ticketpass.shared.exception.BusinessRuleViolationException;
import com.yourapp.ticketpass.shared.exception.ResourceNotFoundException;
import com.yourapp.ticketpass.ticket.application.TransferTicketService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Use case khoá tiền cọc của người bán.
 *
 * <p><b>Quy tắc đã chốt:</b> tiền cọc bằng <b>50% giá bán</b>, tỷ lệ này đọc từ
 * {@code app.deposit.percent} chứ không hardcode. Cọc là khoản bảo đảm để nếu người bán giao
 * vé không hợp lệ hoặc không chuyển vé, sàn có nguồn tiền bồi thường cho người mua mà không
 * phải chờ đòi lại từ người bán.</p>
 *
 * <p><b>Luồng hai bước:</b> {@link #initDepositPayment} tạo bản ghi {@link Deposit} ở
 * {@code PENDING} và lấy {@code payUrl}; {@link #confirmLocked} xử lý callback/IPN, chuyển bản
 * ghi sang {@code LOCKED} và đẩy vé sang {@code DEPOSIT_LOCKED}.</p>
 *
 * <p><b>Giao dịch:</b> {@code @Transactional} được thêm cho {@link #confirmLocked} để
 * đảm bảo cập nhật trạng thái cọc và trạng thái vé trong module ticket diễn ra nguyên tử.</p>
 *
 * <p><b>Ngoại lệ của nguyên tắc 2 (đã chốt):</b> use case này gọi
 * {@code transferTicketService.markDepositLocked(...)} TRỰC TIẾP và ĐỒNG BỘ trong cùng
 * transaction vì đây là luồng tiền, không được để trạng thái vé lệch với trạng thái cọc.</p>
 *
 * <p>Module: payment</p>
 *
 * <p>Chủ sở hữu: Người B</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LockDepositUseCase {

    /** Cổng truy cập dữ liệu khoản cọc. */
    private final DepositRepository depositRepository;

    /** Cổng thanh toán, được tiêm qua abstraction nên không phụ thuộc nhà cung cấp cụ thể. */
    private final PaymentGateway paymentGateway;

    /** Dịch vụ chuyển trạng thái vé của module ticket; gọi đồng bộ trong cùng transaction. */
    private final TransferTicketService transferTicketService;

    /** Tỷ lệ cọc so với giá bán, tính theo phần trăm; mặc định 50 (đọc từ {@code app.deposit.percent}). */
    @Value("${app.deposit.percent:50}")
    private BigDecimal depositPercent = new BigDecimal("50");

    /**
     * Khởi tạo giao dịch khoá cọc và lấy đường dẫn thanh toán cho người bán.
     *
     * <p><b>Trình tự:</b>
     * (1) Kiểm tra đầu vào và vé chưa có cọc đang hiệu lực.
     * (2) Đọc tỷ lệ cọc từ cấu hình (mặc định 50%) và tính {@code amount = salePrice * depositPercent / 100},
     * làm tròn {@code HALF_UP} tới 2 chữ số thập phân.
     * (3) Tạo/cập nhật {@link Deposit} với status = {@code PENDING} rồi save để lấy id làm referenceId.
     * (4) Dựng {@link PaymentRequest} và gọi {@code paymentGateway.initPayment}.
     * (5) Nếu cổng trả thất bại thì cập nhật status = {@code FAILED}, trả về {@link PaymentResult} có {@code payUrl}.</p>
     *
     * @param ticketId  ID vé cần bảo đảm bằng cọc
     * @param sellerId  ID người bán phải khoá cọc
     * @param salePrice giá bán vé, dùng làm căn cứ tính 50% tiền cọc
     * @return kết quả có {@code payUrl} để chuyển hướng người bán sang cổng thanh toán
     */
    public PaymentResult initDepositPayment(Long ticketId, Long sellerId, BigDecimal salePrice) {
        // (1a) Kiểm tra null / giá âm
        if (ticketId == null || sellerId == null || salePrice == null) {
            throw new BusinessRuleViolationException(
                    "ticketId, sellerId và salePrice không được để trống.");
        }
        if (salePrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessRuleViolationException(
                    "Giá bán vé phải lớn hơn 0, nhận được: " + salePrice);
        }

        // (1b) Kiểm tra vé chưa có cọc đang hiệu lực
        Optional<Deposit> existingOpt = depositRepository.findByTicketId(ticketId);
        Deposit deposit;
        if (existingOpt.isPresent()) {
            Deposit existing = existingOpt.get();
            if (existing.getStatus() != DepositStatus.FAILED) {
                throw new BusinessRuleViolationException(
                        "Vé #" + ticketId + " đã có khoản cọc đang hiệu lực (trạng thái: "
                        + existing.getStatus() + "). Không thể tạo thêm cọc mới.");
            }
            // Tái sử dụng bản ghi FAILED trước đó để tránh trùng lặp 2 bản ghi cho cùng 1 ticketId
            deposit = existing;
        } else {
            deposit = new Deposit();
        }

        // (2) Tính số tiền cọc = salePrice * depositPercent / 100, làm tròn HALF_UP tới 2 chữ số thập phân
        BigDecimal percent = depositPercent != null ? depositPercent : new BigDecimal("50");
        BigDecimal amount = salePrice.multiply(percent)
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);

        // (3) Tạo/Cập nhật Deposit với status = PENDING, depositPercent, amount, gatewayName rồi save
        deposit.setTicketId(ticketId);
        deposit.setSellerId(sellerId);
        deposit.setStatus(DepositStatus.PENDING);
        deposit.setDepositPercent(percent);
        deposit.setAmount(amount);
        deposit.setGatewayName(paymentGateway.gatewayName());
        deposit.setGatewayTransactionId(null);
        deposit.setForfeitReason(null);
        deposit.setLockedAt(null);
        deposit.setRefundedAt(null);
        deposit.setForfeitedAt(null);

        deposit = depositRepository.save(deposit);
        log.info("[LockDeposit] Tạo Deposit id={} PENDING cho vé #{}, amount={}", deposit.getId(), ticketId, amount);

        // (4) Dựng PaymentRequest và gọi paymentGateway.initPayment
        PaymentRequest paymentRequest = new PaymentRequest();
        paymentRequest.setReferenceId(String.valueOf(deposit.getId()));
        paymentRequest.setPayerUserId(sellerId);
        paymentRequest.setAmount(amount);
        paymentRequest.setOrderInfo("Khoa coc ve #" + ticketId);

        PaymentResult paymentResult;
        try {
            paymentResult = paymentGateway.initPayment(paymentRequest);
        } catch (Exception e) {
            log.error("[LockDeposit] Lỗi gọi cổng thanh toán cho depositId={}: {}", deposit.getId(), e.getMessage(), e);
            deposit.setStatus(DepositStatus.FAILED);
            deposit.setForfeitReason("Lỗi kết nối cổng thanh toán: " + e.getMessage());
            depositRepository.save(deposit);
            throw new BusinessRuleViolationException("Không thể kết nối cổng thanh toán: " + e.getMessage());
        }

        // (5) Nếu cổng trả thất bại thì cập nhật status = FAILED
        if (paymentResult == null) {
            paymentResult = new PaymentResult();
            paymentResult.setSuccess(false);
            paymentResult.setMessage("Cổng thanh toán không phản hồi");
        }

        if (!paymentResult.isSuccess()) {
            String reason = paymentResult.getMessage() != null
                    ? paymentResult.getMessage() : "Cổng thanh toán từ chối giao dịch";
            log.warn("[LockDeposit] Cổng thanh toán từ chối cho depositId={}: {}", deposit.getId(), reason);
            deposit.setStatus(DepositStatus.FAILED);
            deposit.setForfeitReason(reason);
            depositRepository.save(deposit);
        }

        return paymentResult;
    }

    /**
     * Xác nhận cọc đã bị khoá thành công, dựa trên callback/IPN của cổng thanh toán.
     *
     * <p><b>Trình tự:</b>
     * (1) Xác thực chữ ký HMAC qua {@code paymentGateway.verifyCallback}, từ chối ngay nếu không hợp lệ.
     * (2) Tra bản ghi Deposit theo referenceId (orderId) trong callback.
     * (3) Nếu bản ghi đã ở LOCKED thì trả về nguyên trạng để bảo đảm idempotent với IPN gửi lặp.
     * (4) Cập nhật status = LOCKED, lockedAt = Instant.now(), gatewayTransactionId rồi save.
     * (5) Gọi {@code transferTicketService.markDepositLocked(ticketId, deposit.getId())} ĐỒNG BỘ
     * trong cùng transaction theo ngoại lệ đã chốt của nguyên tắc 2.</p>
     *
     * @param gatewayTransactionId mã giao dịch của cổng thanh toán
     * @param callbackParams       toàn bộ tham số cổng thanh toán gửi tới, dùng để xác thực chữ ký
     * @return bản ghi cọc sau khi đã chuyển sang trạng thái {@code LOCKED}
     */
    @Transactional
    public Deposit confirmLocked(String gatewayTransactionId, Map<String, String> callbackParams) {
        // (1) Gọi paymentGateway.verifyCallback(callbackParams) và từ chối ngay nếu chữ ký không hợp lệ
        PaymentResult verifyResult = paymentGateway.verifyCallback(callbackParams);
        if (verifyResult == null || !verifyResult.isSuccess()) {
            String msg = verifyResult != null ? verifyResult.getMessage() : "kết quả xác thực rỗng";
            log.warn("[LockDeposit] verifyCallback thất bại: {}", msg);
            throw new BusinessRuleViolationException(
                    "Xác thực callback cổng thanh toán thất bại — từ chối xử lý IPN: " + msg);
        }

        // (2) Tra bản ghi Deposit theo referenceId (orderId) trong callback
        Deposit deposit = null;
        if (callbackParams != null) {
            String orderId = callbackParams.getOrDefault("orderId",
                    callbackParams.getOrDefault("referenceId", null));
            if (orderId != null && !orderId.isBlank()) {
                try {
                    Long depositId = Long.parseLong(orderId.trim());
                    deposit = depositRepository.findById(depositId).orElse(null);
                } catch (NumberFormatException ignored) {
                    log.warn("[LockDeposit] Không parse được orderId='{}' thành depositId", orderId);
                }
            }
        }

        if (deposit == null) {
            String ref = callbackParams != null ? callbackParams.get("orderId") : "null";
            throw new ResourceNotFoundException("Không tìm thấy khoản cọc tương ứng với referenceId=" + ref);
        }

        // (3) Nếu bản ghi đã ở LOCKED thì trả về nguyên trạng để bảo đảm idempotent với IPN gửi lặp
        if (deposit.getStatus() == DepositStatus.LOCKED) {
            log.info("[LockDeposit] Deposit id={} đã ở LOCKED, bỏ qua IPN trùng.", deposit.getId());
            return deposit;
        }

        // Từ chối nếu trạng thái không hợp lệ để chuyển sang LOCKED
        if (deposit.getStatus() != DepositStatus.PENDING) {
            throw new BusinessRuleViolationException(
                    "Khoản cọc #" + deposit.getId() + " đang ở trạng thái " + deposit.getStatus()
                    + ", không thể chuyển sang LOCKED.");
        }

        // (4) Cập nhật status = LOCKED, lockedAt = Instant.now(), gatewayTransactionId rồi save
        deposit.setStatus(DepositStatus.LOCKED);
        deposit.setLockedAt(Instant.now());

        String txId = gatewayTransactionId;
        if (txId == null || txId.isBlank()) {
            txId = verifyResult.getGatewayTransactionId();
        }
        if ((txId == null || txId.isBlank()) && callbackParams != null) {
            txId = callbackParams.get("transId");
        }
        deposit.setGatewayTransactionId(txId);

        deposit = depositRepository.save(deposit);
        log.info("[LockDeposit] Deposit id={} chuyển sang LOCKED, gatewayTxId={}", deposit.getId(), txId);

        // (5) Gọi transferTicketService.markDepositLocked(ticketId, deposit.getId()) ĐỒNG BỘ trong cùng transaction
        transferTicketService.markDepositLocked(deposit.getTicketId(), deposit.getId());

        return deposit;
    }

    /**
     * Dành riêng cho testing cấu hình tỷ lệ cọc tuỳ biến.
     */
    void setDepositPercent(BigDecimal depositPercent) {
        this.depositPercent = depositPercent;
    }
}
