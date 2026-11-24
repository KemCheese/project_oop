package com.yourapp.ticketpass.wallet.adapter;

import java.math.BigDecimal;
import java.util.Map;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import com.yourapp.ticketpass.payment.adapter.ManualTransferGatewayAdapter;
import com.yourapp.ticketpass.payment.domain.PaymentGateway;
import com.yourapp.ticketpass.payment.domain.PaymentRequest;
import com.yourapp.ticketpass.payment.domain.PaymentResult;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Primary
@Component
@RequiredArgsConstructor
public class DelegatingPaymentGateway implements PaymentGateway {

    private final ManualTransferGatewayAdapter manualTransferGatewayAdapter;
    private final WalletPaymentGatewayAdapter walletPaymentGatewayAdapter;

    @Override
    public String gatewayName() {
        return manualTransferGatewayAdapter.gatewayName();
    }

    @Override
    public PaymentResult initPayment(PaymentRequest request) {
        return manualTransferGatewayAdapter.initPayment(request);
    }

    @Override
    public PaymentResult verifyCallback(Map<String, String> callbackParams) {
        return manualTransferGatewayAdapter.verifyCallback(callbackParams);
    }

    @Override
    public PaymentResult capture(String gatewayTransactionId, BigDecimal amount) {
        return manualTransferGatewayAdapter.capture(gatewayTransactionId, amount);
    }

    @Override
    public PaymentResult refund(String gatewayTransactionId, BigDecimal amount, String reason) {
        PaymentResult walletResult = walletPaymentGatewayAdapter.refund(gatewayTransactionId, amount, reason);
        manualTransferGatewayAdapter.refund(gatewayTransactionId, amount, reason);
        return walletResult;
    }

    @Override
    public PaymentResult payout(Long recipientUserId, BigDecimal amount, String reference) {
        PaymentResult walletResult = walletPaymentGatewayAdapter.payout(recipientUserId, amount, reference);
        manualTransferGatewayAdapter.payout(recipientUserId, amount, reference);
        return walletResult;
    }
}
