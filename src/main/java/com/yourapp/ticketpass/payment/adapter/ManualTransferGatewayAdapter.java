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

/**
 * Adapter thanh toán qua chuyển khoản ngân hàng thủ công (Manual Transfer).
 *
 * <p><b>Luồng hoạt động:</b> thay vì gọi API cổng thanh toán bên ngoài (MoMo, VNPay...),
 * adapter này tạo một {@code payUrl} nội bộ trỏ tới trang hiển thị mã QR chuyển khoản
 * của tài khoản trung gian. Người dùng quét QR, chuyển khoản qua app ngân hàng, rồi
 * nhấn nút xác nhận trên giao diện. Admin sẽ kiểm tra thủ công sau.</p>
 *
 * <p>Đây là hiện thực của {@link PaymentGateway} theo Adapter pattern: thêm nhà cung cấp
 * mới chỉ cần thêm class tương tự, không sửa use case nào.</p>
 *
 * <p>Module: payment</p>
 *
 * <p>Chủ sở hữu: Người B</p>
 */
@Component
@RequiredArgsConstructor
public class ManualTransferGatewayAdapter implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(ManualTransferGatewayAdapter.class);

    /** Cấu hình chuyển khoản trung gian, nạp từ {@code app.payment.manual-transfer.*}. */
    private final PaymentGatewayConfig paymentGatewayConfig;

    @Override
    public String gatewayName() {
        return paymentGatewayConfig.getGatewayName();
    }

    /**
     * Khởi tạo giao dịch chuyển khoản thủ công.
     *
     * <p>Không gọi API bên ngoài. Chỉ tạo {@code payUrl} nội bộ trỏ tới trang
     * hiển thị QR chuyển khoản kèm thông tin giao dịch (số tiền, nội dung CK).</p>
     *
     * @param request thông tin yêu cầu thanh toán
     * @return kết quả có {@code payUrl} trỏ tới trang QR chuyển khoản nội bộ
     */
    @Override
    public PaymentResult initPayment(PaymentRequest request) {
        String referenceId = request.getReferenceId();

        // Xác định loại giao dịch dựa trên orderInfo
        String type = "escrow";
        if (request.getOrderInfo() != null && request.getOrderInfo().toLowerCase().contains("coc")) {
            type = "deposit";
        }

        // Tạo payUrl nội bộ trỏ tới trang QR chuyển khoản
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

    /**
     * Xác thực callback — luôn trả {@code success = true}.
     *
     * <p>Vì cơ chế chuyển khoản thủ công không có chữ ký HMAC hay IPN tự động,
     * việc xác nhận do admin kiểm tra tay. Khi người dùng nhấn nút "Đã chuyển khoản",
     * hệ thống tin tạm và chuyển trạng thái; admin sẽ duyệt sau.</p>
     */
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

    /**
     * Capture — auto-capture vì tiền đã chuyển thẳng vào tài khoản trung gian.
     */
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

    /**
     * Hoàn tiền — ghi log để admin thực hiện hoàn tiền thủ công.
     */
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

    /**
     * Chi trả (payout) — ghi log để admin thực hiện giải ngân thủ công.
     */
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
