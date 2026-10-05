package com.yourapp.ticketpass.dispute.web.admin;

import java.math.BigDecimal;
import java.security.Principal;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.yourapp.ticketpass.dispute.application.ReviewDisputeUseCase;
import com.yourapp.ticketpass.dispute.domain.Dispute;
import com.yourapp.ticketpass.dispute.domain.DisputeEvidence;
import com.yourapp.ticketpass.dispute.domain.DisputeEvidenceRepository;
import com.yourapp.ticketpass.dispute.domain.DisputeRepository;
import com.yourapp.ticketpass.dispute.web.dto.DisputeDepositDto;
import com.yourapp.ticketpass.dispute.web.dto.DisputeEscrowDto;
import com.yourapp.ticketpass.dispute.web.dto.DisputeEvidenceResponse;
import com.yourapp.ticketpass.dispute.web.dto.DisputeResponse;
import com.yourapp.ticketpass.dispute.web.dto.ReviewDisputeRequest;
import com.yourapp.ticketpass.payment.domain.Deposit;
import com.yourapp.ticketpass.payment.domain.DepositRepository;
import com.yourapp.ticketpass.payment.domain.EscrowTransaction;
import com.yourapp.ticketpass.payment.domain.EscrowTransactionRepository;
import com.yourapp.ticketpass.shared.exception.BusinessRuleViolationException;
import com.yourapp.ticketpass.shared.exception.ResourceNotFoundException;
import com.yourapp.ticketpass.shared.storage.FileStorageService;
import com.yourapp.ticketpass.user.application.UserQueryService;
import com.yourapp.ticketpass.user.domain.User;

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

    /** Cổng truy cập dữ liệu khiếu nại. */
    private final DisputeRepository disputeRepository;

    /** Cổng truy cập dữ liệu bằng chứng. */
    private final DisputeEvidenceRepository disputeEvidenceRepository;

    /** Cổng lưu trữ tệp để dựng publicUrl cho bằng chứng. */
    private final FileStorageService fileStorageService;

    /** Cổng truy cập giao dịch ký quỹ để admin thấy số tiền đang giữ. */
    private final EscrowTransactionRepository escrowTransactionRepository;

    /** Cổng truy cập khoản cọc để admin thấy số tiền cọc đang khoá. */
    private final DepositRepository depositRepository;

    /** Dịch vụ truy vấn người dùng để tìm adminId từ Principal. */
    private final UserQueryService userQueryService;

    /**
     * Danh sách khiếu nại đang chờ admin xử lý.
     *
     * @param model model để đổ dữ liệu ra view
     * @return tên view {@code "admin/dispute/list"}
     */
    @GetMapping
    public String listPending(Model model) {
        List<Dispute> pendingDisputes = reviewDisputeUseCase.listPendingReview();
        List<DisputeResponse> disputeResponses = pendingDisputes.stream()
                .map(d -> mapToResponse(d, null))
                .toList();

        Instant now = Instant.now();
        long overdueCount = pendingDisputes.stream()
                .filter(d -> d.getSellerResponseDeadline() != null && d.getSellerResponseDeadline().isBefore(now))
                .count();

        model.addAttribute("disputes", disputeResponses);
        model.addAttribute("count", disputeResponses.size());
        model.addAttribute("overdueCount", overdueCount);
        return "admin/dispute/list";
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
        Dispute dispute = disputeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy khiếu nại với ID: " + id));

        List<DisputeEvidence> evidences = disputeEvidenceRepository.findByDisputeId(id);
        DisputeResponse disputeResponse = mapToResponse(dispute, evidences);

        // Nạp tóm tắt thông tin Escrow vào DTO an toàn cho View (tránh rò rỉ JPA Entity)
        var escrowOpt = (dispute.getEscrowTransactionId() != null)
                ? escrowTransactionRepository.findById(dispute.getEscrowTransactionId())
                : escrowTransactionRepository.findByTicketId(dispute.getTicketId());

        escrowOpt.ifPresent(escrow -> {
            BigDecimal displayAmount = (escrow.getAmountHeld() != null && escrow.getAmountHeld().compareTo(BigDecimal.ZERO) > 0)
                    ? escrow.getAmountHeld()
                    : escrow.getSalePrice();
            DisputeEscrowDto escrowDto = DisputeEscrowDto.builder()
                    .id(escrow.getId())
                    .amount(displayAmount)
                    .salePrice(escrow.getSalePrice())
                    .status(escrow.getStatus())
                    .gatewayName(escrow.getGatewayName())
                    .build();
            model.addAttribute("escrow", escrowDto);
        });

        // Nạp tóm tắt thông tin Khoản cọc vào DTO an toàn cho View (tránh rò rỉ JPA Entity)
        var depositOpt = (dispute.getDepositId() != null)
                ? depositRepository.findById(dispute.getDepositId())
                : depositRepository.findByTicketId(dispute.getTicketId());

        depositOpt.ifPresent(deposit -> {
            DisputeDepositDto depositDto = DisputeDepositDto.builder()
                    .id(deposit.getId())
                    .depositAmount(deposit.getAmount())
                    .status(deposit.getStatus())
                    .depositPercent(deposit.getDepositPercent())
                    .build();
            model.addAttribute("deposit", depositDto);
        });

        model.addAttribute("dispute", disputeResponse);
        model.addAttribute("reviewRequest", new ReviewDisputeRequest());
        return "admin/dispute/detail";
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
        Long adminId = resolveAdminId(principal);
        reviewDisputeUseCase.resolveBuyerWins(id, adminId, request.getResolutionNote());
        return "redirect:/admin/disputes/" + id;
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
        Long adminId = resolveAdminId(principal);
        reviewDisputeUseCase.resolveSellerWins(id, adminId, request.getResolutionNote());
        return "redirect:/admin/disputes/" + id;
    }

    /**
     * Chuyển đổi entity {@link Dispute} sang {@link DisputeResponse} DTO.
     */
    private DisputeResponse mapToResponse(Dispute dispute, List<DisputeEvidence> evidences) {
        DisputeResponse resp = new DisputeResponse();
        resp.setId(dispute.getId());
        resp.setTicketId(dispute.getTicketId());
        resp.setBuyerId(dispute.getBuyerId());
        resp.setSellerId(dispute.getSellerId());
        resp.setStatus(dispute.getStatus());
        resp.setReason(dispute.getReason());
        resp.setDescription(dispute.getDescription());
        resp.setFiledAt(dispute.getFiledAt());
        resp.setWindowClosesAt(dispute.getWindowClosesAt());
        resp.setSellerResponseDeadline(dispute.getSellerResponseDeadline());
        resp.setSellerResponse(dispute.getSellerResponse());
        resp.setOutcome(dispute.getOutcome());
        resp.setTotalDeposit(dispute.getTotalDeposit());
        resp.setFounderCut(dispute.getFounderCut());
        resp.setBuyerCompensation(dispute.getBuyerCompensation());

        if (evidences != null && !evidences.isEmpty()) {
            List<DisputeEvidenceResponse> evidenceResponses = evidences.stream().map(e -> {
                DisputeEvidenceResponse er = new DisputeEvidenceResponse();
                er.setId(e.getId());
                er.setEvidenceType(e.getEvidenceType());
                er.setOriginalFilename(e.getOriginalFilename());
                er.setPublicUrl(fileStorageService.buildPublicUrl(e.getStorageKey()));
                er.setNote(e.getNote());
                er.setUploadedAt(e.getUploadedAt());
                return er;
            }).toList();
            resp.setEvidences(evidenceResponses);
        }
        return resp;
    }

    /**
     * Xác định adminId từ {@link Principal}.
     */
    private Long resolveAdminId(Principal principal) {
        if (principal == null || principal.getName() == null) {
            throw new BusinessRuleViolationException("Yêu cầu đăng nhập tài khoản quản trị viên.");
        }
        String name = principal.getName();
        try {
            return Long.parseLong(name);
        } catch (NumberFormatException ignored) {
            return userQueryService.findByEmail(name)
                    .map(User::getId)
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản quản trị viên: " + name));
        }
    }
}
