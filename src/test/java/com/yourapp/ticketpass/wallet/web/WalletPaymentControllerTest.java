package com.yourapp.ticketpass.wallet.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.security.Principal;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import com.yourapp.ticketpass.payment.application.HoldFundsUseCase;
import com.yourapp.ticketpass.payment.application.LockDepositUseCase;
import com.yourapp.ticketpass.payment.domain.Deposit;
import com.yourapp.ticketpass.payment.domain.DepositRepository;
import com.yourapp.ticketpass.payment.domain.DepositStatus;
import com.yourapp.ticketpass.payment.domain.EscrowStatus;
import com.yourapp.ticketpass.payment.domain.EscrowTransaction;
import com.yourapp.ticketpass.payment.domain.EscrowTransactionRepository;
import com.yourapp.ticketpass.payment.domain.PaymentResult;
import com.yourapp.ticketpass.ticket.domain.Ticket;
import com.yourapp.ticketpass.ticket.domain.TicketRepository;
import com.yourapp.ticketpass.ticket.domain.TicketStatus;
import com.yourapp.ticketpass.user.domain.User;
import com.yourapp.ticketpass.user.domain.UserRepository;
import com.yourapp.ticketpass.wallet.application.WalletService;
import com.yourapp.ticketpass.wallet.domain.Wallet;
import com.yourapp.ticketpass.wallet.domain.WalletTransactionType;

/**
 * Unit Test cho {@link WalletPaymentController} — Task C14.
 *
 * <p>Kiểm thử các luồng thanh toán 1-click bằng Ví điện tử TicketPass:
 * thanh toán ký quỹ 100% tiền vé, người bán nộp cọc bảo đảm 50%,
 * và thanh toán qua trang xác nhận chuyển khoản. Đặc biệt xác minh kịch bản
 * người mua không đủ số dư ví (tài khoản 0đ) bị từ chối chính xác.</p>
 *
 * <p>Module: wallet</p>
 * <p>Chủ sở hữu: Người C (Dev C)</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WalletPaymentController Test - Điều khiển thanh toán 1-Click bằng Ví")
class WalletPaymentControllerTest {

    @Mock
    private WalletService walletService;

    @Mock
    private HoldFundsUseCase holdFundsUseCase;

    @Mock
    private LockDepositUseCase lockDepositUseCase;

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private EscrowTransactionRepository escrowTransactionRepository;

    @Mock
    private DepositRepository depositRepository;

    @InjectMocks
    private WalletPaymentController controller;

    private User buyer;
    private User seller;
    private Ticket ticket;
    private Principal buyerPrincipal;
    private Principal sellerPrincipal;

    @BeforeEach
    void setUp() {
        buyer = new User();
        buyer.setId(10L);
        buyer.setEmail("buyer@test.com");

        seller = new User();
        seller.setId(20L);
        seller.setEmail("seller@test.com");

        buyerPrincipal = () -> "buyer@test.com";
        sellerPrincipal = () -> "seller@test.com";

        ticket = new Ticket();
        ticket.setId(100L);
        ticket.setSellerId(20L);
        ticket.setTitle("Vé Concert BlackPink VIP");
        ticket.setSalePrice(new BigDecimal("1000000.00"));
        ticket.setStatus(TicketStatus.LISTED);
    }

    @Nested
    @DisplayName("Thanh toán ký quỹ 100% bằng Ví (/payment/wallet/pay-escrow)")
    class PayEscrowTests {

        @Test
        @DisplayName("Thanh toán thành công khi số dư ví đủ tiền")
        void testPayEscrowSuccess() {
            when(userRepository.findByEmail("buyer@test.com")).thenReturn(Optional.of(buyer));
            when(ticketRepository.findById(100L)).thenReturn(Optional.of(ticket));

            Wallet buyerWallet = new Wallet(10L, new BigDecimal("2000000.00"));
            when(walletService.getOrCreateWallet(10L)).thenReturn(buyerWallet);

            EscrowTransaction escrow = new EscrowTransaction();
            escrow.setId(50L);
            escrow.setTicketId(100L);
            escrow.setBuyerId(10L);
            escrow.setSellerId(20L);
            escrow.setStatus(EscrowStatus.PENDING);

            when(escrowTransactionRepository.findByTicketId(100L)).thenReturn(Optional.of(escrow));

            RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();
            String viewName = controller.payEscrowWithWallet(100L, buyerPrincipal, redirectAttributes);

            assertThat(viewName).isEqualTo("redirect:/tickets/100");
            assertThat(redirectAttributes.getFlashAttributes()).containsKey("successMessage");
            assertThat(redirectAttributes.getFlashAttributes().get("successMessage").toString())
                    .contains("Thanh toán bằng Ví TicketPass thành công");

            verify(walletService).debit(eq(10L), eq(new BigDecimal("1000000.00")),
                    eq(WalletTransactionType.ESCROW_PAY), eq("ESCROW_50"), any());
            verify(holdFundsUseCase).confirmHeld(eq(null), any());
        }

        @Test
        @DisplayName("Từ chối thanh toán khi số dư ví không đủ (Tài khoản 0đ / Broke Buyer)")
        void testPayEscrowInsufficientBalance() {
            when(userRepository.findByEmail("buyer@test.com")).thenReturn(Optional.of(buyer));
            when(ticketRepository.findById(100L)).thenReturn(Optional.of(ticket));

            Wallet brokeWallet = new Wallet(10L, BigDecimal.ZERO); // 0đ
            when(walletService.getOrCreateWallet(10L)).thenReturn(brokeWallet);

            RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();
            String viewName = controller.payEscrowWithWallet(100L, buyerPrincipal, redirectAttributes);

            assertThat(viewName).isEqualTo("redirect:/tickets/100");
            assertThat(redirectAttributes.getFlashAttributes()).containsKey("errorMessage");
            assertThat(redirectAttributes.getFlashAttributes().get("errorMessage").toString())
                    .contains("Số dư ví không đủ để thanh toán");

            // Không bao giờ được gọi debit hay confirmHeld
            verify(walletService, never()).debit(anyLong(), any(), any(), any(), any());
            verify(holdFundsUseCase, never()).confirmHeld(any(), any());
        }

        @Test
        @DisplayName("Chặn người bán tự mua vé của chính mình")
        void testPayEscrowSelfBuyPrevented() {
            when(userRepository.findByEmail("seller@test.com")).thenReturn(Optional.of(seller));
            when(ticketRepository.findById(100L)).thenReturn(Optional.of(ticket));

            RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();
            String viewName = controller.payEscrowWithWallet(100L, sellerPrincipal, redirectAttributes);

            assertThat(viewName).isEqualTo("redirect:/tickets/100");
            assertThat(redirectAttributes.getFlashAttributes().get("errorMessage"))
                    .isEqualTo("Bạn không thể tự mua vé của chính mình.");
            verify(walletService, never()).debit(anyLong(), any(), any(), any(), any());
        }
    }

    @Nested
    @DisplayName("Nộp cọc bảo đảm 50% bằng Ví (/payment/wallet/pay-deposit)")
    class PayDepositTests {

        @Test
        @DisplayName("Nộp cọc 50% thành công khi người bán có đủ số dư ví")
        void testPayDepositSuccess() {
            when(userRepository.findByEmail("seller@test.com")).thenReturn(Optional.of(seller));
            when(ticketRepository.findById(100L)).thenReturn(Optional.of(ticket));

            Wallet sellerWallet = new Wallet(20L, new BigDecimal("1000000.00")); // Đủ cọc 500k
            when(walletService.getOrCreateWallet(20L)).thenReturn(sellerWallet);

            Deposit deposit = new Deposit();
            deposit.setId(70L);
            deposit.setTicketId(100L);
            deposit.setSellerId(20L);
            deposit.setAmount(new BigDecimal("500000.00"));
            deposit.setStatus(DepositStatus.PENDING);

            when(depositRepository.findByTicketId(100L)).thenReturn(Optional.of(deposit));

            RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();
            String viewName = controller.payDepositWithWallet(100L, sellerPrincipal, redirectAttributes);

            assertThat(viewName).isEqualTo("redirect:/tickets/100");
            assertThat(redirectAttributes.getFlashAttributes()).containsKey("successMessage");
            assertThat(redirectAttributes.getFlashAttributes().get("successMessage").toString())
                    .contains("Khóa cọc 50% (500,000 VNĐ) bằng Ví thành công");

            verify(walletService).debit(eq(20L), eq(new BigDecimal("500000.00")),
                    eq(WalletTransactionType.DEPOSIT_LOCK), eq("DEPOSIT_70"), any());
            verify(lockDepositUseCase).confirmLocked(eq(null), any());
        }

        @Test
        @DisplayName("Chặn khi người mua cố tình nộp cọc cho vé của người khác")
        void testPayDepositNotOwner() {
            when(userRepository.findByEmail("buyer@test.com")).thenReturn(Optional.of(buyer));
            when(ticketRepository.findById(100L)).thenReturn(Optional.of(ticket));

            RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();
            String viewName = controller.payDepositWithWallet(100L, buyerPrincipal, redirectAttributes);

            assertThat(viewName).isEqualTo("redirect:/tickets/100");
            assertThat(redirectAttributes.getFlashAttributes().get("errorMessage"))
                    .isEqualTo("Bạn không phải chủ sở hữu của vé này.");
            verify(walletService, never()).debit(anyLong(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("Từ chối nộp cọc khi người bán không đủ số dư ví")
        void testPayDepositInsufficientBalance() {
            when(userRepository.findByEmail("seller@test.com")).thenReturn(Optional.of(seller));
            when(ticketRepository.findById(100L)).thenReturn(Optional.of(ticket));

            Wallet sellerWallet = new Wallet(20L, new BigDecimal("100000.00")); // Chỉ có 100k, thiếu 400k
            when(walletService.getOrCreateWallet(20L)).thenReturn(sellerWallet);

            RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();
            String viewName = controller.payDepositWithWallet(100L, sellerPrincipal, redirectAttributes);

            assertThat(viewName).isEqualTo("redirect:/tickets/100");
            assertThat(redirectAttributes.getFlashAttributes()).containsKey("errorMessage");
            assertThat(redirectAttributes.getFlashAttributes().get("errorMessage").toString())
                    .contains("Số dư ví không đủ để nộp cọc 50%");
            verify(walletService, never()).debit(anyLong(), any(), any(), any(), any());
        }
    }

    @Nested
    @DisplayName("Thanh toán từ trang chuyển khoản (/payment/wallet/pay-from-transfer)")
    class PayFromTransferTests {

        @Test
        @DisplayName("Thanh toán ký quỹ trực tiếp từ trang chuyển khoản thành công")
        void testPayFromTransferEscrowSuccess() {
            when(userRepository.findByEmail("buyer@test.com")).thenReturn(Optional.of(buyer));

            EscrowTransaction escrow = new EscrowTransaction();
            escrow.setId(101L);
            escrow.setTicketId(100L);
            escrow.setBuyerId(10L);
            escrow.setSalePrice(new BigDecimal("800000.00"));

            when(escrowTransactionRepository.findById(101L)).thenReturn(Optional.of(escrow));

            Wallet buyerWallet = new Wallet(10L, new BigDecimal("1000000.00"));
            when(walletService.getOrCreateWallet(10L)).thenReturn(buyerWallet);

            RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();
            String viewName = controller.payFromTransfer("101", "escrow", buyerPrincipal, redirectAttributes);

            assertThat(viewName).isEqualTo("redirect:/tickets/100");
            assertThat(redirectAttributes.getFlashAttributes().get("successMessage"))
                    .isEqualTo("Thanh toán ký quỹ thành công qua Ví TicketPass!");
            verify(walletService).debit(eq(10L), eq(new BigDecimal("800000.00")),
                    eq(WalletTransactionType.ESCROW_PAY), eq("ESCROW_101"), any());
        }

        @Test
        @DisplayName("Thanh toán cọc bảo đảm trực tiếp từ trang chuyển khoản thành công")
        void testPayFromTransferDepositSuccess() {
            when(userRepository.findByEmail("seller@test.com")).thenReturn(Optional.of(seller));

            Deposit deposit = new Deposit();
            deposit.setId(202L);
            deposit.setTicketId(100L);
            deposit.setSellerId(20L);
            deposit.setAmount(new BigDecimal("400000.00"));

            when(depositRepository.findById(202L)).thenReturn(Optional.of(deposit));

            Wallet sellerWallet = new Wallet(20L, new BigDecimal("500000.00"));
            when(walletService.getOrCreateWallet(20L)).thenReturn(sellerWallet);

            RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();
            String viewName = controller.payFromTransfer("202", "deposit", sellerPrincipal, redirectAttributes);

            assertThat(viewName).isEqualTo("redirect:/tickets/100");
            assertThat(redirectAttributes.getFlashAttributes().get("successMessage"))
                    .isEqualTo("Khóa cọc bảo đảm 50% thành công qua Ví TicketPass!");
            verify(walletService).debit(eq(20L), eq(new BigDecimal("400000.00")),
                    eq(WalletTransactionType.DEPOSIT_LOCK), eq("DEPOSIT_202"), any());
        }

        @Test
        @DisplayName("Từ chối khi người dùng không phải chủ giao dịch ký quỹ")
        void testPayFromTransferEscrowWrongUser() {
            when(userRepository.findByEmail("seller@test.com")).thenReturn(Optional.of(seller));

            EscrowTransaction escrow = new EscrowTransaction();
            escrow.setId(101L);
            escrow.setTicketId(100L);
            escrow.setBuyerId(10L); // buyer là 10, người gọi là 20
            escrow.setSalePrice(new BigDecimal("800000.00"));

            when(escrowTransactionRepository.findById(101L)).thenReturn(Optional.of(escrow));

            RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();
            String viewName = controller.payFromTransfer("101", "escrow", sellerPrincipal, redirectAttributes);

            assertThat(viewName).isEqualTo("redirect:/payment/transfer?ref=101&type=escrow");
            assertThat(redirectAttributes.getFlashAttributes().get("errorMessage").toString())
                    .contains("Bạn không phải người mua");
        }
    }
}
