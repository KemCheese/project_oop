package com.yourapp.ticketpass.payment.web;

import java.math.BigDecimal;
import java.security.Principal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;

import com.yourapp.ticketpass.payment.application.HoldFundsUseCase;
import com.yourapp.ticketpass.payment.application.LockDepositUseCase;
import com.yourapp.ticketpass.payment.domain.Deposit;
import com.yourapp.ticketpass.payment.domain.DepositRepository;
import com.yourapp.ticketpass.payment.domain.DepositStatus;
import com.yourapp.ticketpass.payment.domain.EscrowStatus;
import com.yourapp.ticketpass.payment.domain.EscrowTransaction;
import com.yourapp.ticketpass.payment.domain.EscrowTransactionRepository;
import com.yourapp.ticketpass.shared.config.PaymentGatewayConfig;
import com.yourapp.ticketpass.ticket.domain.TicketRepository;
import com.yourapp.ticketpass.user.domain.UserRepository;

/**
 * Unit test cho {@link PaymentController} theo phong cách Mockito thuần của dự án.
 */
@ExtendWith(MockitoExtension.class)
class PaymentControllerTest {

    @Mock
    private HoldFundsUseCase holdFundsUseCase;

    @Mock
    private LockDepositUseCase lockDepositUseCase;

    @Mock
    private EscrowTransactionRepository escrowTransactionRepository;

    @Mock
    private DepositRepository depositRepository;

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PaymentGatewayConfig paymentGatewayConfig;

    @Mock
    private Principal principal;

    @InjectMocks
    private PaymentController controller;

    private Model model;

    @BeforeEach
    void setUp() {
        model = new ConcurrentModel();
    }

    @Test
    @DisplayName("showTransferPage() - Trả về view payment/transfer với đầy đủ thông tin QR cho escrow")
    void showTransferPage_escrow_success() {
        EscrowTransaction escrow = new EscrowTransaction();
        escrow.setId(10L);
        escrow.setTicketId(100L);
        escrow.setSalePrice(new BigDecimal("500000"));
        escrow.setStatus(EscrowStatus.PENDING);

        when(escrowTransactionRepository.findById(10L)).thenReturn(Optional.of(escrow));
        when(paymentGatewayConfig.getAccountHolder()).thenReturn("TICKETPASS TRUNG GIAN");
        when(paymentGatewayConfig.getAccountNumber()).thenReturn("0123456789");
        when(paymentGatewayConfig.getBankName()).thenReturn("Vietcombank");
        when(paymentGatewayConfig.getQrImageUrl()).thenReturn("/images/qr-payment.png");
        when(paymentGatewayConfig.getTransferNotePrefix()).thenReturn("TICKETPASS");

        String view = controller.showTransferPage("10", "escrow", model);

        assertEquals("payment/transfer", view);
        assertEquals(new BigDecimal("500000"), model.getAttribute("amount"));
        assertEquals(100L, model.getAttribute("ticketId"));
        assertEquals("TICKETPASS TRUNG GIAN", model.getAttribute("accountHolder"));
        assertEquals("0123456789", model.getAttribute("accountNumber"));
        assertEquals("Vietcombank", model.getAttribute("bankName"));
        assertEquals("TICKETPASS 10", model.getAttribute("transferNote"));
    }

    @Test
    @DisplayName("showTransferPage() - Trả về view payment/transfer với đầy đủ thông tin QR cho deposit")
    void showTransferPage_deposit_success() {
        Deposit deposit = new Deposit();
        deposit.setId(20L);
        deposit.setTicketId(200L);
        deposit.setAmount(new BigDecimal("250000"));
        deposit.setStatus(DepositStatus.PENDING);

        when(depositRepository.findById(20L)).thenReturn(Optional.of(deposit));
        when(paymentGatewayConfig.getAccountHolder()).thenReturn("TICKETPASS TRUNG GIAN");
        when(paymentGatewayConfig.getAccountNumber()).thenReturn("0123456789");
        when(paymentGatewayConfig.getBankName()).thenReturn("Vietcombank");
        when(paymentGatewayConfig.getQrImageUrl()).thenReturn("/images/qr-payment.png");
        when(paymentGatewayConfig.getTransferNotePrefix()).thenReturn("TICKETPASS");

        String view = controller.showTransferPage("20", "deposit", model);

        assertEquals("payment/transfer", view);
        assertEquals(new BigDecimal("250000"), model.getAttribute("amount"));
        assertEquals(200L, model.getAttribute("ticketId"));
    }

    @Test
    @DisplayName("showTransferPage() - Loại giao dịch không hợp lệ trả về view payment/result kèm lỗi")
    void showTransferPage_invalidType() {
        String view = controller.showTransferPage("10", "unknown_type", model);

        assertEquals("payment/result", view);
        assertTrue(model.containsAttribute("error"));
    }

    @Test
    @DisplayName("confirmTransfer() - Xác nhận chuyển khoản ký quỹ thành công và chuyển hướng về trang vé")
    void confirmTransfer_escrow_success() {
        EscrowTransaction escrow = new EscrowTransaction();
        escrow.setId(10L);
        escrow.setTicketId(100L);
        escrow.setSalePrice(new BigDecimal("500000"));
        escrow.setStatus(EscrowStatus.HELD);

        when(holdFundsUseCase.confirmHeld(isNull(), any())).thenReturn(escrow);

        org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes = new org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap();
        String view = controller.confirmTransfer("10", "escrow", principal, redirectAttributes, model);

        assertEquals("redirect:/tickets/100", view);
        assertTrue(redirectAttributes.getFlashAttributes().containsKey("successMessage"));
    }

    @Test
    @DisplayName("confirmTransfer() - Xác nhận chuyển khoản đóng cọc thành công và chuyển hướng về trang vé")
    void confirmTransfer_deposit_success() {
        Deposit deposit = new Deposit();
        deposit.setId(20L);
        deposit.setTicketId(200L);
        deposit.setAmount(new BigDecimal("250000"));
        deposit.setStatus(DepositStatus.LOCKED);

        when(lockDepositUseCase.confirmLocked(isNull(), any())).thenReturn(deposit);

        org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes = new org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap();
        String view = controller.confirmTransfer("20", "deposit", principal, redirectAttributes, model);

        assertEquals("redirect:/tickets/200", view);
        assertTrue(redirectAttributes.getFlashAttributes().containsKey("successMessage"));
    }

    @Test
    @DisplayName("cancelTransfer() - Người dùng nhấn chưa chuyển khoản, redirect về trang vé")
    void cancelTransfer_redirectsToTicket() {
        EscrowTransaction escrow = new EscrowTransaction();
        escrow.setId(10L);
        escrow.setTicketId(100L);

        when(escrowTransactionRepository.findById(10L)).thenReturn(Optional.of(escrow));

        String redirect = controller.cancelTransfer("10", "escrow");

        assertEquals("redirect:/tickets/100", redirect);
    }

    @Test
    @DisplayName("buyTicket() - Khởi tạo ký quỹ thành công và chuyển hướng sang payUrl trang QR")
    void buyTicket_success() {
        com.yourapp.ticketpass.user.domain.User buyer = new com.yourapp.ticketpass.user.domain.User();
        buyer.setId(1L);
        buyer.setEmail("buyer@example.com");

        com.yourapp.ticketpass.ticket.domain.Ticket ticket = new com.yourapp.ticketpass.ticket.domain.Ticket();
        ticket.setId(100L);
        ticket.setSellerId(2L);
        ticket.setSalePrice(new BigDecimal("500000"));

        when(principal.getName()).thenReturn("buyer@example.com");
        when(userRepository.findByEmail("buyer@example.com")).thenReturn(Optional.of(buyer));
        when(ticketRepository.findById(100L)).thenReturn(Optional.of(ticket));
        when(escrowTransactionRepository.findByTicketId(100L)).thenReturn(Optional.empty());

        com.yourapp.ticketpass.payment.domain.PaymentResult result = new com.yourapp.ticketpass.payment.domain.PaymentResult();
        result.setSuccess(true);
        result.setPayUrl("/payment/transfer?ref=10&type=escrow");
        when(holdFundsUseCase.initEscrowPayment(100L, 1L, 2L, new BigDecimal("500000"))).thenReturn(result);

        String redirect = controller.buyTicket(100L, principal);

        assertEquals("redirect:/payment/transfer?ref=10&type=escrow", redirect);
    }

    @Test
    @DisplayName("lockDeposit() - Khởi tạo cọc thành công và chuyển hướng sang payUrl trang QR")
    void lockDeposit_success() {
        com.yourapp.ticketpass.user.domain.User seller = new com.yourapp.ticketpass.user.domain.User();
        seller.setId(2L);
        seller.setEmail("seller@example.com");

        com.yourapp.ticketpass.ticket.domain.Ticket ticket = new com.yourapp.ticketpass.ticket.domain.Ticket();
        ticket.setId(100L);
        ticket.setSellerId(2L);
        ticket.setSalePrice(new BigDecimal("500000"));

        when(principal.getName()).thenReturn("seller@example.com");
        when(userRepository.findByEmail("seller@example.com")).thenReturn(Optional.of(seller));
        when(ticketRepository.findById(100L)).thenReturn(Optional.of(ticket));
        when(depositRepository.findByTicketId(100L)).thenReturn(Optional.empty());

        com.yourapp.ticketpass.payment.domain.PaymentResult result = new com.yourapp.ticketpass.payment.domain.PaymentResult();
        result.setSuccess(true);
        result.setPayUrl("/payment/transfer?ref=20&type=deposit");
        when(lockDepositUseCase.initDepositPayment(100L, 2L, new BigDecimal("500000"))).thenReturn(result);

        String redirect = controller.lockDeposit(100L, principal);

        assertEquals("redirect:/payment/transfer?ref=20&type=deposit", redirect);
    }
}
