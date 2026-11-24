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

@Slf4j
@Service
@RequiredArgsConstructor
public class LockDepositUseCase {

    private final DepositRepository depositRepository;

    private final PaymentGateway paymentGateway;

    private final TransferTicketService transferTicketService;

    @Value("${app.deposit.percent:50}")
    private BigDecimal depositPercent = new BigDecimal("50");

    public PaymentResult initDepositPayment(Long ticketId, Long sellerId, BigDecimal salePrice) {
        if (ticketId == null || sellerId == null || salePrice == null) {
            throw new BusinessRuleViolationException(
                    "ticketId, sellerId và salePrice không được để trống.");
        }
        if (salePrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessRuleViolationException(
                    "Giá bán vé phải lớn hơn 0, nhận được: " + salePrice);
        }

        Optional<Deposit> existingOpt = depositRepository.findByTicketId(ticketId);
        Deposit deposit;
        if (existingOpt.isPresent()) {
            Deposit existing = existingOpt.get();
            if (existing.getStatus() != DepositStatus.FAILED) {
                throw new BusinessRuleViolationException(
                        "Vé #" + ticketId + " đã có khoản cọc đang hiệu lực (trạng thái: "
                        + existing.getStatus() + "). Không thể tạo thêm cọc mới.");
            }
            deposit = existing;
        } else {
            deposit = new Deposit();
        }

        BigDecimal percent = depositPercent != null ? depositPercent : new BigDecimal("50");
        BigDecimal amount = salePrice.multiply(percent)
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);

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

    @Transactional
    public Deposit confirmLocked(String gatewayTransactionId, Map<String, String> callbackParams) {
        PaymentResult verifyResult = paymentGateway.verifyCallback(callbackParams);
        if (verifyResult == null || !verifyResult.isSuccess()) {
            String msg = verifyResult != null ? verifyResult.getMessage() : "kết quả xác thực rỗng";
            log.warn("[LockDeposit] verifyCallback thất bại: {}", msg);
            throw new BusinessRuleViolationException(
                    "Xác thực callback cổng thanh toán thất bại — từ chối xử lý IPN: " + msg);
        }

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

        if (deposit.getStatus() == DepositStatus.LOCKED) {
            log.info("[LockDeposit] Deposit id={} đã ở LOCKED, bỏ qua IPN trùng.", deposit.getId());
            return deposit;
        }

        if (deposit.getStatus() != DepositStatus.PENDING) {
            throw new BusinessRuleViolationException(
                    "Khoản cọc #" + deposit.getId() + " đang ở trạng thái " + deposit.getStatus()
                    + ", không thể chuyển sang LOCKED.");
        }

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

        transferTicketService.markDepositLocked(deposit.getTicketId(), deposit.getId());

        return deposit;
    }

    void setDepositPercent(BigDecimal depositPercent) {
        this.depositPercent = depositPercent;
    }
}
