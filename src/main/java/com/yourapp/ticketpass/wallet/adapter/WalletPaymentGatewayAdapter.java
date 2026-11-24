package com.yourapp.ticketpass.wallet.adapter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import com.yourapp.ticketpass.payment.domain.EscrowTransaction;
import com.yourapp.ticketpass.payment.domain.EscrowTransactionRepository;
import com.yourapp.ticketpass.payment.domain.PaymentGateway;
import com.yourapp.ticketpass.payment.domain.PaymentRequest;
import com.yourapp.ticketpass.payment.domain.PaymentResult;
import com.yourapp.ticketpass.wallet.application.WalletService;
import com.yourapp.ticketpass.wallet.domain.WalletTransactionType;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component("walletPaymentGatewayAdapter")
public class WalletPaymentGatewayAdapter implements PaymentGateway {

    private final WalletService walletService;
    private final EscrowTransactionRepository escrowTransactionRepository;

    public WalletPaymentGatewayAdapter(WalletService walletService,
                                      @Lazy EscrowTransactionRepository escrowTransactionRepository) {
        this.walletService = walletService;
        this.escrowTransactionRepository = escrowTransactionRepository;
    }

    @Override
    public String gatewayName() {
        return "WALLET";
    }

    @Override
    public PaymentResult initPayment(PaymentRequest request) {
        String referenceId = request.getReferenceId();
        log.info("[WalletGateway] Khởi tạo giao dịch ví cho ref={}, amount={}", referenceId, request.getAmount());

        PaymentResult result = new PaymentResult();
        result.setSuccess(true);
        result.setGatewayTransactionId("WALLET_" + referenceId + "_" + Instant.now().toEpochMilli());
        result.setGatewayStatus("PENDING_WALLET");
        result.setPayUrl("/wallet");
        result.setMessage("Vui lòng xác nhận thanh toán bằng ví điện tử TicketPass.");
        return result;
    }

    @Override
    public PaymentResult verifyCallback(Map<String, String> callbackParams) {
        PaymentResult result = new PaymentResult();
        result.setSuccess(true);
        result.setGatewayTransactionId(callbackParams != null ? callbackParams.get("referenceId") : null);
        result.setGatewayStatus("WALLET_VERIFIED");
        result.setMessage("Xác nhận thanh toán qua ví thành công.");
        return result;
    }

    @Override
    public PaymentResult capture(String gatewayTransactionId, BigDecimal amount) {
        PaymentResult result = new PaymentResult();
        result.setSuccess(true);
        result.setGatewayTransactionId(gatewayTransactionId);
        result.setGatewayStatus("CAPTURED");
        result.setMessage("Thanh toán trừ ví đã ghi nhận thành công.");
        return result;
    }

    @Override
    public PaymentResult refund(String gatewayTransactionId, BigDecimal amount, String reason) {
        log.info("[WalletGateway][REFUND] Hoàn tiền về ví: transId={}, amount={}, reason={}",
                gatewayTransactionId, amount, reason);

        Long recipientUserId = null;
        if (gatewayTransactionId != null && !gatewayTransactionId.isBlank()) {
            Optional<EscrowTransaction> escrowOpt = escrowTransactionRepository.findByGatewayTransactionId(gatewayTransactionId);
            if (escrowOpt.isEmpty()) {
                try {
                    Long fallbackEscrowId = Long.parseLong(gatewayTransactionId.trim());
                    escrowOpt = escrowTransactionRepository.findById(fallbackEscrowId);
                } catch (NumberFormatException ignored) {
                }
            }
            if (escrowOpt.isPresent()) {
                recipientUserId = escrowOpt.get().getBuyerId();
            }
        }

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            log.error("[WalletGateway][REFUND] Số tiền hoàn không hợp lệ (amount={}) cho transId={}.", amount, gatewayTransactionId);
            PaymentResult fail = new PaymentResult();
            fail.setSuccess(false);
            fail.setGatewayStatus("REFUND_FAILED");
            fail.setMessage("Số tiền hoàn về ví phải lớn hơn 0.");
            return fail;
        }

        if (recipientUserId == null) {
            log.error("[WalletGateway][REFUND] Không xác định được người nhận cho gatewayTransactionId={} — từ chối xác nhận hoàn tiền rỗng.",
                    gatewayTransactionId);
            PaymentResult fail = new PaymentResult();
            fail.setSuccess(false);
            fail.setGatewayStatus("REFUND_FAILED");
            fail.setMessage("Không tìm thấy giao dịch ký quỹ tương ứng để hoàn tiền vào ví.");
            return fail;
        }

        walletService.credit(recipientUserId, amount, WalletTransactionType.ESCROW_REFUND,
                gatewayTransactionId, "Hoàn tiền ký quỹ: " + reason);

        PaymentResult result = new PaymentResult();
        result.setSuccess(true);
        result.setGatewayTransactionId("REFUND_WALLET_" + Instant.now().toEpochMilli());
        result.setGatewayStatus("REFUNDED");
        result.setMessage("Hoàn tiền vào ví điện tử thành công.");
        return result;
    }

    @Override
    public PaymentResult payout(Long recipientUserId, BigDecimal amount, String reference) {
        log.info("[WalletGateway][PAYOUT] Giải ngân/chi trả vào ví: userId={}, amount={}, ref={}",
                recipientUserId, amount, reference);

        WalletTransactionType type = WalletTransactionType.PAYOUT_RECEIVE;
        String desc = "Nhận tiền giải ngân bán vé / hoàn cọc bảo đảm (" + reference + ")";

        if (reference != null && (reference.contains("REFUND_DEPOSIT") || reference.contains("REFUND-DEPOSIT"))) {
            type = WalletTransactionType.DEPOSIT_REFUND;
            desc = "Hoàn cọc bảo đảm 50% (" + reference + ")";
        } else if (reference != null && (reference.contains("BUYER_COMPENSATION") || reference.contains("FORFEIT-COMPENSATION"))) {
            type = WalletTransactionType.COMPENSATION_RECEIVE;
            desc = "Nhận tiền bồi thường khiếu nại (" + reference + ")";
        }

        walletService.credit(recipientUserId, amount, type, reference, desc);

        PaymentResult result = new PaymentResult();
        result.setSuccess(true);
        result.setGatewayTransactionId("PAYOUT_WALLET_" + reference + "_" + Instant.now().toEpochMilli());
        result.setGatewayStatus("PAID");
        result.setMessage("Tiền đã được tự động cộng vào ví điện tử TicketPass của bạn.");
        return result;
    }
}
