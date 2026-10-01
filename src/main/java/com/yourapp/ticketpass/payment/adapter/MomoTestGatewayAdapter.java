package com.yourapp.ticketpass.payment.adapter;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yourapp.ticketpass.payment.domain.PaymentGateway;
import com.yourapp.ticketpass.payment.domain.PaymentRequest;
import com.yourapp.ticketpass.payment.domain.PaymentResult;
import com.yourapp.ticketpass.shared.config.PaymentGatewayConfig;

import lombok.RequiredArgsConstructor;

/**
 * Adapter nối sàn với cổng thanh toán MoMo ở môi trường thử nghiệm (MoMo Test Environment).
 *
 * <p><b>Vì sao chọn MoMo Test:</b> môi trường này cho phép đăng ký khoá thử nghiệm công khai,
 * KHÔNG cần tài khoản doanh nghiệp và không phát sinh dòng tiền thật — phù hợp với đồ án môn học
 * nhưng vẫn chạy đúng quy trình HTTP, chữ ký và IPN như môi trường thật.</p>
 *
 * <p><b>Cấu hình:</b> toàn bộ {@code endpoint}, {@code partnerCode}, {@code accessKey},
 * {@code secretKey}, {@code returnUrl}, {@code ipnUrl} được nạp từ
 * {@link PaymentGatewayConfig} (thuộc tính {@code app.payment.momo.*} trong
 * {@code application.properties}). TUYỆT ĐỐI KHÔNG hardcode khoá bí mật trong class này.</p>
 *
 * <p>Đây là một hiện thực của {@link PaymentGateway} theo Adapter pattern: thêm nhà cung cấp
 * mới (VNPay, ZaloPay) chỉ cần thêm class tương tự, không sửa use case nào.</p>
 *
 * <p>Module: payment</p>
 *
 * <p>Chủ sở hữu: Người B</p>
 */
@Component
@RequiredArgsConstructor
public class MomoTestGatewayAdapter implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(MomoTestGatewayAdapter.class);

    /** Tên thuật toán HMAC dùng để ký yêu cầu và xác thực callback. */
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    /** requestType cho luồng thanh toán thu ngay (pay-and-capture). */
    private static final String REQUEST_TYPE_PAY = "payWithMethod";

    /** resultCode MoMo trả về khi thao tác thành công. */
    private static final int MOMO_RESULT_CODE_SUCCESS = 0;

    /** Cấu hình cổng thanh toán, nạp từ {@code app.payment.momo.*}. */
    private final PaymentGatewayConfig paymentGatewayConfig;

    /** ObjectMapper dùng để dựng/đọc JSON — Spring tự inject bean mặc định của Boot. */
    private final ObjectMapper objectMapper;

    // ─────────────────────────────────────────────────────────────────────────────
    // PaymentGateway implementation
    // ─────────────────────────────────────────────────────────────────────────────

    @Override
    public String gatewayName() {
        return paymentGatewayConfig.getGatewayName();
    }

    /**
     * Khởi tạo giao dịch MoMo và lấy {@code payUrl} để chuyển hướng người mua/người bán.
     *
     * <p><b>Chuỗi rawSignature (thứ tự alphabetical theo key, theo tài liệu MoMo v2):</b></p>
     * <pre>
     * accessKey=&amp;amount=&amp;extraData=&amp;ipnUrl=&amp;orderId=&amp;orderInfo=
     *   &amp;partnerCode=&amp;redirectUrl=&amp;requestId=&amp;requestType=
     * </pre>
     */
    @Override
    public PaymentResult initPayment(PaymentRequest request) {
        String partnerCode = paymentGatewayConfig.getPartnerCode();
        String accessKey   = paymentGatewayConfig.getAccessKey();
        String secretKey   = paymentGatewayConfig.getSecretKey();

        // requestId: sinh UUID mới mỗi lần; orderId: dùng referenceId nội bộ của sàn
        String requestId   = UUID.randomUUID().toString();
        String orderId     = request.getReferenceId();
        // MoMo nhận amount là số nguyên đơn vị VND
        String amount      = request.getAmount().toBigInteger().toString();
        String orderInfo   = request.getOrderInfo() != null
                ? request.getOrderInfo() : "Thanh toan TicketPass";
        String redirectUrl = request.getReturnUrl() != null
                ? request.getReturnUrl() : paymentGatewayConfig.getReturnUrl();
        String ipnUrl      = request.getIpnUrl() != null
                ? request.getIpnUrl() : paymentGatewayConfig.getIpnUrl();
        String extraData   = request.getExtraData() != null ? request.getExtraData() : "";

        // Dựng rawSignature theo thứ tự alphabetical của key (tài liệu MoMo v2)
        String rawSignature = "accessKey=" + accessKey
                + "&amount=" + amount
                + "&extraData=" + extraData
                + "&ipnUrl=" + ipnUrl
                + "&orderId=" + orderId
                + "&orderInfo=" + orderInfo
                + "&partnerCode=" + partnerCode
                + "&redirectUrl=" + redirectUrl
                + "&requestId=" + requestId
                + "&requestType=" + REQUEST_TYPE_PAY;

        String signature;
        try {
            signature = hmacSha256(rawSignature, secretKey);
        } catch (Exception e) {
            log.error("[MoMo] Lỗi khi ký initPayment cho orderId={}: {}", orderId, e.getMessage(), e);
            return failResult(null, "Lỗi nội bộ khi ký yêu cầu thanh toán");
        }

        // Dựng payload JSON gửi lên MoMo
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("partnerCode", partnerCode);
        payload.put("requestId", requestId);
        payload.put("amount", Long.parseLong(amount));
        payload.put("orderId", orderId);
        payload.put("orderInfo", orderInfo);
        payload.put("redirectUrl", redirectUrl);
        payload.put("ipnUrl", ipnUrl);
        payload.put("requestType", REQUEST_TYPE_PAY);
        payload.put("extraData", extraData);
        payload.put("lang", "vi");
        payload.put("signature", signature);

        try {
            String responseBody = postJson(paymentGatewayConfig.getEndpoint(), payload);
            JsonNode resp = objectMapper.readTree(responseBody);

            int resultCode = resp.path("resultCode").asInt(-1);
            String payUrl  = resp.path("payUrl").asText(null);
            // transId có thể không có trong response create, dùng requestId làm fallback
            String transId = resp.path("transId").asText(requestId);

            PaymentResult result = new PaymentResult();
            result.setSuccess(resultCode == MOMO_RESULT_CODE_SUCCESS);
            result.setGatewayTransactionId(transId);
            result.setGatewayStatus(String.valueOf(resultCode));
            result.setPayUrl(payUrl);
            result.setMessage(resp.path("message").asText(""));
            result.setRawPayload(responseBody);
            return result;

        } catch (Exception e) {
            log.error("[MoMo] Lỗi HTTP khi gọi initPayment cho orderId={}: {}", orderId, e.getMessage(), e);
            return failResult(null, "Lỗi kết nối cổng thanh toán: " + e.getMessage());
        }
    }

    /**
     * Xác thực chữ ký HMAC-SHA256 của IPN/callback từ MoMo.
     *
     * <p><b>Chuỗi rawSignature khi verify (thứ tự alphabetical theo tài liệu MoMo v2 — callback):</b></p>
     * <pre>
     * accessKey=&amp;amount=&amp;extraData=&amp;message=&amp;orderId=&amp;orderInfo=
     *   &amp;orderType=&amp;partnerCode=&amp;payType=&amp;requestId=&amp;responseTime=
     *   &amp;resultCode=&amp;transId=
     * </pre>
     *
     * <p><b>Timing-safe comparison:</b> bắt buộc dùng {@link MessageDigest#isEqual} thay vì
     * {@code String.equals} để chống timing attack — kẻ tấn công không thể đoán số ký tự
     * đúng qua thời gian phản hồi.</p>
     */
    @Override
    public PaymentResult verifyCallback(Map<String, String> callbackParams) {
        if (callbackParams == null || !callbackParams.containsKey("signature")) {
            log.warn("[MoMo] verifyCallback: thiếu tham số signature");
            return failResult(
                    callbackParams != null ? callbackParams.get("transId") : null,
                    "Callback thiếu trường signature");
        }

        String secretKey   = paymentGatewayConfig.getSecretKey();
        String receivedSig = callbackParams.get("signature");

        // Dựng lại rawSignature theo thứ tự alphabetical key của callback MoMo v2
        String rawSignature = "accessKey=" + p(callbackParams, "accessKey")
                + "&amount=" + p(callbackParams, "amount")
                + "&extraData=" + p(callbackParams, "extraData")
                + "&message=" + p(callbackParams, "message")
                + "&orderId=" + p(callbackParams, "orderId")
                + "&orderInfo=" + p(callbackParams, "orderInfo")
                + "&orderType=" + p(callbackParams, "orderType")
                + "&partnerCode=" + p(callbackParams, "partnerCode")
                + "&payType=" + p(callbackParams, "payType")
                + "&requestId=" + p(callbackParams, "requestId")
                + "&responseTime=" + p(callbackParams, "responseTime")
                + "&resultCode=" + p(callbackParams, "resultCode")
                + "&transId=" + p(callbackParams, "transId");

        String computedSig;
        try {
            computedSig = hmacSha256(rawSignature, secretKey);
        } catch (Exception e) {
            log.error("[MoMo] verifyCallback: lỗi khi tính HMAC: {}", e.getMessage(), e);
            return failResult(callbackParams.get("transId"), "Lỗi nội bộ khi xác thực chữ ký");
        }

        // Timing-safe comparison: MessageDigest.isEqual so sánh byte-by-byte ở tốc độ cố định,
        // chống timing attack (kẻ tấn công không thể đoán số ký tự đúng qua thời gian phản hồi)
        boolean signatureValid = MessageDigest.isEqual(
                computedSig.getBytes(StandardCharsets.UTF_8),
                receivedSig.getBytes(StandardCharsets.UTF_8));

        if (!signatureValid) {
            log.warn("[MoMo] verifyCallback: chữ ký KHÔNG hợp lệ cho orderId={}",
                    callbackParams.get("orderId"));
            // KHÔNG tin nội dung callback khi chữ ký sai
            return failResult(null, "Chữ ký IPN không hợp lệ — từ chối xử lý");
        }

        // Chữ ký hợp lệ — map resultCode sang success
        int resultCode = parseIntSafe(callbackParams.get("resultCode"));
        PaymentResult result = new PaymentResult();
        result.setSuccess(resultCode == MOMO_RESULT_CODE_SUCCESS);
        result.setGatewayTransactionId(callbackParams.get("transId"));
        result.setGatewayStatus(callbackParams.get("resultCode"));
        result.setMessage(callbackParams.get("message"));
        result.setRawPayload(callbackParams.toString());
        return result;
    }

    /**
     * Capture một giao dịch đã được cấp quyền trước đó.
     *
     * <p>MoMo Test dùng luồng "payWithMethod" thu tiền ngay trong {@code initPayment},
     * nên không cần bước capture riêng. Method này trả {@code success = true} kèm
     * {@code gatewayTransactionId} được truyền vào — hành vi tương đương auto-capture.</p>
     */
    @Override
    public PaymentResult capture(String gatewayTransactionId, BigDecimal amount) {
        log.info("[MoMo] capture: MoMo Test thu ngay (auto-capture), transId={}, amount={}",
                gatewayTransactionId, amount);
        PaymentResult result = new PaymentResult();
        result.setSuccess(true);
        result.setGatewayTransactionId(gatewayTransactionId);
        result.setGatewayStatus("CAPTURED");
        result.setMessage("Auto-captured trong luong payWithMethod cua MoMo Test");
        return result;
    }

    /**
     * Hoàn tiền một phần hoặc toàn bộ cho một giao dịch MoMo đã thu thành công.
     *
     * <p>Idempotent theo {@code requestId}: mỗi yêu cầu hoàn tiền dùng
     * {@code "REFUND_" + gatewayTransactionId + "_" + amount.toPlainString()}
     * làm {@code requestId} cố định — MoMo Test sẽ trả lại kết quả cũ nếu cùng requestId
     * được gọi lại (IPN gửi lặp sẽ không gây hoàn tiền hai lần).</p>
     */
    @Override
    public PaymentResult refund(String gatewayTransactionId, BigDecimal amount, String reason) {
        String partnerCode    = paymentGatewayConfig.getPartnerCode();
        String accessKey      = paymentGatewayConfig.getAccessKey();
        String secretKey      = paymentGatewayConfig.getSecretKey();
        // Endpoint refund: thay /create → /refund trong URL base
        String refundEndpoint = paymentGatewayConfig.getEndpoint()
                .replace("/create", "/refund");

        // requestId idempotent: mỗi (transId + amount) → requestId cố định
        String requestId   = "REFUND_" + gatewayTransactionId + "_" + amount.toPlainString();
        String orderId     = "REFUND_ORDER_" + gatewayTransactionId;
        String amountStr   = amount.toBigInteger().toString();
        String description = reason != null ? reason : "Hoan tien TicketPass";

        // rawSignature cho refund API MoMo v2 (thứ tự alphabetical)
        String rawSignature = "accessKey=" + accessKey
                + "&amount=" + amountStr
                + "&description=" + description
                + "&orderId=" + orderId
                + "&partnerCode=" + partnerCode
                + "&requestId=" + requestId
                + "&transId=" + gatewayTransactionId;

        String signature;
        try {
            signature = hmacSha256(rawSignature, secretKey);
        } catch (Exception e) {
            log.error("[MoMo] Lỗi ký refund transId={}: {}", gatewayTransactionId, e.getMessage(), e);
            return failResult(gatewayTransactionId, "Lỗi nội bộ khi ký yêu cầu hoàn tiền");
        }

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("partnerCode", partnerCode);
        payload.put("orderId", orderId);
        payload.put("requestId", requestId);
        payload.put("amount", Long.parseLong(amountStr));
        payload.put("transId", gatewayTransactionId);
        payload.put("lang", "vi");
        payload.put("description", description);
        payload.put("signature", signature);

        try {
            String responseBody = postJson(refundEndpoint, payload);
            JsonNode resp = objectMapper.readTree(responseBody);
            int resultCode = resp.path("resultCode").asInt(-1);

            PaymentResult result = new PaymentResult();
            result.setSuccess(resultCode == MOMO_RESULT_CODE_SUCCESS);
            result.setGatewayTransactionId(gatewayTransactionId);
            result.setGatewayStatus(String.valueOf(resultCode));
            result.setMessage(resp.path("message").asText(""));
            result.setRawPayload(responseBody);
            return result;

        } catch (Exception e) {
            log.error("[MoMo] Lỗi HTTP khi gọi refund transId={}: {}", gatewayTransactionId, e.getMessage(), e);
            return failResult(gatewayTransactionId, "Lỗi kết nối khi hoàn tiền: " + e.getMessage());
        }
    }

    /**
     * Mô phỏng chi trả (disbursement) từ tài khoản sàn tới người nhận.
     *
     * <p>MoMo Test Environment không mở API payout cho đối tác thử nghiệm, nên method này
     * ghi nhận lệnh chi vào log nội bộ và trả {@code success = true} kèm mã đối soát mock.
     * Trong môi trường thật, thay method này bằng lời gọi API disbursement thật của MoMo.</p>
     */
    @Override
    public PaymentResult payout(Long recipientUserId, BigDecimal amount, String reference) {
        String mockTransId = "MOCK_PAYOUT_" + reference + "_" + Instant.now().toEpochMilli();

        // Ghi log để đội vận hành đối soát thủ công khi cần
        log.info("[MoMo][MOCK-PAYOUT] userId={} | amount={} VND | reference={} | mockTransId={}",
                recipientUserId, amount.toPlainString(), reference, mockTransId);

        PaymentResult result = new PaymentResult();
        result.setSuccess(true);
        result.setGatewayTransactionId(mockTransId);
        result.setGatewayStatus("MOCK_PAYOUT");
        result.setMessage("Payout ghi nhan noi bo (MoMo Test khong co API payout that)");
        return result;
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Tính HMAC-SHA256 của {@code data} với {@code key}, trả về chuỗi hex lowercase.
     */
    private String hmacSha256(String data, String key) throws Exception {
        Mac mac = Mac.getInstance(HMAC_ALGORITHM);
        SecretKeySpec secretKeySpec = new SecretKeySpec(
                key.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
        mac.init(secretKeySpec);
        byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }

    /**
     * POST JSON lên {@code url} với timeout cấu hình, trả về body phản hồi dạng String.
     * Dùng {@link HttpClient} built-in Java 11+, không cần thêm dependency.
     */
    private String postJson(String url, Object body) throws Exception {
        String json = objectMapper.writeValueAsString(body);
        int timeoutSeconds = paymentGatewayConfig.getRequestTimeoutSeconds();
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(timeoutSeconds))
                .build();
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .header("Content-Type", "application/json; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = client.send(httpRequest,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return response.body();
    }

    /**
     * Lấy giá trị param từ map callback; trả về chuỗi rỗng nếu key vắng mặt
     * để tránh chữ "null" xuất hiện trong chuỗi rawSignature.
     */
    private String p(Map<String, String> params, String key) {
        String val = params.get(key);
        return val != null ? val : "";
    }

    /**
     * Parse int an toàn, trả về -1 nếu chuỗi null, rỗng, hoặc không phải số.
     */
    private int parseIntSafe(String s) {
        if (s == null || s.isBlank()) {
            return -1;
        }
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * Tạo {@link PaymentResult} thất bại với thông điệp lý do, dùng trong mọi luồng lỗi.
     */
    private PaymentResult failResult(String gatewayTransactionId, String message) {
        PaymentResult result = new PaymentResult();
        result.setSuccess(false);
        result.setGatewayTransactionId(gatewayTransactionId);
        result.setGatewayStatus("FAILED");
        result.setMessage(message);
        return result;
    }
}
