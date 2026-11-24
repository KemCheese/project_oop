package com.yourapp.ticketpass.wallet.web;

import java.math.BigDecimal;
import java.security.Principal;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.yourapp.ticketpass.shared.exception.ResourceNotFoundException;
import com.yourapp.ticketpass.user.domain.User;
import com.yourapp.ticketpass.user.domain.UserRepository;
import com.yourapp.ticketpass.wallet.application.WalletService;
import com.yourapp.ticketpass.wallet.domain.Wallet;
import com.yourapp.ticketpass.wallet.domain.WalletTransaction;
import com.yourapp.ticketpass.wallet.domain.WalletTransactionType;
import com.yourapp.ticketpass.wallet.web.dto.TopUpRequest;
import com.yourapp.ticketpass.wallet.web.dto.WalletResponse;
import com.yourapp.ticketpass.wallet.web.dto.WalletTransactionResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Controller
@RequestMapping("/wallet")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;
    private final UserRepository userRepository;

    private Long resolveUserId(Principal principal) {
        if (principal == null) {
            throw new ResourceNotFoundException("Vui lòng đăng nhập để truy cập ví.");
        }
        String email = principal.getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", email));
        return user.getId();
    }

    @GetMapping
    public String showWallet(Principal principal, Model model) {
        Long userId = resolveUserId(principal);
        Wallet wallet = walletService.getOrCreateWallet(userId);
        WalletResponse walletResponse = WalletResponse.fromDomain(wallet);

        List<WalletTransaction> rawTransactions = walletService.getTransactionHistory(userId, 100);
        List<WalletTransactionResponse> transactionResponses = rawTransactions.stream()
                .map(WalletTransactionResponse::fromDomain)
                .collect(Collectors.toList());

        BigDecimal totalIncome = rawTransactions.stream()
                .filter(tx -> tx.getType() != null && tx.getType().isCredit() && tx.getType() != WalletTransactionType.TOP_UP)
                .map(WalletTransaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalExpense = rawTransactions.stream()
                .filter(tx -> tx.getType() != null && !tx.getType().isCredit())
                .map(WalletTransaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalTopUp = rawTransactions.stream()
                .filter(tx -> tx.getType() == WalletTransactionType.TOP_UP)
                .map(WalletTransaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        model.addAttribute("wallet", walletResponse);
        model.addAttribute("transactions", transactionResponses);
        model.addAttribute("totalIncome", totalIncome);
        model.addAttribute("totalExpense", totalExpense);
        model.addAttribute("totalTopUp", totalTopUp);
        if (!model.containsAttribute("topUpRequest")) {
            model.addAttribute("topUpRequest", new TopUpRequest());
        }

        return "wallet/index";
    }

    @PostMapping("/topup")
    public String handleTopUp(@Valid @ModelAttribute("topUpRequest") TopUpRequest request,
                              BindingResult bindingResult,
                              Principal principal,
                              RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            String errorMsg = bindingResult.getFieldError() != null
                    ? bindingResult.getFieldError().getDefaultMessage()
                    : "Thông tin nạp tiền không hợp lệ.";
            redirectAttributes.addFlashAttribute("errorMessage", errorMsg);
            return "redirect:/wallet";
        }

        try {
            Long userId = resolveUserId(principal);
            walletService.topUp(userId, request.getAmount(), request.getNote());
            redirectAttributes.addFlashAttribute("successMessage",
                    String.format("Nạp tiền thành công! Đã cộng %,.0f VNĐ vào ví điện tử TicketPass.", request.getAmount()));
        } catch (Exception e) {
            log.error("[WalletController] Lỗi nạp tiền vào ví: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "Nạp tiền thất bại: " + e.getMessage());
        }

        return "redirect:/wallet";
    }
}
