package com.yourapp.ticketpass.dispute.web.admin;

import java.security.Principal;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.yourapp.ticketpass.dispute.application.ReviewDisputeUseCase;
import com.yourapp.ticketpass.dispute.web.dto.ReviewDisputeRequest;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Điểm vào HTTP cho admin phân xử tranh chấp.
 *
 * <p><b>Các tuyến đường:</b></p>
 * <pre>
 * GET  /admin/disputes                    -&gt; danh sach cho xu ly (view "admin/dispute/list")
 * GET  /admin/disputes/{id}               -&gt; ho so + bang chung (view "admin/dispute/detail")
 * POST /admin/disputes/{id}/buyer-wins    -&gt; admin xu buyer thang
 * POST /admin/disputes/{id}/seller-wins   -&gt; admin xu seller thang
 * </pre>
 *
 * <p><b>PHÂN QUYỀN: chỉ role {@code ADMIN} được truy cập.</b> Toàn bộ tiền tố đường dẫn
 * {@code /admin/**} được chặn ở {@code shared/config/SecurityConfig}. Đây là ràng buộc bắt buộc:
 * hai tuyến {@code buyer-wins} và {@code seller-wins} trực tiếp điều khiển dòng tiền — hoàn tiền,
 * tịch thu cọc, giải ngân — nên nếu để lọt người dùng thường thì họ có thể tự phán quyết mình
 * thắng và rút tiền của người khác.</p>
 *
 * <p>Hai tuyến phán quyết được tách thành hai đường dẫn riêng thay vì một đường dẫn nhận tham số
 * kết quả, để hai hành động có hệ quả tiền tệ trái ngược không thể bị nhầm do một trường dữ liệu
 * gửi sai.</p>
 *
 * <p>Module: dispute</p>
 *
 * <p>Chủ sở hữu: Người B</p>
 */
@Controller
@RequestMapping("/admin/disputes")
@RequiredArgsConstructor
public class DisputeReviewController {

    /** Use case phân xử tranh chấp. */
    private final ReviewDisputeUseCase reviewDisputeUseCase;

    /**
     * Danh sách khiếu nại đang chờ admin xử lý.
     *
     * @param model model để đổ dữ liệu ra view
     * @return tên view {@code "admin/dispute/list"}
     */
    @GetMapping
    public String listPending(Model model) {
        throw new UnsupportedOperationException("TODO [Người B] Prompt 2: gọi "
                + "reviewDisputeUseCase.listPendingReview(), map từng Dispute sang DisputeResponse "
                + "(chưa cần nạp bằng chứng ở trang danh sách), đổ vào model kèm số lượng hồ sơ "
                + "và cảnh báo các hồ sơ sắp tới hạn; trả về tên view \"admin/dispute/list\".");
    }

    /**
     * Hồ sơ chi tiết một khiếu nại kèm toàn bộ bằng chứng của hai bên.
     *
     * @param id    ID khiếu nại
     * @param model model để đổ dữ liệu ra view
     * @return tên view {@code "admin/dispute/detail"}
     */
    @GetMapping("/{id}")
    public String showDetail(@PathVariable Long id, Model model) {
        throw new UnsupportedOperationException("TODO [Người B] Prompt 2: tra Dispute theo id, "
                + "nạp toàn bộ bằng chứng qua DisputeEvidenceRepository.findByDisputeId và dựng publicUrl "
                + "bằng FileStorageService.buildPublicUrl, nạp thêm thông tin vé, giao dịch ký quỹ và khoản cọc "
                + "để admin thấy rõ số tiền sẽ bị tác động bởi mỗi nhánh phán quyết, "
                + "đổ DisputeResponse và ReviewDisputeRequest rỗng vào model; "
                + "trả về tên view \"admin/dispute/detail\".");
    }

    /**
     * Admin phán quyết người mua thắng: hoàn 100% tiền ký quỹ và tịch thu cọc người bán.
     *
     * @param id        ID khiếu nại
     * @param request   ghi chú phán quyết
     * @param principal admin đang đăng nhập
     * @return chuyển hướng về trang hồ sơ khiếu nại
     */
    @PostMapping("/{id}/buyer-wins")
    public String resolveBuyerWins(@PathVariable Long id, @Valid ReviewDisputeRequest request, Principal principal) {
        throw new UnsupportedOperationException("TODO [Người B] Prompt 2: lấy adminId từ principal, "
                + "gọi reviewDisputeUseCase.resolveBuyerWins(id, adminId, request.getResolutionNote()), "
                + "rồi trả về \"redirect:/admin/disputes/\" + id.");
    }

    /**
     * Admin phán quyết người bán thắng: giải ngân cho người bán và hoàn đủ cọc.
     *
     * @param id        ID khiếu nại
     * @param request   ghi chú phán quyết
     * @param principal admin đang đăng nhập
     * @return chuyển hướng về trang hồ sơ khiếu nại
     */
    @PostMapping("/{id}/seller-wins")
    public String resolveSellerWins(@PathVariable Long id, @Valid ReviewDisputeRequest request, Principal principal) {
        throw new UnsupportedOperationException("TODO [Người B] Prompt 2: lấy adminId từ principal, "
                + "gọi reviewDisputeUseCase.resolveSellerWins(id, adminId, request.getResolutionNote()), "
                + "rồi trả về \"redirect:/admin/disputes/\" + id.");
    }
}
