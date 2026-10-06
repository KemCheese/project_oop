package com.yourapp.ticketpass.payment.web;

import java.security.Principal;
import java.util.HashMap;
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
import com.yourapp.ticketpass.payment.domain.DepositStatus;
import com.yourapp.ticketpass.payment.domain.EscrowStatus;
import com.yourapp.ticketpass.payment.domain.EscrowTransaction;
import com.yourapp.ticketpass.payment.domain.EscrowTransactionRepository;
import com.yourapp.ticketpass.payment.domain.PaymentResult;
import com.yourapp.ticketpass.payment.web.dto.EscrowSummaryResponse;
import com.yourapp.ticketpass.payment.web.dto.InitDepositRequest;
import com.yourapp.ticketpass.payment.web.dto.InitEscrowRequest;
import com.yourapp.ticketpass.payment.web.dto.PaymentInitResponse;
import com.yourapp.ticketpass.shared.config.PaymentGatewayConfig;
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
 * và xử lý xác nhận chuyển khoản thủ công qua QR trung gian.
 *
 * <p><b>Cơ chế thanh toán:</b> hệ thống hiển thị mã QR tài khoản trung gian (sàn) cho
 * người dùng. Người dùng chuyển khoản qua app ngân hàng, sau đó nhấn nút xác nhận trên
 * giao diện. Admin sẽ kiểm tra thủ công khoản chuyển khoản đã vào tài khoản hay chưa.</p>
 *
 * <p>Module: payment</p>
 * <p>Chủ sở hữu: Người B</p>
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
    private final PaymentGatewayConfig paymentGatewayConfig;

    /**
     * Tra cứu userId từ phiên đăng nhập hiện tại.
     *
     * @param principal phiên đăng nhập
     * @return ID người dùng
     */
    private Long resolveUserId(Principal principal) {
        String email = principal.getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", email));
        return user.getId();
    }

    // ========================= Khởi tạo thanh toán =========================

    /**
     * Người mua bắt đầu thanh toán vào ký quỹ cho một vé.
     *
     * <p>Lấy buyerId từ phiên đăng nhập (KHÔNG nhận từ request body để tránh lỗ hổng bảo mật),
     * tra vé theo ticketId để lấy sellerId và giá bán, gọi
     * {@code holdFundsUseCase.initEscrowPayment()} rồi trả về payUrl trỏ tới trang QR
     * chuyển khoản.</p>
     *
     * @param request   dữ liệu yêu cầu, gồm ID vé
     * @param principal người dùng đang đăng nhập, dùng để suy ra ID người mua
     * @return phản hồi có {@code payUrl} để giao diện chuyển hướng người mua tới trang QR
     */
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

    /**
     * Người bán bắt đầu khoá cọc 50% giá bán cho vé của mình.
     *
     * <p>Lấy sellerId từ phiên đăng nhập, kiểm tra đúng chủ vé, gọi
     * {@code lockDepositUseCase.initDepositPayment()} rồi trả về payUrl trỏ tới trang QR
     * chuyển khoản.</p>
     *
     * @param request   dữ liệu yêu cầu, gồm ID vé
     * @param principal người dùng đang đăng nhập, dùng để suy ra ID người bán
     * @return phản hồi có {@code payUrl} để giao diện chuyển hướng người bán tới trang QR
     */
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

    /**
     * Người mua bắt đầu thanh toán ký quỹ qua form HTML trên web (chuyển hướng trực tiếp sang trang QR).
     *
     * @param ticketId  ID vé
     * @param principal người dùng đăng nhập (buyer)
     * @return chuyển hướng sang trang QR chuyển khoản (/payment/transfer?ref=...&type=escrow)
     */
    @PostMapping("/escrow/buy")
    public String buyTicket(@RequestParam Long ticketId, Principal principal) {
        Long buyerId = resolveUserId(principal);
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket", ticketId));

        // Nếu đã có escrow PENDING của chính buyer này, chuyển hướng luôn sang trang QR
        Optional<EscrowTransaction> existingOpt = escrowTransactionRepository.findByTicketId(ticket.getId());
        if (existingOpt.isPresent() && existingOpt.get().getStatus() == EscrowStatus.PENDING
                && buyerId.equals(existingOpt.get().getBuyerId())) {
            return "redirect:/payment/transfer?ref=" + existingOpt.get().getId() + "&type=escrow";
        }

        PaymentResult result = holdFundsUseCase.initEscrowPayment(
                ticket.getId(), buyerId, ticket.getSellerId(), ticket.getSalePrice());

        if (result.isSuccess() && result.getPayUrl() != null) {
            return "redirect:" + result.getPayUrl();
        } else {
            throw new BusinessRuleViolationException(
                    result.getMessage() != null ? result.getMessage() : "Không thể khởi tạo thanh toán.");
        }
    }

    @GetMapping("/escrow/buy")
    public String buyTicketGet(@RequestParam Long ticketId, Principal principal) {
        return buyTicket(ticketId, principal);
    }

    /**
     * Người bán bắt đầu nộp tiền bảo lãnh cọc qua form HTML trên web (chuyển hướng sang trang QR).
     *
     * @param ticketId  ID vé
     * @param principal người dùng đăng nhập (seller)
     * @return chuyển hướng sang trang QR chuyển khoản (/payment/transfer?ref=...&type=deposit)
     */
    @PostMapping("/deposit/lock")
    public String lockDeposit(@RequestParam Long ticketId, Principal principal) {
        Long sellerId = resolveUserId(principal);
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket", ticketId));

        if (!sellerId.equals(ticket.getSellerId())) {
            throw new BusinessRuleViolationException("Bạn không phải chủ sở hữu của vé này.");
        }

        // Nếu đã có cọc PENDING, chuyển hướng luôn sang trang QR
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

    /**
     * Endpoint tương thích ngược (fallback) cho các liên kết MoMo cũ còn sót lại trên trình duyệt.
     */
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

    // ========================= Trang QR chuyển khoản =========================

    /**
     * Hiển thị trang chuyển khoản QR cho người dùng.
     *
     * <p>Trang này hiển thị mã QR của tài khoản trung gian, số tiền cần chuyển,
     * nội dung chuyển khoản, và hai nút: "Đã chuyển khoản thành công" / "Chưa chuyển khoản".</p>
     *
     * <p>TUYỆT ĐỐI KHÔNG thay đổi trạng thái giao dịch tại đây — chỉ hiển thị thông tin.</p>
     *
     * @param ref   mã tham chiếu giao dịch (ID escrow hoặc deposit)
     * @param type  loại giao dịch: {@code "escrow"} hoặc {@code "deposit"}
     * @param model model để đổ dữ liệu ra view
     * @return tên view {@code "payment/transfer"}
     */
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

            // Thông tin QR chuyển khoản
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

    // ========================= Xác nhận chuyển khoản =========================

    /**
     * Người dùng nhấn "Đã chuyển khoản thành công".
     *
     * <p>Gọi {@code confirmHeld()} hoặc {@code confirmLocked()} tùy loại giao dịch để
     * chuyển trạng thái từ PENDING sang HELD/LOCKED. Admin sẽ kiểm tra thủ công sau.</p>
     *
     * @param ref       mã tham chiếu giao dịch
     * @param type      loại giao dịch: {@code "escrow"} hoặc {@code "deposit"}
     * @param principal người dùng đang đăng nhập
     * @param model     model để đổ dữ liệu ra view
     * @return tên view {@code "payment/result"}
     */
    @PostMapping("/transfer/confirm")
    public String confirmTransfer(@RequestParam String ref, @RequestParam String type,
                                  Principal principal, Model model) {
        try {
            Long refId = Long.parseLong(ref);

            // Tạo callbackParams giả lập để truyền vào confirmHeld/confirmLocked
            Map<String, String> callbackParams = new HashMap<>();
            callbackParams.put("referenceId", ref);
            callbackParams.put("orderId", ref);

            if ("escrow".equals(type)) {
                EscrowTransaction tx = holdFundsUseCase.confirmHeld(null, callbackParams);
                EscrowSummaryResponse summary = new EscrowSummaryResponse();
                summary.setId(tx.getId());
                summary.setTicketId(tx.getTicketId());
                summary.setStatus(tx.getStatus());
                summary.setSalePrice(tx.getSalePrice());
                summary.setBuyerCommission(tx.getBuyerCommission());
                summary.setSellerCommission(tx.getSellerCommission());
                summary.setTaxWithheld(tx.getTaxWithheld());
                summary.setSellerNetPayout(tx.getSellerNetPayout());
                summary.setHeldAt(tx.getHeldAt());
                summary.setReleasedAt(tx.getReleasedAt());
                model.addAttribute("escrow", summary);
                model.addAttribute("ticketId", tx.getTicketId());
                model.addAttribute("successMessage",
                        "Thanh toán thành công! Hệ thống đã ghi nhận tiền ký quỹ Escrow và chuyển vé sang bước tiếp theo.");
            } else if ("deposit".equals(type)) {
                Deposit deposit = lockDepositUseCase.confirmLocked(null, callbackParams);
                model.addAttribute("deposit", deposit);
                model.addAttribute("ticketId", deposit.getTicketId());
                model.addAttribute("successMessage",
                        "Khóa cọc thành công! Hệ thống đã tự động chuyển giao vé và mở mã QR cho người mua kiểm tra.");
            } else {
                model.addAttribute("error", "Loại giao dịch không hợp lệ: " + type);
            }

        } catch (Exception e) {
            model.addAttribute("error", "Lỗi xác nhận chuyển khoản: " + e.getMessage());
        }

        return "payment/result";
    }

    /**
     * Người dùng nhấn "Thanh toán thất bại" — đánh dấu FAILED và hiển thị trang kết quả lỗi.
     *
     * @param ref   mã tham chiếu giao dịch
     * @param type  loại giao dịch
     * @param model model truyền dữ liệu sang view
     * @return tên view "payment/result"
     */
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

    /**
     * Người dùng nhấn "Chưa chuyển khoản" — quay lại trang chi tiết vé.
     *
     * <p>Không thay đổi trạng thái giao dịch, chỉ redirect người dùng về trang vé.</p>
     *
     * @param ref  mã tham chiếu giao dịch
     * @param type loại giao dịch
     * @return chuyển hướng về trang chi tiết vé
     */
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
            // Fall through to default redirect
        }

        return "redirect:/tickets";
    }
}
