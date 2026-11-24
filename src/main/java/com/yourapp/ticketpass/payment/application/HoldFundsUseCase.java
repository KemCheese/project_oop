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
import com.yourapp.ticketpass.reputation.application.ReputationPolicyService;
import com.yourapp.ticketpass.shared.dto.CommissionBreakdown;
import com.yourapp.ticketpass.shared.exception.BusinessRuleViolationException;
import com.yourapp.ticketpass.shared.exception.ResourceNotFoundException;
import com.yourapp.ticketpass.ticket.application.TransferTicketService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class HoldFundsUseCase {

    private final EscrowTransactionRepository escrowTransactionRepository;

    private final PaymentGateway paymentGateway;

    private final CommissionCalculatorService commissionCalculatorService;

    private final TransferTicketService transferTicketService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private ReputationPolicyService reputationPolicyService;

    public PaymentResult initEscrowPayment(Long ticketId, Long buyerId, Long sellerId, BigDecimal salePrice) {
        if (ticketId == null || buyerId == null || sellerId == null || salePrice == null) {
            throw new BusinessRuleViolationException(
                    "ticketId, buyerId, sellerId và salePrice không được để trống.");
        }
        if (salePrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessRuleViolationException(
                    "Giá bán vé phải lớn hơn 0, nhận được: " + salePrice);
        }
        if (buyerId.equals(sellerId)) {
            throw new BusinessRuleViolationException(
                    "Người mua không thể tự mua vé của chính mình (userId=" + buyerId + ").");
        }
        if (reputationPolicyService != null) {
            reputationPolicyService.assertCanTransact(buyerId, salePrice, "mua vé");
        }
        Optional<EscrowTransaction> existingOpt = escrowTransactionRepository.findByTicketId(ticketId);
        EscrowTransaction tx;
        if (existingOpt.isPresent()) {
            EscrowTransaction existing = existingOpt.get();
            if (existing.getStatus() != EscrowStatus.FAILED) {
                if (existing.getStatus() == EscrowStatus.PENDING
                        && existing.getCreatedAt() != null
                        && existing.getCreatedAt().plus(java.time.Duration.ofMinutes(15)).isBefore(Instant.now())) {
                    log.warn("[HoldFunds] Giao dịch ký quỹ cũ #{} đã quá hạn 15 phút chờ thanh toán. Đánh dấu FAILED.", existing.getId());
                    existing.setStatus(EscrowStatus.FAILED);
                    existing.setFailureReason("Quá hạn 15 phút chờ thanh toán (System Timeout).");
                    escrowTransactionRepository.save(existing);
                } else {
                    throw new BusinessRuleViolationException(
                            "Vé #" + ticketId + " đã có giao dịch ký quỹ đang hiệu lực (trạng thái: "
                            + existing.getStatus() + "). Không thể mở thêm escrow mới.");
                }
            }
            tx = new EscrowTransaction();
        } else {
            tx = new EscrowTransaction();
        }

        CommissionBreakdown commission = commissionCalculatorService.calculate(salePrice);

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

    @Transactional
    public EscrowTransaction confirmHeld(String gatewayTransactionId, Map<String, String> callbackParams) {
        PaymentResult verifyResult = paymentGateway.verifyCallback(callbackParams);
        if (verifyResult == null || !verifyResult.isSuccess()) {
            String msg = verifyResult != null ? verifyResult.getMessage() : "kết quả xác thực rỗng";
            log.warn("[HoldFunds] verifyCallback thất bại: {}", msg);
            throw new BusinessRuleViolationException(
                    "Xác thực callback cổng thanh toán thất bại — từ chối xử lý IPN: " + msg);
        }

        EscrowTransaction tx = null;

        if (gatewayTransactionId != null && !gatewayTransactionId.isBlank()) {
            tx = escrowTransactionRepository.findByGatewayTransactionId(gatewayTransactionId).orElse(null);
        }

        if (tx == null && callbackParams != null) {
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

        if (tx.getStatus() == EscrowStatus.HELD) {
            log.info("[HoldFunds] EscrowTransaction id={} đã ở HELD, bỏ qua IPN trùng.", tx.getId());
            return tx;
        }

        if (tx.getStatus() != EscrowStatus.PENDING) {
            throw new BusinessRuleViolationException(
                    "Giao dịch ký quỹ #" + tx.getId() + " đang ở trạng thái " + tx.getStatus()
                    + ", không thể chuyển sang HELD.");
        }

        if (reputationPolicyService != null && tx.getBuyerId() != null && tx.getSalePrice() != null) {
            reputationPolicyService.assertCanTransact(tx.getBuyerId(), tx.getSalePrice(), "mua vé");
        }

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

        transferTicketService.markPaymentEscrowed(tx.getTicketId(), tx.getBuyerId(), tx.getId());

        return tx;
    }
}
