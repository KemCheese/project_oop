package com.yourapp.ticketpass.wallet.web;

import com.yourapp.ticketpass.shared.exception.BusinessRuleViolationException;
import com.yourapp.ticketpass.shared.exception.ResourceNotFoundException;
import com.yourapp.ticketpass.user.domain.User;
import com.yourapp.ticketpass.user.domain.UserRepository;
import com.yourapp.ticketpass.wallet.application.WalletCheckoutUseCase;
import com.yourapp.ticketpass.wallet.application.WalletCheckoutUseCase.CheckoutResult;
import java.security.Principal;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
@Controller
@RequestMapping("/payment/wallet")
@RequiredArgsConstructor
public class WalletPaymentController {

    private static final Set<String> ALLOWED_TRANSFER_TYPES = Set.of("escrow", "deposit");

    private final WalletCheckoutUseCase walletCheckoutUseCase;
    private final UserRepository userRepository;

    private Long resolveUserId(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            throw new ResourceNotFoundException("Vui lòng đăng nhập để thực hiện thanh toán.");
        }
        String email = principal.getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", email));
        return user.getId();
    }

    @PostMapping("/pay-escrow")
    public String payEscrowWithWallet(@RequestParam Long ticketId,
                                      @RequestParam(defaultValue = "false") boolean confirmOverlap,
                                      Principal principal,
                                      RedirectAttributes redirectAttributes) {
        Long buyerId = resolveUserId(principal);
        try {
            CheckoutResult result = walletCheckoutUseCase.payEscrowWithWallet(ticketId, buyerId, confirmOverlap);
            if (result.isOverlapWarning()) {
                if (redirectAttributes != null) {
                    redirectAttributes.addFlashAttribute("overlapWarning", true);
                    redirectAttributes.addFlashAttribute("overlapConflicts", result.getConflicts());
                    redirectAttributes.addFlashAttribute("pendingPaymentMethod", "WALLET");
                }
                return "redirect:/tickets/" + ticketId + "?overlapWarning=true&method=wallet";
            }

            if (redirectAttributes != null) {
                redirectAttributes.addFlashAttribute("successMessage",
                        String.format("Thanh toán bằng Ví TicketPass thành công! Đã ký quỹ %,.0f VNĐ an toàn.",
                                result.getChargedAmount()));
            }
        } catch (BusinessRuleViolationException e) {
            log.warn("[WalletPaymentController] Từ chối thanh toán ký quỹ bằng ví cho ticketId={}: {}",
                    ticketId, e.getMessage());
            if (redirectAttributes != null) {
                redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            }
        } catch (OptimisticLockingFailureException e) {
            log.warn("[WalletPaymentController] Xung đột giao dịch đồng thời khi thanh toán ký quỹ ticketId={}", ticketId);
            if (redirectAttributes != null) {
                redirectAttributes.addFlashAttribute("errorMessage",
                        "Vé hoặc số dư ví vừa được cập nhật đồng thời. Vui lòng tải lại trang và thử lại.");
            }
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("[WalletPaymentController] Lỗi hệ thống khi thanh toán ký quỹ bằng ví cho ticketId={}",
                    ticketId, e);
            if (redirectAttributes != null) {
                redirectAttributes.addFlashAttribute("errorMessage",
                        "Thanh toán bằng ví gặp sự cố kỹ thuật tạm thời. Số dư ví của bạn được bảo toàn nguyên vẹn, vui lòng thử lại.");
            }
        }

        return "redirect:/tickets/" + ticketId;
    }

    public String payEscrowWithWallet(Long ticketId,
                                      Principal principal,
                                      RedirectAttributes redirectAttributes) {
        return payEscrowWithWallet(ticketId, false, principal, redirectAttributes);
    }

    @PostMapping("/pay-deposit")
    public String payDepositWithWallet(@RequestParam Long ticketId,
                                       Principal principal,
                                       RedirectAttributes redirectAttributes) {
        Long sellerId = resolveUserId(principal);
        try {
            CheckoutResult result = walletCheckoutUseCase.payDepositWithWallet(ticketId, sellerId);
            if (redirectAttributes != null) {
                redirectAttributes.addFlashAttribute("successMessage",
                        String.format("Khóa cọc 50%% (%,.0f VNĐ) bằng Ví thành công! Mã QR check-in đã được mở khóa ngay cho người mua.",
                                result.getChargedAmount()));
            }
        } catch (BusinessRuleViolationException e) {
            log.warn("[WalletPaymentController] Từ chối khóa cọc bằng ví cho ticketId={}: {}",
                    ticketId, e.getMessage());
            if (redirectAttributes != null) {
                redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            }
        } catch (OptimisticLockingFailureException e) {
            log.warn("[WalletPaymentController] Xung đột giao dịch đồng thời khi khóa cọc ticketId={}", ticketId);
            if (redirectAttributes != null) {
                redirectAttributes.addFlashAttribute("errorMessage",
                        "Giao dịch vừa được cập nhật đồng thời. Vui lòng tải lại trang và thử lại.");
            }
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("[WalletPaymentController] Lỗi hệ thống khi khóa cọc bằng ví cho ticketId={}", ticketId, e);
            if (redirectAttributes != null) {
                redirectAttributes.addFlashAttribute("errorMessage",
                        "Khóa cọc bằng ví gặp sự cố kỹ thuật tạm thời. Số dư ví của bạn được bảo toàn nguyên vẹn, vui lòng thử lại.");
            }
        }

        return "redirect:/tickets/" + ticketId;
    }

    @PostMapping("/pay-from-transfer")
    public String payFromTransfer(@RequestParam String ref,
                                  @RequestParam String type,
                                  @RequestParam(defaultValue = "false") boolean confirmOverlap,
                                  Principal principal,
                                  RedirectAttributes redirectAttributes) {
        Long userId = resolveUserId(principal);
        String safeType = (type != null && ALLOWED_TRANSFER_TYPES.contains(type)) ? type : null;
        Long refId;
        try {
            refId = Long.parseLong(ref != null ? ref.trim() : "");
        } catch (NumberFormatException ex) {
            log.warn("[WalletPaymentController] Tham số ref không hợp lệ trong pay-from-transfer: ref={}", ref);
            if (redirectAttributes != null) {
                redirectAttributes.addFlashAttribute("errorMessage", "Mã tham chiếu giao dịch không hợp lệ.");
            }
            return "redirect:/wallet";
        }
        if (safeType == null) {
            log.warn("[WalletPaymentController] Loại giao dịch không hợp lệ trong pay-from-transfer: type={}", type);
            if (redirectAttributes != null) {
                redirectAttributes.addFlashAttribute("errorMessage", "Loại giao dịch không hợp lệ.");
            }
            return "redirect:/wallet";
        }

        String safeTransferRedirect = "redirect:" + UriComponentsBuilder.fromPath("/payment/transfer")
                .queryParam("ref", refId)
                .queryParam("type", safeType)
                .toUriString();

        try {
            CheckoutResult result = walletCheckoutUseCase.payFromTransfer(refId, safeType, userId, confirmOverlap);

            if (result.isOverlapWarning()) {
                if (redirectAttributes != null) {
                    redirectAttributes.addFlashAttribute("overlapWarning", true);
                    redirectAttributes.addFlashAttribute("overlapConflicts", result.getConflicts());
                    redirectAttributes.addFlashAttribute("pendingPaymentMethod", "WALLET");
                }
                return "redirect:/tickets/" + result.getTicketId() + "?overlapWarning=true&method=wallet";
            }

            if (redirectAttributes != null) {
                String msg = "escrow".equals(safeType)
                        ? "Thanh toán ký quỹ thành công qua Ví TicketPass!"
                        : "Khóa cọc bảo đảm 50% thành công qua Ví TicketPass!";
                redirectAttributes.addFlashAttribute("successMessage", msg);
            }
            return "redirect:/tickets/" + result.getTicketId();
        } catch (BusinessRuleViolationException e) {
            log.warn("[WalletPaymentController] Từ chối pay-from-transfer (ref={}, type={}): {}",
                    refId, safeType, e.getMessage());
            if (redirectAttributes != null) {
                redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            }
            return safeTransferRedirect;
        } catch (OptimisticLockingFailureException e) {
            log.warn("[WalletPaymentController] Xung đột đồng thời pay-from-transfer (ref={}, type={})", refId, safeType);
            if (redirectAttributes != null) {
                redirectAttributes.addFlashAttribute("errorMessage",
                        "Giao dịch vừa được cập nhật đồng thời. Vui lòng tải lại trang và thử lại.");
            }
            return safeTransferRedirect;
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("[WalletPaymentController] Lỗi hệ thống pay-from-transfer (ref={}, type={})", refId, safeType, e);
            if (redirectAttributes != null) {
                redirectAttributes.addFlashAttribute("errorMessage",
                        "Thanh toán ví gặp sự cố kỹ thuật tạm thời. Số dư ví của bạn được bảo toàn nguyên vẹn.");
            }
            return safeTransferRedirect;
        }
    }

    public String payFromTransfer(String ref,
                                  String type,
                                  Principal principal,
                                  RedirectAttributes redirectAttributes) {
        return payFromTransfer(ref, type, false, principal, redirectAttributes);
    }
}
