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

/**
 * Cổng thanh toán ủy quyền hợp nhất (Composite / Delegating Payment Gateway).
 *
 * <p><b>Áp dụng Decorator / Composite Pattern:</b> Lớp này đánh dấu {@link Primary}
 * để Spring Boot tự động ưu tiên nạp vào các Use Case thanh toán ({@code HoldFundsUseCase},
 * {@code ReleaseFundsUseCase}, {@code RefundBuyerUseCase}).</p>
 *
 * <p><b>Cơ chế hoạt động:</b></p>
 * <ul>
 *   <li>Giữ nguyên luồng khởi tạo và xác thực của {@link ManualTransferGatewayAdapter}
 *       để bảo toàn tương thích ngược cho chuyển khoản ngân hàng thủ công.</li>
 *   <li>Khi giải ngân ({@link #payout}) hoặc hoàn tiền ({@link #refund}), tự động ủy quyền
 *       sang {@link WalletPaymentGatewayAdapter} để cộng tiền và ghi nhận biến động số dư
 *       tức thì vào ví điện tử của người dùng.</li>
 * </ul>
 *
 * <p>Module: wallet</p>
 * <p>Chủ sở hữu: Người C (Dev C)</p>
 */
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
        // Tự động hoàn tiền vào ví điện tử của người mua
        PaymentResult walletResult = walletPaymentGatewayAdapter.refund(gatewayTransactionId, amount, reason);
        manualTransferGatewayAdapter.refund(gatewayTransactionId, amount, reason);
        return walletResult;
    }

    @Override
    public PaymentResult payout(Long recipientUserId, BigDecimal amount, String reference) {
        // Tự động giải ngân/hoàn cọc vào ví điện tử của người nhận
        PaymentResult walletResult = walletPaymentGatewayAdapter.payout(recipientUserId, amount, reference);
        manualTransferGatewayAdapter.payout(recipientUserId, amount, reference);
        return walletResult;
    }
}
