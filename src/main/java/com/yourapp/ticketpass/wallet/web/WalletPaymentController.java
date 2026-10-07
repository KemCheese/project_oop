package com.yourapp.ticketpass.wallet.web;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.Principal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.yourapp.ticketpass.payment.application.HoldFundsUseCase;
import com.yourapp.ticketpass.payment.application.LockDepositUseCase;
import com.yourapp.ticketpass.payment.domain.Deposit;
import com.yourapp.ticketpass.payment.domain.DepositRepository;
import com.yourapp.ticketpass.payment.domain.EscrowStatus;
import com.yourapp.ticketpass.payment.domain.EscrowTransaction;
import com.yourapp.ticketpass.payment.domain.EscrowTransactionRepository;
import com.yourapp.ticketpass.payment.domain.PaymentResult;
import com.yourapp.ticketpass.shared.exception.BusinessRuleViolationException;
import com.yourapp.ticketpass.shared.exception.ResourceNotFoundException;
import com.yourapp.ticketpass.ticket.domain.Ticket;
import com.yourapp.ticketpass.ticket.domain.TicketRepository;
import com.yourapp.ticketpass.user.domain.User;
import com.yourapp.ticketpass.user.domain.UserRepository;
import com.yourapp.ticketpass.wallet.application.WalletService;
import com.yourapp.ticketpass.wallet.domain.Wallet;
import com.yourapp.ticketpass.wallet.domain.WalletTransactionType;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Controller phục vụ các thao tác thanh toán 1-click trực tiếp từ Ví điện tử TicketPass.
 *
 * <p>Module: wallet</p>
 * <p>Chủ sở hữu: Người C (Dev C)</p>
 */
@Slf4j
@Controller
@RequestMapping("/payment/wallet")
@RequiredArgsConstructor
public class WalletPaymentController {

    private final WalletService walletService;
    private final HoldFundsUseCase holdFundsUseCase;
    private final LockDepositUseCase lockDepositUseCase;
    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final EscrowTransactionRepository escrowTransactionRepository;
    private final DepositRepository depositRepository;

    private Long resolveUserId(Principal principal) {
        if (principal == null) {
            throw new ResourceNotFoundException("Vui lòng đăng nhập để thực hiện thanh toán.");
        }
        String email = principal.getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", email));
        return user.getId();
    }

    /**
     * Người mua thanh toán ký quỹ 100% tiền vé bằng số dư Ví điện tử TicketPass (1-Click).
     */
    @PostMapping("/pay-escrow")
    public String payEscrowWithWallet(@RequestParam Long ticketId,
                                      Principal principal,
                                      RedirectAttributes redirectAttributes) {
        Long buyerId = resolveUserId(principal);
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket", ticketId));

        if (buyerId.equals(ticket.getSellerId())) {
            redirectAttributes.addFlashAttribute("errorMessage", "Bạn không thể tự mua vé của chính mình.");
            return "redirect:/tickets/" + ticketId;
        }

        BigDecimal salePrice = ticket.getSalePrice();
        Wallet wallet = walletService.getOrCreateWallet(buyerId);

        // Kiểm tra số dư ví trước
        if (!wallet.hasSufficientBalance(salePrice)) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    String.format("Số dư ví không đủ để thanh toán (Hiện có: %,.0f VNĐ, Cần: %,.0f VNĐ). Vui lòng nạp thêm tiền vào ví.",
                            wallet.getBalance(), salePrice));
            return "redirect:/tickets/" + ticketId;
        }

        try {
            // Khởi tạo escrow nếu chưa có hoặc tái sử dụng escrow PENDING hiện tại
            Optional<EscrowTransaction> existingOpt = escrowTransactionRepository.findByTicketId(ticketId);
            EscrowTransaction tx;
            if (existingOpt.isPresent() && existingOpt.get().getStatus() == EscrowStatus.PENDING) {
                tx = existingOpt.get();
            } else {
                PaymentResult initResult = holdFundsUseCase.initEscrowPayment(
                        ticket.getId(), buyerId, ticket.getSellerId(), salePrice);
                if (!initResult.isSuccess()) {
                    throw new BusinessRuleViolationException(initResult.getMessage());
                }
                tx = escrowTransactionRepository.findByTicketId(ticketId)
                        .orElseThrow(() -> new IllegalStateException("Không tìm thấy Escrow vừa khởi tạo"));
            }

            // Trừ tiền ví an toàn với khóa bi quan
            walletService.debit(buyerId, salePrice, WalletTransactionType.ESCROW_PAY,
                    "ESCROW_" + tx.getId(), "Thanh toán ký quỹ vé #" + ticketId + " (" + ticket.getTitle() + ")");

            // Xác nhận giữ tiền ký quỹ
            Map<String, String> callbackParams = new HashMap<>();
            callbackParams.put("referenceId", String.valueOf(tx.getId()));
            callbackParams.put("orderId", String.valueOf(tx.getId()));
            holdFundsUseCase.confirmHeld(null, callbackParams);

            redirectAttributes.addFlashAttribute("successMessage",
                    String.format("Thanh toán bằng Ví TicketPass thành công! Đã ký quỹ %,.0f VNĐ an toàn.", salePrice));
        } catch (Exception e) {
            log.error("[WalletPaymentController] Lỗi thanh toán ký quỹ bằng ví: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "Thanh toán bằng ví thất bại: " + e.getMessage());
        }

        return "redirect:/tickets/" + ticketId;
    }

    /**
     * Người bán nộp tiền cọc bảo lãnh 50% bằng số dư Ví điện tử TicketPass (1-Click).
     */
    @PostMapping("/pay-deposit")
    public String payDepositWithWallet(@RequestParam Long ticketId,
                                       Principal principal,
                                       RedirectAttributes redirectAttributes) {
        Long sellerId = resolveUserId(principal);
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket", ticketId));

        if (!sellerId.equals(ticket.getSellerId())) {
            redirectAttributes.addFlashAttribute("errorMessage", "Bạn không phải chủ sở hữu của vé này.");
            return "redirect:/tickets/" + ticketId;
        }

        BigDecimal depositAmount = ticket.getSalePrice().multiply(new BigDecimal("0.50")).setScale(0, RoundingMode.HALF_UP);
        Wallet wallet = walletService.getOrCreateWallet(sellerId);

        // Kiểm tra số dư ví
        if (!wallet.hasSufficientBalance(depositAmount)) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    String.format("Số dư ví không đủ để nộp cọc 50%% (Hiện có: %,.0f VNĐ, Cần: %,.0f VNĐ). Vui lòng nạp thêm tiền vào ví.",
                            wallet.getBalance(), depositAmount));
            return "redirect:/tickets/" + ticketId;
        }

        try {
            Optional<Deposit> existingOpt = depositRepository.findByTicketId(ticketId);
            Deposit deposit;
            if (existingOpt.isPresent()) {
                deposit = existingOpt.get();
            } else {
                PaymentResult initResult = lockDepositUseCase.initDepositPayment(
                        ticket.getId(), sellerId, ticket.getSalePrice());
                if (!initResult.isSuccess()) {
                    throw new BusinessRuleViolationException(initResult.getMessage());
                }
                deposit = depositRepository.findByTicketId(ticketId)
                        .orElseThrow(() -> new IllegalStateException("Không tìm thấy Deposit vừa khởi tạo"));
            }

            // Trừ tiền cọc khỏi ví với khóa bi quan
            walletService.debit(sellerId, deposit.getAmount(), WalletTransactionType.DEPOSIT_LOCK,
                    "DEPOSIT_" + deposit.getId(), "Khóa cọc bảo đảm 50% cho vé #" + ticketId + " (" + ticket.getTitle() + ")");

            // Xác nhận khóa cọc (hệ thống sẽ tự động chuyển vé sang TRANSFERRED)
            Map<String, String> callbackParams = new HashMap<>();
            callbackParams.put("referenceId", String.valueOf(deposit.getId()));
            callbackParams.put("orderId", String.valueOf(deposit.getId()));
            lockDepositUseCase.confirmLocked(null, callbackParams);

            redirectAttributes.addFlashAttribute("successMessage",
                    String.format("Khóa cọc 50%% (%,.0f VNĐ) bằng Ví thành công! Vé đã được tự động mở cho người mua kiểm tra.", deposit.getAmount()));
        } catch (Exception e) {
            log.error("[WalletPaymentController] Lỗi khóa cọc bằng ví: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "Khóa cọc bằng ví thất bại: " + e.getMessage());
        }

        return "redirect:/tickets/" + ticketId;
    }

    /**
     * Thanh toán trực tiếp từ trang QR chuyển khoản (/payment/transfer).
     */
    @PostMapping("/pay-from-transfer")
    public String payFromTransfer(@RequestParam String ref,
                                  @RequestParam String type,
                                  Principal principal,
                                  RedirectAttributes redirectAttributes) {
        Long userId = resolveUserId(principal);
        try {
            Long refId = Long.parseLong(ref);
            Wallet wallet = walletService.getOrCreateWallet(userId);

            if ("escrow".equals(type)) {
                EscrowTransaction escrow = escrowTransactionRepository.findById(refId)
                        .orElseThrow(() -> new ResourceNotFoundException("EscrowTransaction", refId));

                if (!userId.equals(escrow.getBuyerId())) {
                    throw new BusinessRuleViolationException("Bạn không phải người mua của giao dịch này.");
                }

                if (!wallet.hasSufficientBalance(escrow.getSalePrice())) {
                    redirectAttributes.addFlashAttribute("errorMessage",
                            String.format("Số dư ví không đủ (Hiện có: %,.0f VNĐ, Cần: %,.0f VNĐ). Vui lòng nạp tiền vào ví.",
                                    wallet.getBalance(), escrow.getSalePrice()));
                    return "redirect:/payment/transfer?ref=" + ref + "&type=" + type;
                }

                // Trừ ví
                walletService.debit(userId, escrow.getSalePrice(), WalletTransactionType.ESCROW_PAY,
                        "ESCROW_" + refId, "Thanh toán ký quỹ vé #" + escrow.getTicketId());

                // Xác nhận HELD
                Map<String, String> callbackParams = new HashMap<>();
                callbackParams.put("referenceId", ref);
                callbackParams.put("orderId", ref);
                holdFundsUseCase.confirmHeld(null, callbackParams);

                redirectAttributes.addFlashAttribute("successMessage",
                        "Thanh toán ký quỹ thành công qua Ví TicketPass!");
                return "redirect:/tickets/" + escrow.getTicketId();

            } else if ("deposit".equals(type)) {
                Deposit deposit = depositRepository.findById(refId)
                        .orElseThrow(() -> new ResourceNotFoundException("Deposit", refId));

                if (!userId.equals(deposit.getSellerId())) {
                    throw new BusinessRuleViolationException("Bạn không phải người bán của giao dịch này.");
                }

                if (!wallet.hasSufficientBalance(deposit.getAmount())) {
                    redirectAttributes.addFlashAttribute("errorMessage",
                            String.format("Số dư ví không đủ (Hiện có: %,.0f VNĐ, Cần: %,.0f VNĐ). Vui lòng nạp tiền vào ví.",
                                    wallet.getBalance(), deposit.getAmount()));
                    return "redirect:/payment/transfer?ref=" + ref + "&type=" + type;
                }

                // Trừ ví
                walletService.debit(userId, deposit.getAmount(), WalletTransactionType.DEPOSIT_LOCK,
                        "DEPOSIT_" + refId, "Khóa cọc 50% vé #" + deposit.getTicketId());

                // Xác nhận LOCKED
                Map<String, String> callbackParams = new HashMap<>();
                callbackParams.put("referenceId", ref);
                callbackParams.put("orderId", ref);
                lockDepositUseCase.confirmLocked(null, callbackParams);

                redirectAttributes.addFlashAttribute("successMessage",
                        "Khóa cọc bảo đảm 50% thành công qua Ví TicketPass!");
                return "redirect:/tickets/" + deposit.getTicketId();
            } else {
                throw new BusinessRuleViolationException("Loại giao dịch không hợp lệ: " + type);
            }
        } catch (Exception e) {
            log.error("[WalletPaymentController] Lỗi pay-from-transfer: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "Lỗi thanh toán ví: " + e.getMessage());
            return "redirect:/payment/transfer?ref=" + ref + "&type=" + type;
        }
    }
}
