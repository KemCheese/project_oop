package com.yourapp.ticketpass.payment.web;

import java.security.Principal;
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

import com.yourapp.ticketpass.payment.application.HoldFundsUseCase;
import com.yourapp.ticketpass.payment.application.LockDepositUseCase;
import com.yourapp.ticketpass.payment.domain.Deposit;
import com.yourapp.ticketpass.payment.domain.DepositRepository;
import com.yourapp.ticketpass.payment.domain.EscrowTransaction;
import com.yourapp.ticketpass.payment.domain.EscrowTransactionRepository;
import com.yourapp.ticketpass.payment.domain.PaymentResult;
import com.yourapp.ticketpass.payment.web.dto.EscrowSummaryResponse;
import com.yourapp.ticketpass.payment.web.dto.InitDepositRequest;
import com.yourapp.ticketpass.payment.web.dto.InitEscrowRequest;
import com.yourapp.ticketpass.payment.web.dto.PaymentInitResponse;
import com.yourapp.ticketpass.shared.exception.BusinessRuleViolationException;
import com.yourapp.ticketpass.shared.exception.ResourceNotFoundException;
import com.yourapp.ticketpass.ticket.domain.Ticket;
import com.yourapp.ticketpass.ticket.domain.TicketRepository;
import com.yourapp.ticketpass.user.domain.User;
import com.yourapp.ticketpass.user.domain.UserRepository;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Điểm vào HTTP cho luồng thanh toán: người mua nộp tiền vào ký quỹ, người bán khoá cọc,
 * và hai đầu mối nhận kết quả từ cổng thanh toán.
 */
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

    @GetMapping("/momo/return")
    public String momoReturn(@RequestParam Map<String, String> params, Model model) {
        String referenceId = params.getOrDefault("orderId", params.get("referenceId"));
        String resultCode = params.get("resultCode");

        try {
            if (referenceId != null && !referenceId.isBlank()) {
                Long refId = Long.parseLong(referenceId);

                Optional<EscrowTransaction> escrowOpt = escrowTransactionRepository.findById(refId);
                if (escrowOpt.isPresent()) {
                    EscrowTransaction escrow = escrowOpt.get();
                    EscrowSummaryResponse summary = new EscrowSummaryResponse();
                    summary.setId(escrow.getId());
                    summary.setTicketId(escrow.getTicketId());
                    summary.setStatus(escrow.getStatus());
                    summary.setSalePrice(escrow.getSalePrice());
                    summary.setBuyerCommission(escrow.getBuyerCommission());
                    summary.setSellerCommission(escrow.getSellerCommission());
                    summary.setTaxWithheld(escrow.getTaxWithheld());
                    summary.setSellerNetPayout(escrow.getSellerNetPayout());
                    summary.setHeldAt(escrow.getHeldAt());
                    summary.setReleasedAt(escrow.getReleasedAt());
                    model.addAttribute("escrow", summary);
                } else {
                    Optional<Deposit> depositOpt = depositRepository.findById(refId);
                    if (depositOpt.isPresent()) {
                        model.addAttribute("deposit", depositOpt.get());
                    } else {
                        model.addAttribute("error", "Không tìm thấy giao dịch với mã: " + refId);
                    }
                }
            } else {
                model.addAttribute("error", "Không có mã giao dịch.");
            }
            model.addAttribute("resultCode", resultCode);
        } catch (Exception e) {
            model.addAttribute("error", "Lỗi xử lý tham số trả về: " + e.getMessage());
        }

        return "payment/result";
    }

    @PostMapping("/momo/ipn")
    @ResponseBody
    public String momoIpn(@RequestBody Map<String, String> params) {
        String referenceId = params.getOrDefault("orderId", params.get("referenceId"));
        
        try {
            if (referenceId != null && !referenceId.isBlank()) {
                Long refId = Long.parseLong(referenceId);
                
                if (escrowTransactionRepository.findById(refId).isPresent()) {
                    holdFundsUseCase.confirmHeld(params.get("transId"), params);
                } else if (depositRepository.findById(refId).isPresent()) {
                    lockDepositUseCase.confirmLocked(params.get("transId"), params);
                }
            }
        } catch (Exception e) {
            // Log the exception but still return 200 so MoMo does not retry indefinitely
            e.printStackTrace();
        }
        
        // MoMo expects a 200 OK status.
        return "";
    }
}
