package com.yourapp.ticketpass.payment.web;

import java.security.Principal;
import java.util.Map;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.yourapp.ticketpass.payment.application.HoldFundsUseCase;
import com.yourapp.ticketpass.payment.application.LockDepositUseCase;
import com.yourapp.ticketpass.payment.web.dto.InitDepositRequest;
import com.yourapp.ticketpass.payment.web.dto.InitEscrowRequest;
import com.yourapp.ticketpass.payment.web.dto.PaymentInitResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Điểm vào HTTP cho luồng thanh toán: người mua nộp tiền vào ký quỹ, người bán khoá cọc,
 * và hai đầu mối nhận kết quả từ cổng thanh toán.
 *
 * <p><b>Các tuyến đường:</b></p>
 * <pre>
 * POST /payment/escrow/init      -&gt; nguoi mua bat dau thanh toan vao escrow
 * POST /payment/deposit/init     -&gt; nguoi ban bat dau khoa coc
 * GET  /payment/momo/return      -&gt; nguoi dung quay lai tu MoMo (view "payment/result")
 * POST /payment/momo/ipn         -&gt; IPN server-to-server tu MoMo (@ResponseBody)
 * </pre>
 *
 * <p><b>Phân biệt {@code return} và {@code ipn} — rất quan trọng:</b> tuyến {@code return} chỉ
 * để hiển thị kết quả cho người dùng và KHÔNG được dùng làm căn cứ đổi trạng thái giao dịch,
 * vì trình duyệt có thể không quay về (người dùng đóng tab) hoặc tham số có thể bị sửa. Nguồn
 * sự thật duy nhất là tuyến {@code ipn} — lời gọi server-to-server có chữ ký, được cổng thanh
 * toán gửi lặp lại tới khi nhận được phản hồi thành công.</p>
 *
 * <p>Controller chỉ làm ba việc: lấy danh tính người dùng từ phiên đăng nhập, gọi use case,
 * và chọn view hoặc phản hồi. Mọi quy tắc nghiệp vụ nằm ở {@code payment.application}.</p>
 *
 * <p>Module: payment</p>
 *
 * <p>Chủ sở hữu: Người B</p>
 */
@Controller
@RequestMapping("/payment")
@RequiredArgsConstructor
@Validated
public class PaymentController {

    /** Use case giữ tiền người mua vào ký quỹ. */
    private final HoldFundsUseCase holdFundsUseCase;

    /** Use case khoá cọc của người bán. */
    private final LockDepositUseCase lockDepositUseCase;

    /**
     * Người mua bắt đầu thanh toán 100% giá bán vào ký quỹ.
     *
     * @param request   dữ liệu yêu cầu, gồm ID vé và phương thức thanh toán
     * @param principal người dùng đang đăng nhập, dùng để suy ra ID người mua
     * @return phản hồi có {@code payUrl} để giao diện chuyển hướng người mua
     */
    @PostMapping("/escrow/init")
    @ResponseBody
    public PaymentInitResponse initEscrow(@Valid @RequestBody InitEscrowRequest request, Principal principal) {
        throw new UnsupportedOperationException("TODO [Người B] Prompt 2: lấy buyerId từ principal (KHÔNG nhận từ "
                + "phía người dùng), tra vé theo request.getTicketId() để lấy sellerId và salePrice, "
                + "gọi holdFundsUseCase.initEscrowPayment(ticketId, buyerId, sellerId, salePrice), "
                + "rồi map PaymentResult sang PaymentInitResponse (success, payUrl, referenceId, message).");
    }

    /**
     * Người bán bắt đầu khoá cọc 50% giá bán cho vé của mình.
     *
     * @param request   dữ liệu yêu cầu, gồm ID vé
     * @param principal người dùng đang đăng nhập, dùng để suy ra ID người bán
     * @return phản hồi có {@code payUrl} để giao diện chuyển hướng người bán
     */
    @PostMapping("/deposit/init")
    @ResponseBody
    public PaymentInitResponse initDeposit(@Valid @RequestBody InitDepositRequest request, Principal principal) {
        throw new UnsupportedOperationException("TODO [Người B] Prompt 2: lấy sellerId từ principal, "
                + "kiểm tra người đăng nhập đúng là chủ vé request.getTicketId(), tra salePrice của vé, "
                + "gọi lockDepositUseCase.initDepositPayment(ticketId, sellerId, salePrice), "
                + "rồi map PaymentResult sang PaymentInitResponse.");
    }

    /**
     * Trang người dùng được chuyển về sau khi thao tác trên cổng MoMo.
     *
     * <p>Chỉ hiển thị kết quả, KHÔNG đổi trạng thái giao dịch — việc đó thuộc tuyến IPN.</p>
     *
     * @param params toàn bộ tham số truy vấn MoMo gắn vào URL quay về
     * @param model  model để đổ dữ liệu ra view
     * @return tên view {@code "payment/result"}
     */
    @GetMapping("/momo/return")
    public String momoReturn(@RequestParam Map<String, String> params, Model model) {
        throw new UnsupportedOperationException("TODO [Người B] Prompt 2: đọc referenceId và resultCode từ params, "
                + "tra trạng thái giao dịch hiện tại trong cơ sở dữ liệu để hiển thị, "
                + "đổ EscrowSummaryResponse hoặc thông điệp lỗi vào model, "
                + "TUYỆT ĐỐI KHÔNG đổi trạng thái giao dịch ở đây vì tham số URL không đáng tin; "
                + "trả về tên view \"payment/result\".");
    }

    /**
     * Đầu mối nhận thông báo server-to-server (IPN) từ MoMo — nguồn sự thật của kết quả thanh toán.
     *
     * @param params toàn bộ tham số MoMo gửi tới, gồm cả chữ ký
     * @return chuỗi phản hồi cho MoMo để cổng biết đã nhận thành công và ngừng gửi lại
     */
    @PostMapping("/momo/ipn")
    @ResponseBody
    public String momoIpn(@RequestBody Map<String, String> params) {
        throw new UnsupportedOperationException("TODO [Người B] Prompt 2: phân loại IPN là của escrow hay của cọc "
                + "dựa trên extraData/referenceId, rồi gọi holdFundsUseCase.confirmHeld(...) "
                + "hoặc lockDepositUseCase.confirmLocked(...) — hai method này đã tự xác thực chữ ký "
                + "qua paymentGateway.verifyCallback và đã idempotent với IPN gửi lặp; "
                + "trả về chuỗi phản hồi đúng định dạng MoMo yêu cầu và luôn trả HTTP 200 "
                + "để cổng không gửi lại vô hạn. Cần cho phép tuyến này qua Spring Security "
                + "(không yêu cầu đăng nhập, miễn CSRF) trong shared/config/SecurityConfig.");
    }
}
