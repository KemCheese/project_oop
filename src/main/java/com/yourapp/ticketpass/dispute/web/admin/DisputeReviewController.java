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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

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
import com.yourapp.ticketpass.payment.domain.DepositRepository;
import com.yourapp.ticketpass.payment.domain.EscrowTransactionRepository;
import com.yourapp.ticketpass.shared.exception.BusinessRuleViolationException;
import com.yourapp.ticketpass.shared.exception.ResourceNotFoundException;
import com.yourapp.ticketpass.shared.storage.FileStorageService;
import com.yourapp.ticketpass.user.application.UserQueryService;
import com.yourapp.ticketpass.user.domain.User;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/admin/disputes")
@RequiredArgsConstructor
public class DisputeReviewController {

    private final ReviewDisputeUseCase reviewDisputeUseCase;

    private final DisputeRepository disputeRepository;

    private final DisputeEvidenceRepository disputeEvidenceRepository;

    private final FileStorageService fileStorageService;

    private final EscrowTransactionRepository escrowTransactionRepository;

    private final DepositRepository depositRepository;

    private final UserQueryService userQueryService;

    private final com.yourapp.ticketpass.ticket.domain.TicketRepository ticketRepository;

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

    @GetMapping("/{id}")
    public String showDetail(@PathVariable Long id, Model model) {
        Dispute dispute = disputeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy khiếu nại với ID: " + id));

        List<DisputeEvidence> evidences = disputeEvidenceRepository.findByDisputeId(id);
        DisputeResponse disputeResponse = mapToResponse(dispute, evidences);

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

        if (dispute.getTicketId() != null) {
            ticketRepository.findById(dispute.getTicketId()).ifPresent(ticket -> model.addAttribute("ticket", ticket));
        }

        model.addAttribute("dispute", disputeResponse);
        model.addAttribute("reviewRequest", new ReviewDisputeRequest());
        return "admin/dispute/detail";
    }

    @PostMapping("/{id}/buyer-wins")
    public String resolveBuyerWins(@PathVariable Long id, @Valid ReviewDisputeRequest request,
                                   Principal principal, RedirectAttributes redirectAttributes) {
        Long adminId = resolveAdminId(principal);
        reviewDisputeUseCase.resolveBuyerWins(id, adminId, request.getResolutionNote());
        redirectAttributes.addFlashAttribute("successMessage",
                "Phán quyết thành công! Đã xử NGƯỜI MUA THẮNG. 100% tiền Escrow đã được hoàn lại và cọc người bán đã bị xử lý theo hợp đồng.");
        return "redirect:/admin/disputes/" + id;
    }

    @PostMapping("/{id}/seller-wins")
    public String resolveSellerWins(@PathVariable Long id, @Valid ReviewDisputeRequest request,
                                    Principal principal, RedirectAttributes redirectAttributes) {
        Long adminId = resolveAdminId(principal);
        reviewDisputeUseCase.resolveSellerWins(id, adminId, request.getResolutionNote());
        redirectAttributes.addFlashAttribute("successMessage",
                "Phán quyết thành công! Đã xử NGƯỜI BÁN THẮNG. Tiền bán vé đã giải ngân và 100% tiền cọc đã được hoàn trả nguyên vẹn.");
        return "redirect:/admin/disputes/" + id;
    }

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
