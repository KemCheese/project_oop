package com.yourapp.ticketpass.payment.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.yourapp.ticketpass.payment.domain.EscrowStatus;
import com.yourapp.ticketpass.payment.domain.EscrowTransaction;
import com.yourapp.ticketpass.payment.domain.EscrowTransactionRepository;
import com.yourapp.ticketpass.payment.domain.PaymentGateway;
import com.yourapp.ticketpass.payment.domain.PaymentRequest;
import com.yourapp.ticketpass.payment.domain.PaymentResult;
import com.yourapp.ticketpass.shared.dto.CommissionBreakdown;
import com.yourapp.ticketpass.shared.exception.BusinessRuleViolationException;
import com.yourapp.ticketpass.shared.exception.ResourceNotFoundException;
import com.yourapp.ticketpass.ticket.application.TransferTicketService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Use case giữ tiền của người mua vào ký quỹ: người mua nộp 100% giá bán, sàn giữ hộ
 * cho tới khi giao dịch hoàn tất hoặc tranh chấp được xử lý.
 *
 * <p><b>Luồng hai bước bất đồng bộ với cổng thanh toán:</b></p>
 * <ol>
 *   <li>{@link #initEscrowPayment} tạo bản ghi {@link EscrowTransaction} ở trạng thái
 *       {@code PENDING} và lấy {@code payUrl} để chuyển hướng người mua.</li>
 *   <li>{@link #confirmHeld} được gọi khi cổng thanh toán báo về (IPN), chuyển bản ghi sang
 *       {@code HELD} và đẩy vé sang {@code PAYMENT_ESCROWED}.</li>
 * </ol>
 *
 * <p><b>Giao dịch:</b> {@code @Transactional} được thêm cho {@link #confirmHeld} — bước này
 * ghi cả bản ghi escrow và trạng thái vé nên hai thay đổi phải cùng thành công hoặc cùng
 * thất bại.</p>
 *
 * <p><b>Ngoại lệ của nguyên tắc 2 (đã chốt):</b> use case này gọi
 * {@code transferTicketService.markPaymentEscrowed(...)} TRỰC TIẾP và ĐỒNG BỘ trong cùng
 * transaction, không đi qua event. Lý do: đây là luồng tiền — không thể có tình huống tiền đã
 * bị giữ mà trạng thái vé chưa cập nhật, vì khoảng trống đó cho phép vé được bán cho người
 * khác. Chỉ các side-effect không thuộc luồng tiền (uy tín, thông báo) mới đi qua event.</p>
 *
 * <p><b>Idempotent:</b> cổng thanh toán có thể gửi IPN nhiều lần cho cùng một giao dịch, nên
 * {@link #confirmHeld} phải nhận ra bản ghi đã ở {@code HELD} và trả về nguyên trạng thay vì
 * xử lý lại.</p>
 *
 * <p><b>Snapshot hoa hồng:</b> hoa hồng được tính và lưu ngay khi tạo escrow ở bước PENDING,
 * không phải lúc giải ngân. Điều này bảo đảm nếu bảng phí thay đổi sau giao dịch, số liệu
 * cũ vẫn được ghi nhận chính xác cho mục đích đối soát.</p>
 *
 * <p>Module: payment</p>
 *
 * <p>Chủ sở hữu: Người B</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HoldFundsUseCase {

    /** Cổng truy cập dữ liệu giao dịch ký quỹ. */
    private final EscrowTransactionRepository escrowTransactionRepository;

    /** Cổng thanh toán, được tiêm qua abstraction nên không phụ thuộc nhà cung cấp cụ thể. */
    private final PaymentGateway paymentGateway;

    /** Dịch vụ tính hoa hồng, dùng để lưu snapshot phần tiền ngay khi tạo escrow. */
    private final CommissionCalculatorService commissionCalculatorService;

    /** Dịch vụ chuyển trạng thái vé của module ticket; gọi đồng bộ trong cùng transaction. */
    private final TransferTicketService transferTicketService;

    /**
     * Khởi tạo giao dịch ký quỹ và lấy đường dẫn thanh toán cho người mua.
     *
     * <p><b>Trình tự:</b>
     * (1) Kiểm tra đầu vào và vé chưa có escrow đang hiệu lực.
     * (2) Tính hoa hồng lưu snapshot vào EscrowTransaction.
     * (3) Lưu EscrowTransaction PENDING để lấy id làm referenceId.
     * (4) Gọi paymentGateway.initPayment với referenceId = escrow.id.
     * (5) Nếu cổng thất bại thì đánh dấu FAILED, trả về PaymentResult chứa payUrl.</p>
     *
     * @param ticketId  ID vé được mua
     * @param buyerId   ID người mua
     * @param sellerId  ID người bán
     * @param salePrice giá bán vé, cũng là số tiền người mua phải nộp (100% giá bán)
     * @return kết quả có {@code payUrl} để chuyển hướng người mua sang cổng thanh toán
     */
    public PaymentResult initEscrowPayment(Long ticketId, Long buyerId, Long sellerId, BigDecimal salePrice) {
        // (1a) Kiểm tra null / giá âm
        if (ticketId == null || buyerId == null || sellerId == null || salePrice == null) {
            throw new BusinessRuleViolationException(
                    "ticketId, buyerId, sellerId và salePrice không được để trống.");
        }
        if (salePrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessRuleViolationException(
                    "Giá bán vé phải lớn hơn 0, nhận được: " + salePrice);
        }
        // (1b) Người mua không được tự mua vé của mình
        if (buyerId.equals(sellerId)) {
            throw new BusinessRuleViolationException(
                    "Người mua không thể tự mua vé của chính mình (userId=" + buyerId + ").");
        }
        // (1c) Kiểm tra chưa có escrow đang hiệu lực (PENDING / HELD / ...)
        Optional<EscrowTransaction> existingOpt = escrowTransactionRepository.findByTicketId(ticketId);
        EscrowTransaction tx;
        if (existingOpt.isPresent()) {
            EscrowTransaction existing = existingOpt.get();
            if (existing.getStatus() != EscrowStatus.FAILED) {
                throw new BusinessRuleViolationException(
                        "Vé #" + ticketId + " đã có giao dịch ký quỹ đang hiệu lực (trạng thái: "
                        + existing.getStatus() + "). Không thể mở thêm escrow mới.");
            }
            // Tái sử dụng bản ghi FAILED trước đó để tránh trùng lặp 2 bản ghi cho cùng 1 ticketId
            tx = existing;
        } else {
            tx = new EscrowTransaction();
        }

        // (2) Tính hoa hồng — lưu snapshot tại thời điểm giao dịch
        CommissionBreakdown commission = commissionCalculatorService.calculate(salePrice);

        // (3) Thiết lập EscrowTransaction PENDING, amountHeld = 0, lưu để lấy id làm referenceId
        tx.setTicketId(ticketId);
        tx.setBuyerId(buyerId);
        tx.setSellerId(sellerId);
        tx.setStatus(EscrowStatus.PENDING);
        tx.setSalePrice(salePrice);
        tx.setAmountHeld(BigDecimal.ZERO);
        tx.setBuyerCommission(commission.getBuyerShare());
        tx.setSellerCommission(commission.getSellerShare());
        tx.setTotalCommission(commission.getTotalCommission());
        tx.setTierCode(commission.getTierCode());
        tx.setGatewayName(paymentGateway.gatewayName());
        tx.setGatewayTransactionId(null);
        tx.setFailureReason(null);
        tx.setHeldAt(null);

        tx = escrowTransactionRepository.save(tx);
        log.info("[HoldFunds] Tạo EscrowTransaction id={} PENDING cho vé #{}, amount={}", tx.getId(), ticketId, salePrice);

        // (4) Dựng PaymentRequest và gọi paymentGateway.initPayment
        PaymentRequest paymentRequest = new PaymentRequest();
        paymentRequest.setReferenceId(String.valueOf(tx.getId()));
        paymentRequest.setPayerUserId(buyerId);
        paymentRequest.setAmount(salePrice);
        paymentRequest.setOrderInfo("Thanh toan ky quy ve #" + ticketId);

        PaymentResult paymentResult;
        try {
            paymentResult = paymentGateway.initPayment(paymentRequest);
        } catch (Exception e) {
            log.error("[HoldFunds] Lỗi gọi cổng thanh toán cho escrowId={}: {}", tx.getId(), e.getMessage(), e);
            tx.setStatus(EscrowStatus.FAILED);
            tx.setFailureReason("Lỗi kết nối cổng thanh toán: " + e.getMessage());
            escrowTransactionRepository.save(tx);
            throw new BusinessRuleViolationException("Không thể kết nối cổng thanh toán: " + e.getMessage());
        }

        // (5) Nếu cổng trả thất bại thì cập nhật status = FAILED kèm failureReason
        if (paymentResult == null) {
            paymentResult = new PaymentResult();
            paymentResult.setSuccess(false);
            paymentResult.setMessage("Cổng thanh toán không phản hồi");
        }

        if (!paymentResult.isSuccess()) {
            String reason = paymentResult.getMessage() != null
                    ? paymentResult.getMessage() : "Cổng thanh toán từ chối giao dịch";
            log.warn("[HoldFunds] Cổng thanh toán từ chối cho escrowId={}: {}", tx.getId(), reason);
            tx.setStatus(EscrowStatus.FAILED);
            tx.setFailureReason(reason);
            escrowTransactionRepository.save(tx);
        }

        return paymentResult;
    }

    /**
     * Xác nhận tiền đã được giữ thành công, dựa trên callback/IPN của cổng thanh toán.
     *
     * <p><b>Trình tự:</b>
     * (1) Xác thực chữ ký HMAC của callback, từ chối ngay nếu sai.
     * (2) Tra bản ghi escrow qua gatewayTransactionId hoặc referenceId/orderId trong params.
     * (3) Idempotent: nếu đã HELD thì trả về nguyên trạng.
     * (4) Cập nhật status = HELD, amountHeld = salePrice, heldAt, gatewayTransactionId.
     * (5) Gọi transferTicketService.markPaymentEscrowed() đồng bộ trong cùng transaction.</p>
     *
     * @param gatewayTransactionId mã giao dịch của cổng thanh toán
     * @param callbackParams       toàn bộ tham số cổng thanh toán gửi tới, dùng để xác thực chữ ký
     * @return bản ghi ký quỹ sau khi đã chuyển sang trạng thái {@code HELD}
     */
    @Transactional
    public EscrowTransaction confirmHeld(String gatewayTransactionId, Map<String, String> callbackParams) {
        // (1) Xác thực chữ ký HMAC — từ chối ngay nếu không hợp lệ
        PaymentResult verifyResult = paymentGateway.verifyCallback(callbackParams);
        if (verifyResult == null || !verifyResult.isSuccess()) {
            String msg = verifyResult != null ? verifyResult.getMessage() : "kết quả xác thực rỗng";
            log.warn("[HoldFunds] verifyCallback thất bại: {}", msg);
            throw new BusinessRuleViolationException(
                    "Xác thực callback cổng thanh toán thất bại — từ chối xử lý IPN: " + msg);
        }

        // (2) Tra bản ghi EscrowTransaction
        // Ưu tiên: findByGatewayTransactionId, fallback: parse orderId = escrow.id (referenceId)
        EscrowTransaction tx = null;

        if (gatewayTransactionId != null && !gatewayTransactionId.isBlank()) {
            tx = escrowTransactionRepository.findByGatewayTransactionId(gatewayTransactionId).orElse(null);
        }

        if (tx == null && callbackParams != null) {
            // Callback nhận orderId = referenceId = escrow.id
            String orderId = callbackParams.getOrDefault("orderId",
                    callbackParams.getOrDefault("referenceId", null));
            if (orderId != null && !orderId.isBlank()) {
                try {
                    Long escrowId = Long.parseLong(orderId.trim());
                    tx = escrowTransactionRepository.findById(escrowId).orElse(null);
                } catch (NumberFormatException ignored) {
                    log.warn("[HoldFunds] Không parse được orderId='{}' thành escrowId", orderId);
                }
            }
        }

        if (tx == null) {
            String ref = (gatewayTransactionId != null && !gatewayTransactionId.isBlank())
                    ? "gatewayTransactionId=" + gatewayTransactionId
                    : "callbackParams=" + (callbackParams != null ? callbackParams.get("orderId") : "null");
            throw new ResourceNotFoundException("Không tìm thấy giao dịch ký quỹ ứng với " + ref);
        }

        // (3) Idempotent: nếu đã HELD thì trả về ngay — IPN gửi lặp không gây tác động
        if (tx.getStatus() == EscrowStatus.HELD) {
            log.info("[HoldFunds] EscrowTransaction id={} đã ở HELD, bỏ qua IPN trùng.", tx.getId());
            return tx;
        }

        // Từ chối nếu trạng thái không hợp lệ để chuyển sang HELD
        if (tx.getStatus() != EscrowStatus.PENDING) {
            throw new BusinessRuleViolationException(
                    "Giao dịch ký quỹ #" + tx.getId() + " đang ở trạng thái " + tx.getStatus()
                    + ", không thể chuyển sang HELD.");
        }

        // (4) Cập nhật bản ghi escrow sang HELD
        tx.setStatus(EscrowStatus.HELD);
        tx.setAmountHeld(tx.getSalePrice());
        tx.setHeldAt(Instant.now());

        String txId = gatewayTransactionId;
        if (txId == null || txId.isBlank()) {
            txId = verifyResult.getGatewayTransactionId();
        }
        if ((txId == null || txId.isBlank()) && callbackParams != null) {
            txId = callbackParams.get("transId");
        }
        tx.setGatewayTransactionId(txId);

        tx = escrowTransactionRepository.save(tx);
        log.info("[HoldFunds] EscrowTransaction id={} chuyển sang HELD, gatewayTxId={}", tx.getId(), txId);

        // (5) Đẩy vé sang PAYMENT_ESCROWED — gọi ĐỒNG BỘ trong cùng transaction (ngoại lệ đã chốt)
        transferTicketService.markPaymentEscrowed(tx.getTicketId(), tx.getBuyerId(), tx.getId());

        return tx;
    }
}
