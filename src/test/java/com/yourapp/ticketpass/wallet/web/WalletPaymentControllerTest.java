package com.yourapp.ticketpass.wallet.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yourapp.ticketpass.shared.dto.ConflictingTicketInfo;
import com.yourapp.ticketpass.shared.exception.BusinessRuleViolationException;
import com.yourapp.ticketpass.user.domain.User;
import com.yourapp.ticketpass.user.domain.UserRepository;
import com.yourapp.ticketpass.wallet.application.WalletCheckoutUseCase;
import com.yourapp.ticketpass.wallet.application.WalletCheckoutUseCase.CheckoutResult;
import java.math.BigDecimal;
import java.security.Principal;
import java.time.Instant;
import java.util.List;
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

@ExtendWith(MockitoExtension.class)
@DisplayName("WalletPaymentController Test - MVC Thin Controller ủy quyền WalletCheckoutUseCase")
class WalletPaymentControllerTest {

    @Mock
    private WalletCheckoutUseCase walletCheckoutUseCase;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private WalletPaymentController controller;

    private User buyer;
    private User seller;
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
    }

    @Nested
    @DisplayName("POST /payment/wallet/pay-escrow")
    class PayEscrowTests {

        @Test
        @DisplayName("Thành công -> redirect /tickets/{id} kèm successMessage")
        void testPayEscrowSuccess() {
            when(userRepository.findByEmail("buyer@test.com")).thenReturn(Optional.of(buyer));
            when(walletCheckoutUseCase.payEscrowWithWallet(100L, 10L, false))
                    .thenReturn(CheckoutResult.success(100L, new BigDecimal("1000000.00")));

            RedirectAttributesModelMap attrs = new RedirectAttributesModelMap();
            String view = controller.payEscrowWithWallet(100L, buyerPrincipal, attrs);

            assertThat(view).isEqualTo("redirect:/tickets/100");
            assertThat(attrs.getFlashAttributes().get("successMessage").toString())
                    .contains("Thanh toán bằng Ví TicketPass thành công");
            verify(walletCheckoutUseCase).payEscrowWithWallet(100L, 10L, false);
        }

        @Test
        @DisplayName("Không đủ số dư / vi phạm nghiệp vụ -> redirect /tickets/{id} kèm errorMessage sạch")
        void testPayEscrowBusinessError() {
            when(userRepository.findByEmail("buyer@test.com")).thenReturn(Optional.of(buyer));
            when(walletCheckoutUseCase.payEscrowWithWallet(100L, 10L, false))
                    .thenThrow(new BusinessRuleViolationException("Số dư ví không đủ để thanh toán."));

            RedirectAttributesModelMap attrs = new RedirectAttributesModelMap();
            String view = controller.payEscrowWithWallet(100L, buyerPrincipal, attrs);

            assertThat(view).isEqualTo("redirect:/tickets/100");
            assertThat(attrs.getFlashAttributes().get("errorMessage"))
                    .isEqualTo("Số dư ví không đủ để thanh toán.");
        }

        @Test
        @DisplayName("Trùng giờ chưa confirm -> redirect ?overlapWarning=true&method=wallet")
        void testPayEscrowOverlapWarning() {
            when(userRepository.findByEmail("buyer@test.com")).thenReturn(Optional.of(buyer));
            ConflictingTicketInfo conflict = new ConflictingTicketInfo(
                    99L, "Vé phim cũ", "Dune", "CGV", "AWAITING_USE", "Đang giữ vé",
                    Instant.now(), Instant.now().plusSeconds(7200), false, "13:00 - 15:00"
            );
            when(walletCheckoutUseCase.payEscrowWithWallet(100L, 10L, false))
                    .thenReturn(CheckoutResult.overlapWarning(100L, List.of(conflict)));

            RedirectAttributesModelMap attrs = new RedirectAttributesModelMap();
            String view = controller.payEscrowWithWallet(100L, false, buyerPrincipal, attrs);

            assertThat(view).isEqualTo("redirect:/tickets/100?overlapWarning=true&method=wallet");
            assertThat(attrs.getFlashAttributes().get("overlapWarning")).isEqualTo(true);
            assertThat(attrs.getFlashAttributes().get("pendingPaymentMethod")).isEqualTo("WALLET");
        }
    }

    @Nested
    @DisplayName("POST /payment/wallet/pay-deposit")
    class PayDepositTests {

        @Test
        @DisplayName("Khóa cọc 50% thành công -> redirect /tickets/{id} kèm successMessage")
        void testPayDepositSuccess() {
            when(userRepository.findByEmail("seller@test.com")).thenReturn(Optional.of(seller));
            when(walletCheckoutUseCase.payDepositWithWallet(100L, 20L))
                    .thenReturn(CheckoutResult.success(100L, new BigDecimal("500000.00")));

            RedirectAttributesModelMap attrs = new RedirectAttributesModelMap();
            String view = controller.payDepositWithWallet(100L, sellerPrincipal, attrs);

            assertThat(view).isEqualTo("redirect:/tickets/100");
            assertThat(attrs.getFlashAttributes().get("successMessage").toString())
                    .contains("Khóa cọc 50%");
        }

        @Test
        @DisplayName("Khóa cọc bị từ chối -> redirect /tickets/{id} kèm errorMessage")
        void testPayDepositRejected() {
            when(userRepository.findByEmail("buyer@test.com")).thenReturn(Optional.of(buyer));
            when(walletCheckoutUseCase.payDepositWithWallet(100L, 10L))
                    .thenThrow(new BusinessRuleViolationException("Bạn không phải chủ sở hữu của vé này."));

            RedirectAttributesModelMap attrs = new RedirectAttributesModelMap();
            String view = controller.payDepositWithWallet(100L, buyerPrincipal, attrs);

            assertThat(view).isEqualTo("redirect:/tickets/100");
            assertThat(attrs.getFlashAttributes().get("errorMessage"))
                    .isEqualTo("Bạn không phải chủ sở hữu của vé này.");
        }
    }

    @Nested
    @DisplayName("POST /payment/wallet/pay-from-transfer")
    class PayFromTransferTests {

        @Test
        @DisplayName("Thanh toán ký quỹ thành công từ trang chuyển khoản")
        void testPayFromTransferEscrowSuccess() {
            when(userRepository.findByEmail("buyer@test.com")).thenReturn(Optional.of(buyer));
            when(walletCheckoutUseCase.payFromTransfer(101L, "escrow", 10L, false))
                    .thenReturn(CheckoutResult.success(100L, new BigDecimal("800000.00")));

            RedirectAttributesModelMap attrs = new RedirectAttributesModelMap();
            String view = controller.payFromTransfer("101", "escrow", buyerPrincipal, attrs);

            assertThat(view).isEqualTo("redirect:/tickets/100");
            assertThat(attrs.getFlashAttributes().get("successMessage"))
                    .isEqualTo("Thanh toán ký quỹ thành công qua Ví TicketPass!");
        }

        @Test
        @DisplayName("Trùng giờ ở nút pay-from-transfer (escrow) -> redirect cảnh báo trùng giờ, không trừ ví")
        void testPayFromTransferEscrowOverlapWarning() {
            when(userRepository.findByEmail("buyer@test.com")).thenReturn(Optional.of(buyer));
            ConflictingTicketInfo conflict = new ConflictingTicketInfo(
                    55L, "Vé trùng giờ", "Bus", "Bến xe", "AWAITING_USE", "Đang giữ vé",
                    Instant.now(), Instant.now().plusSeconds(3600), false, "18:00 - 20:00"
            );
            when(walletCheckoutUseCase.payFromTransfer(101L, "escrow", 10L, false))
                    .thenReturn(CheckoutResult.overlapWarning(100L, List.of(conflict)));

            RedirectAttributesModelMap attrs = new RedirectAttributesModelMap();
            String view = controller.payFromTransfer("101", "escrow", false, buyerPrincipal, attrs);

            assertThat(view).isEqualTo("redirect:/tickets/100?overlapWarning=true&method=wallet");
            assertThat(attrs.getFlashAttributes().get("overlapWarning")).isEqualTo(true);
        }

        @Test
        @DisplayName("Thanh toán cọc thành công từ trang chuyển khoản")
        void testPayFromTransferDepositSuccess() {
            when(userRepository.findByEmail("seller@test.com")).thenReturn(Optional.of(seller));
            when(walletCheckoutUseCase.payFromTransfer(202L, "deposit", 20L, false))
                    .thenReturn(CheckoutResult.success(100L, new BigDecimal("400000.00")));

            RedirectAttributesModelMap attrs = new RedirectAttributesModelMap();
            String view = controller.payFromTransfer("202", "deposit", sellerPrincipal, attrs);

            assertThat(view).isEqualTo("redirect:/tickets/100");
            assertThat(attrs.getFlashAttributes().get("successMessage"))
                    .isEqualTo("Khóa cọc bảo đảm 50% thành công qua Ví TicketPass!");
        }

        @Test
        @DisplayName("Sai chủ sở hữu hoặc lỗi nghiệp vụ -> redirect lại /payment/transfer kèm errorMessage")
        void testPayFromTransferErrorRedirectsBackToTransferPage() {
            when(userRepository.findByEmail("seller@test.com")).thenReturn(Optional.of(seller));
            when(walletCheckoutUseCase.payFromTransfer(101L, "escrow", 20L, false))
                    .thenThrow(new BusinessRuleViolationException("Bạn không phải người mua của giao dịch này."));

            RedirectAttributesModelMap attrs = new RedirectAttributesModelMap();
            String view = controller.payFromTransfer("101", "escrow", sellerPrincipal, attrs);

            assertThat(view).isEqualTo("redirect:/payment/transfer?ref=101&type=escrow");
            assertThat(attrs.getFlashAttributes().get("errorMessage").toString())
                    .contains("Bạn không phải người mua");
        }
    }
}
