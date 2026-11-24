package com.yourapp.ticketpass.payment.adapter;

import java.math.BigDecimal;
import java.util.Map;

import com.yourapp.ticketpass.payment.domain.PaymentGateway;
import com.yourapp.ticketpass.payment.domain.PaymentRequest;
import com.yourapp.ticketpass.payment.domain.PaymentResult;

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
