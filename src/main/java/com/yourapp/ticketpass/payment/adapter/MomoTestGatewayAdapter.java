package com.yourapp.ticketpass.payment.adapter;

import java.math.BigDecimal;
import java.util.Map;

import com.yourapp.ticketpass.payment.domain.PaymentGateway;
import com.yourapp.ticketpass.payment.domain.PaymentRequest;
import com.yourapp.ticketpass.payment.domain.PaymentResult;

/**
 * Adapter nối sàn với cổng thanh toán MoMo ở môi trường thử nghiệm (MoMo Test Environment).
 *
 * <p><b>LƯU Ý — ĐÃ BỊ VÔ HIỆU HÓA:</b> Hệ thống đã chuyển đổi sang cơ chế chuyển khoản QR
 * trung gian thủ công qua {@link ManualTransferGatewayAdapter}. Lớp này không còn được gắn
 * {@code @Component} và các phương thức ném {@link UnsupportedOperationException}.
 * Giữ lại làm tài liệu tham khảo kiến trúc Adapter pattern khi mở rộng tích hợp cổng API tự động sau này.</p>
 *
 * <p>Module: payment</p>
 * <p>Chủ sở hữu: Người B</p>
 */
@Deprecated
public class MomoTestGatewayAdapter implements PaymentGateway {

    @Override
    public String gatewayName() {
        return "MOMO_TEST";
    }

    @Override
    public PaymentResult initPayment(PaymentRequest request) {
        throw new UnsupportedOperationException("Cổng MoMo đã bị vô hiệu hóa, hệ thống đang dùng ManualTransferGatewayAdapter.");
    }

    @Override
    public PaymentResult verifyCallback(Map<String, String> callbackParams) {
        throw new UnsupportedOperationException("Cổng MoMo đã bị vô hiệu hóa, hệ thống đang dùng ManualTransferGatewayAdapter.");
    }

    @Override
    public PaymentResult capture(String gatewayTransactionId, BigDecimal amount) {
        throw new UnsupportedOperationException("Cổng MoMo đã bị vô hiệu hóa, hệ thống đang dùng ManualTransferGatewayAdapter.");
    }

    @Override
    public PaymentResult refund(String gatewayTransactionId, BigDecimal amount, String reason) {
        throw new UnsupportedOperationException("Cổng MoMo đã bị vô hiệu hóa, hệ thống đang dùng ManualTransferGatewayAdapter.");
    }

    @Override
    public PaymentResult payout(Long recipientUserId, BigDecimal amount, String reference) {
        throw new UnsupportedOperationException("Cổng MoMo đã bị vô hiệu hóa, hệ thống đang dùng ManualTransferGatewayAdapter.");
    }
}
