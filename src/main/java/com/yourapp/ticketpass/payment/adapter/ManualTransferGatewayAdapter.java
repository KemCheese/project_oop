package com.yourapp.ticketpass.payment.adapter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.yourapp.ticketpass.payment.domain.PaymentGateway;
import com.yourapp.ticketpass.payment.domain.PaymentRequest;
import com.yourapp.ticketpass.payment.domain.PaymentResult;
import com.yourapp.ticketpass.shared.config.PaymentGatewayConfig;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ManualTransferGatewayAdapter implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(ManualTransferGatewayAdapter.class);

    private final PaymentGatewayConfig paymentGatewayConfig;

    @Override
    public String gatewayName() {
        return paymentGatewayConfig.getGatewayName();
    }

    @Override
    public PaymentResult initPayment(PaymentRequest request) {
        String referenceId = request.getReferenceId();

        String type = "escrow";
        if (request.getOrderInfo() != null && request.getOrderInfo().toLowerCase().contains("coc")) {
            type = "deposit";
        }

        String payUrl = "/payment/transfer?ref=" + referenceId + "&type=" + type;

        log.info("[ManualTransfer] Tạo giao dịch chuyển khoản thủ công, ref={}, type={}, amount={}",
                referenceId, type, request.getAmount());

        PaymentResult result = new PaymentResult();
        result.setSuccess(true);
        result.setGatewayTransactionId("MT_" + referenceId + "_" + Instant.now().toEpochMilli());
        result.setGatewayStatus("PENDING_TRANSFER");
        result.setPayUrl(payUrl);
        result.setMessage("Vui lòng chuyển khoản theo thông tin trên màn hình.");
        return result;
    }

    @Override
    public PaymentResult verifyCallback(Map<String, String> callbackParams) {
        log.info("[ManualTransfer] verifyCallback: chấp nhận xác nhận chuyển khoản thủ công");
        PaymentResult result = new PaymentResult();
        result.setSuccess(true);
        result.setGatewayTransactionId(
                callbackParams != null ? callbackParams.get("referenceId") : null);
        result.setGatewayStatus("MANUAL_VERIFIED");
        result.setMessage("Xác nhận chuyển khoản thủ công — chờ admin duyệt.");
        return result;
    }

    @Override
    public PaymentResult capture(String gatewayTransactionId, BigDecimal amount) {
        log.info("[ManualTransfer] capture: auto-capture cho chuyển khoản thủ công, transId={}, amount={}",
                gatewayTransactionId, amount);
        PaymentResult result = new PaymentResult();
        result.setSuccess(true);
        result.setGatewayTransactionId(gatewayTransactionId);
        result.setGatewayStatus("CAPTURED");
        result.setMessage("Auto-captured — tiền đã vào tài khoản trung gian.");
        return result;
    }

    @Override
    public PaymentResult refund(String gatewayTransactionId, BigDecimal amount, String reason) {
        String mockTransId = "REFUND_MT_" + gatewayTransactionId + "_" + Instant.now().toEpochMilli();
        log.info("[ManualTransfer][REFUND] transId={} | amount={} VND | reason={} | mockRefundId={}",
                gatewayTransactionId, amount.toPlainString(), reason, mockTransId);

        PaymentResult result = new PaymentResult();
        result.setSuccess(true);
        result.setGatewayTransactionId(mockTransId);
        result.setGatewayStatus("REFUND_PENDING");
        result.setMessage("Yêu cầu hoàn tiền đã ghi nhận — admin sẽ chuyển khoản hoàn lại thủ công.");
        return result;
    }

    @Override
    public PaymentResult payout(Long recipientUserId, BigDecimal amount, String reference) {
        String mockTransId = "PAYOUT_MT_" + reference + "_" + Instant.now().toEpochMilli();
        log.info("[ManualTransfer][PAYOUT] userId={} | amount={} VND | reference={} | mockTransId={}",
                recipientUserId, amount.toPlainString(), reference, mockTransId);

        PaymentResult result = new PaymentResult();
        result.setSuccess(true);
        result.setGatewayTransactionId(mockTransId);
        result.setGatewayStatus("PAYOUT_PENDING");
        result.setMessage("Yêu cầu giải ngân đã ghi nhận — admin sẽ chuyển khoản thủ công.");
        return result;
    }
}
