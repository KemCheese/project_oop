package com.yourapp.ticketpass.payment.domain;

import java.math.BigDecimal;
import java.util.Map;

public interface PaymentGateway {

    String gatewayName();

    PaymentResult initPayment(PaymentRequest request);

    PaymentResult verifyCallback(Map<String, String> callbackParams);

    PaymentResult capture(String gatewayTransactionId, BigDecimal amount);

    PaymentResult refund(String gatewayTransactionId, BigDecimal amount, String reason);

    PaymentResult payout(Long recipientUserId, BigDecimal amount, String reference);
}
