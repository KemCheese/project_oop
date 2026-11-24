package com.yourapp.ticketpass.payment.adapter;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.yourapp.ticketpass.payment.domain.PaymentRequest;
import com.yourapp.ticketpass.payment.domain.PaymentResult;
import com.yourapp.ticketpass.shared.config.PaymentGatewayConfig;

class ManualTransferGatewayAdapterTest {

    private PaymentGatewayConfig config;
    private ManualTransferGatewayAdapter adapter;

    @BeforeEach
    void setUp() {
        config = new PaymentGatewayConfig();
        config.setGatewayName("MANUAL_TRANSFER");
        config.setAccountHolder("TICKETPASS TRUNG GIAN");
        config.setAccountNumber("0123456789");
        config.setBankName("Vietcombank");
        config.setQrImageUrl("/images/qr-payment.png");
        config.setTransferNotePrefix("TICKETPASS");

        adapter = new ManualTransferGatewayAdapter(config);
    }

    @Test
    @DisplayName("gatewayName() trả về đúng tên cấu hình MANUAL_TRANSFER")
    void testGatewayName() {
        assertEquals("MANUAL_TRANSFER", adapter.gatewayName());
    }

    @Test
    @DisplayName("initPayment() tạo payUrl nội bộ trỏ sang /payment/transfer cho escrow")
    void testInitPaymentEscrow() {
        PaymentRequest request = new PaymentRequest();
        request.setReferenceId("100");
        request.setAmount(new BigDecimal("500000"));
        request.setOrderInfo("Thanh toan ky quy ve #1");

        PaymentResult result = adapter.initPayment(request);

        assertTrue(result.isSuccess());
        assertNotNull(result.getPayUrl());
        assertTrue(result.getPayUrl().contains("/payment/transfer?ref=100&type=escrow"));
        assertEquals("PENDING_TRANSFER", result.getGatewayStatus());
    }

    @Test
    @DisplayName("initPayment() tạo payUrl nội bộ với type=deposit khi là giao dịch cọc")
    void testInitPaymentDeposit() {
        PaymentRequest request = new PaymentRequest();
        request.setReferenceId("200");
        request.setAmount(new BigDecimal("250000"));
        request.setOrderInfo("Khoa coc 50% ve #2");

        PaymentResult result = adapter.initPayment(request);

        assertTrue(result.isSuccess());
        assertNotNull(result.getPayUrl());
        assertTrue(result.getPayUrl().contains("/payment/transfer?ref=200&type=deposit"));
    }

    @Test
    @DisplayName("verifyCallback() luôn trả về success=true cho chuyển khoản thủ công")
    void testVerifyCallback() {
        Map<String, String> params = new HashMap<>();
        params.put("referenceId", "100");
        params.put("orderId", "100");

        PaymentResult result = adapter.verifyCallback(params);

        assertTrue(result.isSuccess());
        assertEquals("MANUAL_VERIFIED", result.getGatewayStatus());
        assertEquals("100", result.getGatewayTransactionId());
    }

    @Test
    @DisplayName("capture() trả về success với trạng thái CAPTURED")
    void testCapture() {
        PaymentResult result = adapter.capture("MT_100", new BigDecimal("500000"));
        assertTrue(result.isSuccess());
        assertEquals("MT_100", result.getGatewayTransactionId());
        assertEquals("CAPTURED", result.getGatewayStatus());
    }

    @Test
    @DisplayName("refund() trả về success với trạng thái REFUND_PENDING để admin xử lý thủ công")
    void testRefund() {
        PaymentResult result = adapter.refund("MT_100", new BigDecimal("500000"), "Huy giao dich");
        assertTrue(result.isSuccess());
        assertEquals("REFUND_PENDING", result.getGatewayStatus());
        assertTrue(result.getGatewayTransactionId().startsWith("REFUND_MT_"));
    }

    @Test
    @DisplayName("payout() trả về success với trạng thái PAYOUT_PENDING để admin xử lý thủ công")
    void testPayout() {
        PaymentResult result = adapter.payout(10L, new BigDecimal("450000"), "ESCROW_100");
        assertTrue(result.isSuccess());
        assertEquals("PAYOUT_PENDING", result.getGatewayStatus());
        assertTrue(result.getGatewayTransactionId().startsWith("PAYOUT_MT_"));
    }
}
