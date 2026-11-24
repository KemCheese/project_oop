package com.yourapp.ticketpass.payment.web;

import java.security.Principal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.yourapp.ticketpass.payment.application.HoldFundsUseCase;
import com.yourapp.ticketpass.payment.application.LockDepositUseCase;
import com.yourapp.ticketpass.payment.domain.Deposit;
import com.yourapp.ticketpass.payment.domain.DepositRepository;
import com.yourapp.ticketpass.payment.domain.DepositStatus;
import com.yourapp.ticketpass.payment.domain.EscrowStatus;
import com.yourapp.ticketpass.payment.domain.EscrowTransaction;
import com.yourapp.ticketpass.payment.domain.EscrowTransactionRepository;
import com.yourapp.ticketpass.payment.domain.PaymentResult;
import com.yourapp.ticketpass.payment.web.dto.InitDepositRequest;
import com.yourapp.ticketpass.payment.web.dto.InitEscrowRequest;
import com.yourapp.ticketpass.payment.web.dto.PaymentInitResponse;
import com.yourapp.ticketpass.reputation.application.ReputationPolicyService;
import com.yourapp.ticketpass.shared.config.PaymentGatewayConfig;
import com.yourapp.ticketpass.shared.dto.ConflictingTicketInfo;
import com.yourapp.ticketpass.shared.exception.BusinessRuleViolationException;
import com.yourapp.ticketpass.shared.exception.ResourceNotFoundException;
import com.yourapp.ticketpass.ticket.application.TicketScheduleService;
import com.yourapp.ticketpass.ticket.domain.Ticket;
import com.yourapp.ticketpass.ticket.domain.TicketRepository;
import com.yourapp.ticketpass.user.domain.User;
import com.yourapp.ticketpass.user.domain.UserRepository;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/payment")
@RequiredArgsConstructor
@Validated
public class PaymentController {

    private final HoldFundsUseCase holdFundsUseCase;
    private final LockDepositUseCase lockDepositUseCase;
    private final UserRepository userRepository;
    private final TicketRepository ticketRepository;
    private final EscrowTransactionRepository escrowTransactionRepository;
    private final DepositRepository depositRepository;
    private final PaymentGatewayConfig paymentGatewayConfig;
    private final TicketScheduleService ticketScheduleService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private ReputationPolicyService reputationPolicyService;

    private Long resolveUserId(Principal principal) {
        String email = principal.getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", email));
        return user.getId();
    }

    @PostMapping("/escrow/init")
    @ResponseBody
    public PaymentInitResponse initEscrow(@Valid @RequestBody InitEscrowRequest request, Principal principal) {
        Long buyerId = resolveUserId(principal);
        Ticket ticket = ticketRepository.findById(request.getTicketId())
                .orElseThrow(() -> new ResourceNotFoundException("Ticket", request.getTicketId()));

        if (!request.isConfirmOverlap() && ticketScheduleService != null) {
            List<ConflictingTicketInfo> conflicts = ticketScheduleService.findConflictingTickets(buyerId, ticket);
            if (conflicts != null && !conflicts.isEmpty()) {
                ConflictingTicketInfo first = conflicts.get(0);
                throw new BusinessRuleViolationException(
                        "Vé này bị trùng khung giờ sử dụng với vé \"" + first.getTitle()
                        + "\" (" + first.getFormattedTimeRange() + "). "
                        + "Gửi confirmOverlap=true để xác nhận tiếp tục mua.");
            }
        }

        PaymentResult result = holdFundsUseCase.initEscrowPayment(
                ticket.getId(), buyerId, ticket.getSellerId(), ticket.getSalePrice());

        PaymentInitResponse response = new PaymentInitResponse();
        response.setSuccess(result.isSuccess());
        response.setPayUrl(result.getPayUrl());
        response.setMessage(result.getMessage());
        return response;
    }

    @PostMapping("/deposit/init")
    @ResponseBody
    public PaymentInitResponse initDeposit(@Valid @RequestBody InitDepositRequest request, Principal principal) {
        Long sellerId = resolveUserId(principal);
        Ticket ticket = ticketRepository.findById(request.getTicketId())
                .orElseThrow(() -> new ResourceNotFoundException("Ticket", request.getTicketId()));

        if (!sellerId.equals(ticket.getSellerId())) {
            throw new BusinessRuleViolationException("Bạn không phải chủ sở hữu của vé này.");
        }

        PaymentResult result = lockDepositUseCase.initDepositPayment(
                ticket.getId(), sellerId, ticket.getSalePrice());

        PaymentInitResponse response = new PaymentInitResponse();
        response.setSuccess(result.isSuccess());
        response.setPayUrl(result.getPayUrl());
        response.setMessage(result.getMessage());
        return response;
    }

    @PostMapping("/escrow/buy")
    public String buyTicket(@RequestParam Long ticketId,
                            @RequestParam(defaultValue = "false") boolean confirmOverlap,
                            Principal principal,
                            RedirectAttributes redirectAttributes) {
        Long buyerId = resolveUserId(principal);
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket", ticketId));

        if (!confirmOverlap && ticketScheduleService != null) {
            List<ConflictingTicketInfo> conflicts = ticketScheduleService.findConflictingTickets(buyerId, ticket);
            if (conflicts != null && !conflicts.isEmpty()) {
                if (redirectAttributes != null) {
                    redirectAttributes.addFlashAttribute("overlapWarning", true);
                    redirectAttributes.addFlashAttribute("overlapConflicts", conflicts);
                    redirectAttributes.addFlashAttribute("pendingPaymentMethod", "VIETQR");
                }
                return "redirect:/tickets/" + ticketId + "?overlapWarning=true&method=vietqr";
            }
        }

        if (reputationPolicyService != null) {
            try {
                reputationPolicyService.assertCanTransact(buyerId, ticket.getSalePrice(), "mua vé");
            } catch (BusinessRuleViolationException ex) {
                if (redirectAttributes != null) {
                    redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
                    return "redirect:/tickets/" + ticketId;
                }
                throw ex;
            }
        }

        Optional<EscrowTransaction> existingOpt = escrowTransactionRepository.findByTicketId(ticket.getId());
        if (existingOpt.isPresent() && existingOpt.get().getStatus() == EscrowStatus.PENDING
                && buyerId.equals(existingOpt.get().getBuyerId())) {
            return "redirect:/payment/transfer?ref=" + existingOpt.get().getId() + "&type=escrow";
        }

        try {
            PaymentResult result = holdFundsUseCase.initEscrowPayment(
                    ticket.getId(), buyerId, ticket.getSellerId(), ticket.getSalePrice());

            if (result.isSuccess() && result.getPayUrl() != null) {
                return "redirect:" + result.getPayUrl();
            } else {
                throw new BusinessRuleViolationException(
                        result.getMessage() != null ? result.getMessage() : "Không thể khởi tạo thanh toán.");
            }
        } catch (BusinessRuleViolationException ex) {
            if (redirectAttributes != null) {
                redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
                return "redirect:/tickets/" + ticketId;
            }
            throw ex;
        }
    }

    public String buyTicket(Long ticketId, Principal principal) {
        return buyTicket(ticketId, false, principal, null);
    }

    @GetMapping("/escrow/buy")
    public String buyTicketGet(@RequestParam Long ticketId, Principal principal) {
        return buyTicket(ticketId, principal);
    }

    @PostMapping("/deposit/lock")
    public String lockDeposit(@RequestParam Long ticketId, Principal principal) {
        Long sellerId = resolveUserId(principal);
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket", ticketId));

        if (!sellerId.equals(ticket.getSellerId())) {
            throw new BusinessRuleViolationException("Bạn không phải chủ sở hữu của vé này.");
        }

        Optional<Deposit> existingOpt = depositRepository.findByTicketId(ticket.getId());
        if (existingOpt.isPresent() && existingOpt.get().getStatus() == DepositStatus.PENDING) {
            return "redirect:/payment/transfer?ref=" + existingOpt.get().getId() + "&type=deposit";
        }

        PaymentResult result = lockDepositUseCase.initDepositPayment(
                ticket.getId(), sellerId, ticket.getSalePrice());

        if (result.isSuccess() && result.getPayUrl() != null) {
            return "redirect:" + result.getPayUrl();
        } else {
            throw new BusinessRuleViolationException(
                    result.getMessage() != null ? result.getMessage() : "Không thể khởi tạo cọc.");
        }
    }

    @GetMapping("/deposit/lock")
    public String lockDepositGet(@RequestParam Long ticketId, Principal principal) {
        return lockDeposit(ticketId, principal);
    }

    @GetMapping("/momo/return")
    public String momoReturnFallback(@RequestParam(required = false) String orderId,
                                     @RequestParam(required = false) String ref,
                                     Principal principal) {
        if (orderId != null && orderId.startsWith("DEMO-ESCROW-")) {
            try {
                Long ticketId = Long.parseLong(orderId.replace("DEMO-ESCROW-", "").trim());
                if (principal != null) {
                    return buyTicket(ticketId, principal);
                }
                return "redirect:/tickets/" + ticketId;
            } catch (Exception ignored) {
            }
        }
        if (ref != null) {
            return "redirect:/payment/transfer?ref=" + ref + "&type=escrow";
        }
        return "redirect:/tickets";
    }

    @GetMapping("/transfer")
    public String showTransferPage(@RequestParam String ref, @RequestParam String type, Model model) {
        try {
            Long refId = Long.parseLong(ref);

            if ("escrow".equals(type)) {
                EscrowTransaction escrow = escrowTransactionRepository.findById(refId)
                        .orElseThrow(() -> new ResourceNotFoundException("EscrowTransaction", refId));
                model.addAttribute("amount", escrow.getSalePrice());
                model.addAttribute("ticketId", escrow.getTicketId());
            } else if ("deposit".equals(type)) {
                Deposit deposit = depositRepository.findById(refId)
                        .orElseThrow(() -> new ResourceNotFoundException("Deposit", refId));
                model.addAttribute("amount", deposit.getAmount());
                model.addAttribute("ticketId", deposit.getTicketId());
            } else {
                model.addAttribute("error", "Loại giao dịch không hợp lệ: " + type);
                return "payment/result";
            }

            model.addAttribute("ref", ref);
            model.addAttribute("type", type);
            model.addAttribute("accountHolder", paymentGatewayConfig.getAccountHolder());
            model.addAttribute("accountNumber", paymentGatewayConfig.getAccountNumber());
            model.addAttribute("bankName", paymentGatewayConfig.getBankName());
            model.addAttribute("qrImageUrl", paymentGatewayConfig.getQrImageUrl());
            model.addAttribute("transferNote",
                    paymentGatewayConfig.getTransferNotePrefix() + " " + ref);

        } catch (NumberFormatException e) {
            model.addAttribute("error", "Mã giao dịch không hợp lệ: " + ref);
            return "payment/result";
        }

        return "payment/transfer";
    }

    @PostMapping("/transfer/confirm")
    public String confirmTransfer(@RequestParam String ref, @RequestParam String type,
                                  Principal principal, RedirectAttributes redirectAttributes, Model model) {
        try {
            Long refId = Long.parseLong(ref);

            Map<String, String> callbackParams = new HashMap<>();
            callbackParams.put("referenceId", ref);
            callbackParams.put("orderId", ref);

            if ("escrow".equals(type)) {
                EscrowTransaction tx = holdFundsUseCase.confirmHeld(null, callbackParams);
                redirectAttributes.addFlashAttribute("successMessage",
                        "Thanh toán thành công! Hệ thống đã ghi nhận tiền ký quỹ Escrow và chuyển vé sang bước tiếp theo.");
                return "redirect:/tickets/" + tx.getTicketId();
            } else if ("deposit".equals(type)) {
                Deposit deposit = lockDepositUseCase.confirmLocked(null, callbackParams);
                redirectAttributes.addFlashAttribute("successMessage",
                        "Khóa cọc 50% thành công! Mã QR check-in đã được mở khóa ngay cho người mua trong suốt thời gian mở cửa sổ khiếu nại.");
                return "redirect:/tickets/" + deposit.getTicketId();
            } else {
                model.addAttribute("error", "Loại giao dịch không hợp lệ: " + type);
            }

        } catch (Exception e) {
            model.addAttribute("error", "Lỗi xác nhận chuyển khoản: " + e.getMessage());
        }

        return "payment/result";
    }

    @PostMapping("/transfer/fail")
    public String failTransfer(@RequestParam String ref, @RequestParam String type, Model model) {
        try {
            Long refId = Long.parseLong(ref);
            Long ticketId = null;

            if ("escrow".equals(type)) {
                Optional<EscrowTransaction> escrowOpt = escrowTransactionRepository.findById(refId);
                if (escrowOpt.isPresent()) {
                    EscrowTransaction tx = escrowOpt.get();
                    if (tx.getStatus() == EscrowStatus.PENDING) {
                        tx.setStatus(EscrowStatus.FAILED);
                        tx.setFailureReason("Người dùng bấm xác nhận thanh toán thất bại.");
                        escrowTransactionRepository.save(tx);
                    }
                    ticketId = tx.getTicketId();
                }
            } else if ("deposit".equals(type)) {
                Optional<Deposit> depositOpt = depositRepository.findById(refId);
                if (depositOpt.isPresent()) {
                    Deposit dep = depositOpt.get();
                    if (dep.getStatus() == DepositStatus.PENDING) {
                        dep.setStatus(DepositStatus.FAILED);
                        dep.setForfeitReason("Người bán bấm xác nhận thanh toán cọc thất bại.");
                        depositRepository.save(dep);
                    }
                    ticketId = dep.getTicketId();
                }
            }

            if (ticketId != null) {
                model.addAttribute("ticketId", ticketId);
            }
        } catch (Exception ignored) {
        }

        model.addAttribute("error", "Giao dịch thanh toán không thành công. Bạn có thể quay lại trang vé để thử lại.");
        return "payment/result";
    }

    @PostMapping("/transfer/cancel")
    public String cancelTransfer(@RequestParam String ref, @RequestParam String type) {
        try {
            Long refId = Long.parseLong(ref);
            Long ticketId = null;

            if ("escrow".equals(type)) {
                Optional<EscrowTransaction> escrowOpt = escrowTransactionRepository.findById(refId);
                if (escrowOpt.isPresent()) {
                    ticketId = escrowOpt.get().getTicketId();
                }
            } else if ("deposit".equals(type)) {
                Optional<Deposit> depositOpt = depositRepository.findById(refId);
                if (depositOpt.isPresent()) {
                    ticketId = depositOpt.get().getTicketId();
                }
            }

            if (ticketId != null) {
                return "redirect:/tickets/" + ticketId;
            }
        } catch (NumberFormatException ignored) {
        }

        return "redirect:/tickets";
    }
}
