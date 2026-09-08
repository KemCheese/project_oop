package com.yourapp.ticketpass.payment.adapter;

import java.math.BigDecimal;
import java.util.Map;

import org.springframework.stereotype.Component;

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

    /** Cấu hình cổng thanh toán, nạp từ {@code app.payment.momo.*}. */
    private final PaymentGatewayConfig paymentGatewayConfig;

    @Override
    public String gatewayName() {
        return paymentGatewayConfig.getGatewayName();
    }

    @Override
    public PaymentResult initPayment(PaymentRequest request) {
        throw new UnsupportedOperationException("TODO [Người B] Prompt 2: dựng payload create-payment của MoMo "
                + "(partnerCode, requestId, orderId = referenceId, amount, orderInfo, redirectUrl = returnUrl, "
                + "ipnUrl, extraData, requestType), ký HMAC SHA256 chuỗi rawSignature bằng secretKey, "
                + "POST tới paymentGatewayConfig.getEndpoint() với timeout = requestTimeoutSeconds, "
                + "rồi map response sang PaymentResult có payUrl để chuyển hướng người dùng.");
    }

    @Override
    public PaymentResult verifyCallback(Map<String, String> callbackParams) {
        throw new UnsupportedOperationException("TODO [Người B] Prompt 2: xác thực chữ ký của callback/IPN từ MoMo "
                + "bằng cách dựng lại rawSignature từ callbackParams theo đúng thứ tự tham số MoMo quy định, "
                + "ký HMAC SHA256 với secretKey rồi so sánh với tham số 'signature'; "
                + "chỉ khi khớp mới map resultCode sang PaymentResult.success = true, "
                + "sai chữ ký thì trả success = false và KHÔNG tin nội dung callback.");
    }

    @Override
    public PaymentResult capture(String gatewayTransactionId, BigDecimal amount) {
        throw new UnsupportedOperationException("TODO [Người B] Prompt 2: gọi API confirm/capture của MoMo cho "
                + "giao dịch gatewayTransactionId với số tiền amount (requestType = 'capture'), "
                + "ký HMAC SHA256 với secretKey và map kết quả sang PaymentResult; "
                + "với luồng thanh toán thu ngay của MoMo Test có thể trả về kết quả đã capture sẵn từ initPayment.");
    }

    @Override
    public PaymentResult refund(String gatewayTransactionId, BigDecimal amount, String reason) {
        throw new UnsupportedOperationException("TODO [Người B] Prompt 2: gọi API refund của MoMo cho giao dịch "
                + "gatewayTransactionId với số tiền amount và mô tả reason, ký HMAC SHA256 với secretKey; "
                + "hỗ trợ cả hoàn toàn phần và hoàn một phần, và phải idempotent theo requestId "
                + "để IPN gửi lặp không gây hoàn tiền hai lần.");
    }

    @Override
    public PaymentResult payout(Long recipientUserId, BigDecimal amount, String reference) {
        throw new UnsupportedOperationException("TODO [Người B] Prompt 2: mô phỏng chi trả (disbursement) cho "
                + "recipientUserId số tiền amount với mã đối soát reference; MoMo Test Environment không mở API "
                + "chi trả nên ghi nhận lệnh chi vào sổ nội bộ và trả PaymentResult.success = true "
                + "kèm gatewayTransactionId dạng mock, đồng thời ghi log để đối soát.");
    }
}
