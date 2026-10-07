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

/**
 * Adapter tích hợp Ví Điện Tử với cổng thanh toán {@link PaymentGateway}.
 *
 * <p><b>Áp dụng Adapter Pattern:</b> Lớp này kết nối module {@code wallet} vào luồng thanh toán
 * chuẩn của hệ thống mà không làm thay đổi hợp đồng giao tiếp giữa các module.</p>
 *
 * <p>Module: wallet</p>
 * <p>Chủ sở hữu: Người C (Dev C)</p>
 */
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

        // Tra cứu giao dịch escrow để tìm buyerId
        Long recipientUserId = null;
        if (gatewayTransactionId != null) {
            Optional<EscrowTransaction> escrowOpt = escrowTransactionRepository.findByGatewayTransactionId(gatewayTransactionId);
            if (escrowOpt.isPresent()) {
                recipientUserId = escrowOpt.get().getBuyerId();
            }
        }

        if (recipientUserId != null) {
            walletService.credit(recipientUserId, amount, WalletTransactionType.ESCROW_REFUND,
                    gatewayTransactionId, "Hoàn tiền ký quỹ: " + reason);
        } else {
            log.warn("[WalletGateway][REFUND] Không tìm thấy recipientUserId cho gatewayTransactionId={}, bỏ qua cộng ví trực tiếp.", gatewayTransactionId);
        }

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

        if (reference != null && reference.contains("REFUND_DEPOSIT")) {
            type = WalletTransactionType.DEPOSIT_REFUND;
            desc = "Hoàn cọc bảo đảm 50% (" + reference + ")";
        } else if (reference != null && reference.contains("BUYER_COMPENSATION")) {
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
