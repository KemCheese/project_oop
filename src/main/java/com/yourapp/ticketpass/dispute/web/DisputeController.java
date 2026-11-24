package com.yourapp.ticketpass.dispute.web;

import java.security.Principal;

import java.util.List;
import java.util.Objects;

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
import com.yourapp.ticketpass.dispute.domain.Dispute;
import com.yourapp.ticketpass.dispute.domain.DisputeEvidence;
import com.yourapp.ticketpass.dispute.domain.DisputeEvidenceRepository;
import com.yourapp.ticketpass.dispute.domain.DisputeRepository;
import com.yourapp.ticketpass.dispute.web.dto.DisputeEvidenceResponse;
import com.yourapp.ticketpass.dispute.web.dto.DisputeResponse;
import com.yourapp.ticketpass.dispute.web.dto.FileDisputeRequest;
import com.yourapp.ticketpass.dispute.web.dto.RespondToDisputeRequest;
import com.yourapp.ticketpass.shared.exception.BusinessRuleViolationException;
import com.yourapp.ticketpass.shared.exception.ResourceNotFoundException;
import com.yourapp.ticketpass.shared.storage.FileStorageService;
import com.yourapp.ticketpass.user.domain.User;
import com.yourapp.ticketpass.user.domain.UserRole;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.BindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/disputes")
@RequiredArgsConstructor
public class DisputeController {

    private final FileDisputeUseCase fileDisputeUseCase;

    private final RespondToDisputeUseCase respondToDisputeUseCase;

    private final DisputeRepository disputeRepository;

    private final DisputeEvidenceRepository disputeEvidenceRepository;

    private final FileStorageService fileStorageService;

    private final com.yourapp.ticketpass.ticket.domain.TicketRepository ticketRepository;

    private final com.yourapp.ticketpass.user.domain.UserRepository userRepository;

    @GetMapping("/ticket/{ticketId}")
    public String redirectToDisputeByTicket(@PathVariable Long ticketId) {
        return disputeRepository.findByTicketId(ticketId)
                .map(d -> "redirect:/disputes/" + d.getId())
                .orElse("redirect:/disputes/new?ticketId=" + ticketId);
    }

    @GetMapping("/new")
    public String showCreateForm(@RequestParam Long ticketId, Model model, Principal principal) {
        var ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new com.yourapp.ticketpass.shared.exception.ResourceNotFoundException("Ticket", ticketId));

        if (principal != null) {
            Long currentUserId = resolveUserId(principal);
            if (ticket.getBuyerId() == null || !ticket.getBuyerId().equals(currentUserId)) {
                throw new com.yourapp.ticketpass.shared.exception.BusinessRuleViolationException(
                        "Chỉ người mua vé mới có quyền mở khiếu nại.");
            }
        }

        java.time.Instant referenceTime = ticket.getReceivedAt() != null ? ticket.getReceivedAt() : ticket.getTransferredAt();
        if (referenceTime == null) {
            referenceTime = ticket.getUpdatedAt() != null ? ticket.getUpdatedAt() : java.time.Instant.now();
        }
        var policy = fileDisputeUseCase.resolvePolicy(ticket.getTicketType());
        java.time.Instant windowClosesAt = policy.computeWindowClose(referenceTime, ticket.getEventStartAt());
        boolean isGateDispute = ticket.getEventStartAt() != null && java.time.Instant.now().isAfter(ticket.getEventStartAt());

        FileDisputeRequest form = new FileDisputeRequest();
        form.setTicketId(ticketId);

        model.addAttribute("ticket", ticket);
        model.addAttribute("form", form);
        model.addAttribute("windowClosesAt", windowClosesAt);
        model.addAttribute("isGateDispute", isGateDispute);
        model.addAttribute("reasons", com.yourapp.ticketpass.dispute.domain.DisputeReason.values());
        return "dispute/create";
    }

    @PostMapping
    public String fileDispute(@Valid FileDisputeRequest request,
                              BindingResult bindingResult,
                              @RequestParam(required = false) MultipartFile[] evidences,
                              RedirectAttributes redirectAttributes,
                              Principal principal) {
        if (bindingResult != null && bindingResult.hasErrors()) {
            String errorMsg = bindingResult.getAllErrors().get(0).getDefaultMessage();
            if (redirectAttributes != null) {
                redirectAttributes.addFlashAttribute("errorMessage", errorMsg);
            }
            return "redirect:/disputes/new?ticketId=" + request.getTicketId();
        }

        try {
            Long buyerId = resolveUserId(principal);
            java.util.List<FileDisputeUseCase.DisputeEvidenceAttachment> attachments = new java.util.ArrayList<>();
            if (evidences != null) {
                for (MultipartFile file : evidences) {
                    if (file != null && !file.isEmpty()) {
                        try {
                            attachments.add(new FileDisputeUseCase.DisputeEvidenceAttachment(
                                    fileDisputeUseCase.inferEvidenceType(file.getContentType()),
                                    file.getOriginalFilename(),
                                    file.getContentType(),
                                    file.getSize(),
                                    file.getInputStream()
                            ));
                        } catch (java.io.IOException e) {
                            if (redirectAttributes != null) {
                                redirectAttributes.addFlashAttribute("errorMessage", "Không thể đọc tệp bằng chứng: " + file.getOriginalFilename());
                            }
                            return "redirect:/disputes/new?ticketId=" + request.getTicketId();
                        }
                    }
                }
            }

            var dispute = fileDisputeUseCase.fileDispute(request.getTicketId(), buyerId,
                    request.getReason(), request.getDescription(), attachments);
            if (redirectAttributes != null) {
                redirectAttributes.addFlashAttribute("successMessage", "Khiếu nại đã được gửi thành công.");
            }
            return "redirect:/disputes/" + dispute.getId();
        } catch (com.yourapp.ticketpass.shared.exception.BusinessRuleViolationException e) {
            if (redirectAttributes != null) {
                redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            }
            return "redirect:/disputes/new?ticketId=" + request.getTicketId();
        } catch (Exception e) {
            if (redirectAttributes != null) {
                redirectAttributes.addFlashAttribute("errorMessage", "Lỗi xử lý khiếu nại: " + e.getMessage());
            }
            return "redirect:/disputes/new?ticketId=" + request.getTicketId();
        }
    }

    public String fileDispute(FileDisputeRequest request, MultipartFile[] evidences, Principal principal) {
        return fileDispute(request, new org.springframework.validation.BeanPropertyBindingResult(request, "request"), evidences, new org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap(), principal);
    }

    private Long resolveUserId(Principal principal) {
        if (principal == null) {
            throw new com.yourapp.ticketpass.shared.exception.BusinessRuleViolationException("Yêu cầu đăng nhập.");
        }
        String email = principal.getName();
        var user = userRepository.findByEmail(email)
                .orElseThrow(() -> new com.yourapp.ticketpass.shared.exception.ResourceNotFoundException("User", email));
        return user.getId();
    }

    @GetMapping("/{id}")
    public String showDetail(@PathVariable Long id, Model model, Principal principal) {
        Dispute dispute = disputeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Dispute", id));

        Long currentUserId = null;
        boolean isAdmin = false;
        if (principal != null) {
            String email = principal.getName();
            var userOpt = userRepository.findByEmail(email);
            if (userOpt.isPresent()) {
                User user = userOpt.get();
                currentUserId = user.getId();
                isAdmin = user.getRole() == UserRole.ADMIN;
            }
        }

        boolean isBuyer = currentUserId != null && Objects.equals(dispute.getBuyerId(), currentUserId);
        boolean isSeller = currentUserId != null && Objects.equals(dispute.getSellerId(), currentUserId);

        if (!isBuyer && !isSeller && !isAdmin) {
            throw new BusinessRuleViolationException("Bạn không có quyền truy cập hồ sơ khiếu nại này.");
        }

        List<DisputeEvidence> evidences = disputeEvidenceRepository.findByDisputeId(id);
        DisputeResponse disputeResponse = mapToResponse(dispute, evidences);

        model.addAttribute("dispute", disputeResponse);
        model.addAttribute("isBuyer", isBuyer);
        model.addAttribute("isSeller", isSeller);
        model.addAttribute("isAdmin", isAdmin);
        model.addAttribute("buyerEvidences", disputeResponse.getEvidences());
        return "dispute/detail";
    }

    @PostMapping("/{id}/respond")
    public String respond(@PathVariable Long id,
                          @Valid RespondToDisputeRequest request,
                          BindingResult bindingResult,
                          RedirectAttributes redirectAttributes,
                          Principal principal) {
        if (bindingResult != null && bindingResult.hasErrors()) {
            String errorMsg = bindingResult.getAllErrors().get(0).getDefaultMessage();
            if (redirectAttributes != null) {
                redirectAttributes.addFlashAttribute("errorMessage", errorMsg);
            }
            return "redirect:/disputes/" + id;
        }

        try {
            Long sellerId = resolveUserId(principal);
            respondToDisputeUseCase.submitSellerResponse(id, sellerId, request.getResponse());
            if (redirectAttributes != null) {
                redirectAttributes.addFlashAttribute("successMessage", "Đã gửi bản giải trình thành công.");
            }
        } catch (com.yourapp.ticketpass.shared.exception.BusinessRuleViolationException e) {
            if (redirectAttributes != null) {
                redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            }
        } catch (Exception e) {
            if (redirectAttributes != null) {
                redirectAttributes.addFlashAttribute("errorMessage", "Lỗi xử lý giải trình: " + e.getMessage());
            }
        }
        return "redirect:/disputes/" + id;
    }

    public String respond(Long id, RespondToDisputeRequest request, Principal principal) {
        return respond(id, request, new org.springframework.validation.BeanPropertyBindingResult(request, "request"), new org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap(), principal);
    }

    @PostMapping("/{id}/withdraw")
    public String withdraw(@PathVariable Long id,
                           @RequestParam(required = false, defaultValue = "Người mua tự nguyện rút khiếu nại") String reason,
                           RedirectAttributes redirectAttributes,
                           Principal principal) {
        try {
            Long buyerId = resolveUserId(principal);
            respondToDisputeUseCase.withdrawDispute(id, buyerId, reason);
            if (redirectAttributes != null) {
                redirectAttributes.addFlashAttribute("successMessage", "Đã rút khiếu nại thành công.");
            }
        } catch (com.yourapp.ticketpass.shared.exception.BusinessRuleViolationException e) {
            if (redirectAttributes != null) {
                redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            }
        } catch (Exception e) {
            if (redirectAttributes != null) {
                redirectAttributes.addFlashAttribute("errorMessage", "Lỗi khi rút khiếu nại: " + e.getMessage());
            }
        }
        return "redirect:/disputes/" + id;
    }

    public String withdraw(Long id, String reason, Principal principal) {
        return withdraw(id, reason, new org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap(), principal);
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
}
