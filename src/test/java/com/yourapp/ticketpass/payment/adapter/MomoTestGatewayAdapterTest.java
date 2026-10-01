package com.yourapp.ticketpass.payment.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yourapp.ticketpass.payment.domain.PaymentResult;
import com.yourapp.ticketpass.shared.config.PaymentGatewayConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test cho {@link MomoTestGatewayAdapter}.
 */
class MomoTestGatewayAdapterTest {

    private PaymentGatewayConfig config;
    private ObjectMapper objectMapper;
    private MomoTestGatewayAdapter adapter;

    private static final String SECRET_KEY = "test_secret_key_123";
    private static final String ACCESS_KEY = "test_access_key_123";
    private static final String PARTNER_CODE = "MOMO_TEST";

    @BeforeEach
    void setUp() {
        config = new PaymentGatewayConfig();
        config.setGatewayName("momo-test");
        config.setPartnerCode(PARTNER_CODE);
        config.setAccessKey(ACCESS_KEY);
        config.setSecretKey(SECRET_KEY);
        config.setEndpoint("https://test-payment.momo.vn/v2/gateway/api/create");
        config.setRequestTimeoutSeconds(5);
        config.setReturnUrl("http://localhost:8080/payment/callback");
        config.setIpnUrl("http://localhost:8080/payment/ipn");

        objectMapper = new ObjectMapper();
        adapter = new MomoTestGatewayAdapter(config, objectMapper);
    }

    @Test
    @DisplayName("gatewayName() trả về đúng tên cấu hình")
    void testGatewayName() {
        assertEquals("momo-test", adapter.gatewayName());
    }

    @Test
    @DisplayName("capture() trả về success với trạng thái CAPTURED (auto-capture)")
    void testCapture() {
        PaymentResult result = adapter.capture("MOMO_TRANS_123", new BigDecimal("500000"));
        assertTrue(result.isSuccess());
        assertEquals("MOMO_TRANS_123", result.getGatewayTransactionId());
        assertEquals("CAPTURED", result.getGatewayStatus());
    }

    @Test
    @DisplayName("payout() trả về success mô phỏng cho đồ án")
    void testPayout() {
        PaymentResult result = adapter.payout(10L, new BigDecimal("450000"), "RELEASE_ESCROW_1");
        assertTrue(result.isSuccess());
        assertTrue(result.getGatewayTransactionId().startsWith("MOCK_PAYOUT_"));
        assertEquals("MOCK_PAYOUT", result.getGatewayStatus());
    }

    @Test
    @DisplayName("verifyCallback: thiếu signature trả về thất bại")
    void testVerifyCallback_MissingSignature() {
        Map<String, String> params = new HashMap<>();
        params.put("orderId", "ORDER_123");

        PaymentResult result = adapter.verifyCallback(params);
        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("thiếu trường signature"));
    }

    @Test
    @DisplayName("verifyCallback: chữ ký sai trả về thất bại")
    void testVerifyCallback_InvalidSignature() {
        Map<String, String> params = new HashMap<>();
        params.put("partnerCode", PARTNER_CODE);
        params.put("orderId", "ORDER_123");
        params.put("signature", "invalid_tampered_signature_abc");

        PaymentResult result = adapter.verifyCallback(params);
        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Chữ ký IPN không hợp lệ"));
    }

    @Test
    @DisplayName("verifyCallback: chữ ký đúng và resultCode = 0 -> thành công")
    void testVerifyCallback_ValidSignature_Success() throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("accessKey", ACCESS_KEY);
        params.put("amount", "200000");
        params.put("extraData", "");
        params.put("message", "Thanh toan thanh cong");
        params.put("orderId", "ORDER_123");
        params.put("orderInfo", "Ve xem phim");
        params.put("orderType", "momo_wallet");
        params.put("partnerCode", PARTNER_CODE);
        params.put("payType", "qr");
        params.put("requestId", "REQ_001");
        params.put("responseTime", "1690000000000");
        params.put("resultCode", "0");
        params.put("transId", "TRANS_99999");

        // Dựng raw signature đúng thứ tự
        String rawSignature = "accessKey=" + params.get("accessKey")
                + "&amount=" + params.get("amount")
                + "&extraData=" + params.get("extraData")
                + "&message=" + params.get("message")
                + "&orderId=" + params.get("orderId")
                + "&orderInfo=" + params.get("orderInfo")
                + "&orderType=" + params.get("orderType")
                + "&partnerCode=" + params.get("partnerCode")
                + "&payType=" + params.get("payType")
                + "&requestId=" + params.get("requestId")
                + "&responseTime=" + params.get("responseTime")
                + "&resultCode=" + params.get("resultCode")
                + "&transId=" + params.get("transId");

        String validSig = hmacSha256(rawSignature, SECRET_KEY);
        params.put("signature", validSig);

        PaymentResult result = adapter.verifyCallback(params);
        assertTrue(result.isSuccess());
        assertEquals("TRANS_99999", result.getGatewayTransactionId());
        assertEquals("0", result.getGatewayStatus());
    }

    @Test
    @DisplayName("verifyCallback: chữ ký đúng nhưng resultCode != 0 (thất bại bên MoMo)")
    void testVerifyCallback_ValidSignature_FailedOnGateway() throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("accessKey", ACCESS_KEY);
        params.put("amount", "200000");
        params.put("extraData", "");
        params.put("message", "Giao dịch bị huỷ");
        params.put("orderId", "ORDER_123");
        params.put("orderInfo", "Ve xem phim");
        params.put("orderType", "momo_wallet");
        params.put("partnerCode", PARTNER_CODE);
        params.put("payType", "qr");
        params.put("requestId", "REQ_001");
        params.put("responseTime", "1690000000000");
        params.put("resultCode", "1006"); // Mã lỗi người dùng từ chối
        params.put("transId", "TRANS_99999");

        String rawSignature = "accessKey=" + params.get("accessKey")
                + "&amount=" + params.get("amount")
                + "&extraData=" + params.get("extraData")
                + "&message=" + params.get("message")
                + "&orderId=" + params.get("orderId")
                + "&orderInfo=" + params.get("orderInfo")
                + "&orderType=" + params.get("orderType")
                + "&partnerCode=" + params.get("partnerCode")
                + "&payType=" + params.get("payType")
                + "&requestId=" + params.get("requestId")
                + "&responseTime=" + params.get("responseTime")
                + "&resultCode=" + params.get("resultCode")
                + "&transId=" + params.get("transId");

        String validSig = hmacSha256(rawSignature, SECRET_KEY);
        params.put("signature", validSig);

        PaymentResult result = adapter.verifyCallback(params);
        assertFalse(result.isSuccess());
        assertEquals("1006", result.getGatewayStatus());
    }

    private String hmacSha256(String data, String key) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }
}
