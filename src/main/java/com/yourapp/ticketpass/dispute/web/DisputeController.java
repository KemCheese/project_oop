package com.yourapp.ticketpass.dispute.web;

import java.security.Principal;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import com.yourapp.ticketpass.dispute.application.FileDisputeUseCase;
import com.yourapp.ticketpass.dispute.application.RespondToDisputeUseCase;
import com.yourapp.ticketpass.dispute.web.dto.FileDisputeRequest;
import com.yourapp.ticketpass.dispute.web.dto.RespondToDisputeRequest;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Điểm vào HTTP cho người mua và người bán trong quy trình khiếu nại.
 *
 * <p><b>Các tuyến đường:</b></p>
 * <pre>
 * GET  /disputes/new?ticketId=  -&gt; form mo khieu nai (view "dispute/create")
 * POST /disputes                -&gt; buyer mo khieu nai (@Valid FileDisputeRequest + MultipartFile[] evidences)
 * GET  /disputes/{id}           -&gt; chi tiet khieu nai (view "dispute/detail")
 * POST /disputes/{id}/respond   -&gt; seller phan hoi
 * POST /disputes/{id}/withdraw  -&gt; buyer rut khieu nai
 * </pre>
 *
 * <p><b>Kiểm soát truy cập:</b> mọi tuyến ở đây đều phải xác minh người đăng nhập đúng là một
 * bên của khiếu nại — người mua với các thao tác mở và rút, người bán với thao tác giải trình.
 * ID người dùng luôn lấy từ {@link Principal}, không bao giờ nhận từ dữ liệu gửi lên.</p>
 *
 * <p>Controller chỉ điều phối: xác thực đầu vào, gọi use case, chọn view. Toàn bộ quy tắc nghiệp
 * vụ nằm ở {@code dispute.application}.</p>
 *
 * <p>Module: dispute</p>
 *
 * <p>Chủ sở hữu: Người B</p>
 */
@Controller
@RequestMapping("/disputes")
@RequiredArgsConstructor
public class DisputeController {

    /** Use case mở khiếu nại và đính kèm bằng chứng. */
    private final FileDisputeUseCase fileDisputeUseCase;

    /** Use case người bán giải trình và người mua rút khiếu nại. */
    private final RespondToDisputeUseCase respondToDisputeUseCase;

    /**
     * Hiển thị biểu mẫu mở khiếu nại cho một vé.
     *
     * @param ticketId  ID vé cần khiếu nại
     * @param model     model để đổ dữ liệu ra view
     * @param principal người dùng đang đăng nhập
     * @return tên view {@code "dispute/create"}
     */
    @GetMapping("/new")
    public String showCreateForm(@RequestParam Long ticketId, Model model, Principal principal) {
        throw new UnsupportedOperationException("TODO [Người B] Prompt 2: tra vé theo ticketId, "
                + "xác minh người đăng nhập đúng là người mua của vé, "
                + "dùng fileDisputeUseCase.resolvePolicy(ticket.getTicketType()) để tính và hiển thị "
                + "thời điểm cửa sổ khiếu nại đóng, đổ FileDisputeRequest rỗng cùng danh sách "
                + "DisputeReason.values() vào model; trả về tên view \"dispute/create\".");
    }

    /**
     * Người mua nộp khiếu nại kèm các tệp bằng chứng.
     *
     * @param request   dữ liệu khiếu nại đã được kiểm tra ràng buộc
     * @param evidences các tệp bằng chứng người mua tải lên, có thể rỗng
     * @param principal người dùng đang đăng nhập, dùng để suy ra ID người mua
     * @return chuyển hướng tới trang chi tiết khiếu nại vừa tạo
     */
    @PostMapping
    public String fileDispute(@Valid FileDisputeRequest request, @RequestParam(required = false) MultipartFile[] evidences,
                              Principal principal) {
        throw new UnsupportedOperationException("TODO [Người B] Prompt 2: lấy buyerId từ principal, "
                + "gọi fileDisputeUseCase.fileDispute(request.getTicketId(), buyerId, request.getReason(), "
                + "request.getDescription()); sau đó với từng tệp trong evidences (bỏ qua tệp rỗng) gọi "
                + "fileDisputeUseCase.attachEvidence(dispute.getId(), buyerId, suy luận DisputeEvidenceType "
                + "từ contentType, file.getOriginalFilename(), file.getContentType(), file.getSize(), "
                + "file.getInputStream()) trong try-with-resources; "
                + "trả về \"redirect:/disputes/\" + dispute.getId().");
    }

    /**
     * Xem chi tiết một khiếu nại kèm bằng chứng của cả hai bên.
     *
     * @param id        ID khiếu nại
     * @param model     model để đổ dữ liệu ra view
     * @param principal người dùng đang đăng nhập
     * @return tên view {@code "dispute/detail"}
     */
    @GetMapping("/{id}")
    public String showDetail(@PathVariable Long id, Model model, Principal principal) {
        throw new UnsupportedOperationException("TODO [Người B] Prompt 2: tra Dispute theo id, "
                + "xác minh người đăng nhập là người mua hoặc người bán của khiếu nại (nếu không thì trả 403), "
                + "nạp bằng chứng qua DisputeEvidenceRepository.findByDisputeId và dựng publicUrl bằng "
                + "FileStorageService.buildPublicUrl, map sang DisputeResponse rồi đổ vào model; "
                + "trả về tên view \"dispute/detail\".");
    }

    /**
     * Người bán gửi giải trình cho khiếu nại.
     *
     * @param id        ID khiếu nại
     * @param request   nội dung giải trình
     * @param principal người dùng đang đăng nhập, dùng để suy ra ID người bán
     * @return chuyển hướng về trang chi tiết khiếu nại
     */
    @PostMapping("/{id}/respond")
    public String respond(@PathVariable Long id, @Valid RespondToDisputeRequest request, Principal principal) {
        throw new UnsupportedOperationException("TODO [Người B] Prompt 2: lấy sellerId từ principal, "
                + "gọi respondToDisputeUseCase.submitSellerResponse(id, sellerId, request.getResponse()), "
                + "rồi trả về \"redirect:/disputes/\" + id.");
    }

    /**
     * Người mua rút khiếu nại đã mở.
     *
     * @param id        ID khiếu nại
     * @param reason    lý do rút khiếu nại
     * @param principal người dùng đang đăng nhập, dùng để suy ra ID người mua
     * @return chuyển hướng về trang chi tiết khiếu nại
     */
    @PostMapping("/{id}/withdraw")
    public String withdraw(@PathVariable Long id, @RequestParam String reason, Principal principal) {
        throw new UnsupportedOperationException("TODO [Người B] Prompt 2: lấy buyerId từ principal, "
                + "gọi respondToDisputeUseCase.withdrawDispute(id, buyerId, reason), "
                + "rồi trả về \"redirect:/disputes/\" + id.");
    }
}
