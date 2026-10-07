package com.yourapp.ticketpass.wallet.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.security.Principal;
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
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import com.yourapp.ticketpass.shared.exception.ResourceNotFoundException;
import com.yourapp.ticketpass.user.domain.User;
import com.yourapp.ticketpass.user.domain.UserRepository;
import com.yourapp.ticketpass.wallet.application.WalletService;
import com.yourapp.ticketpass.wallet.domain.Wallet;
import com.yourapp.ticketpass.wallet.domain.WalletStatus;
import com.yourapp.ticketpass.wallet.domain.WalletTransaction;
import com.yourapp.ticketpass.wallet.domain.WalletTransactionType;
import com.yourapp.ticketpass.wallet.web.dto.TopUpRequest;
import com.yourapp.ticketpass.wallet.web.dto.WalletResponse;

/**
 * Unit Test cho {@link WalletController} — Task C14.
 *
 * <p>Kiểm thử trang giao diện Ví cá nhân (/wallet) và tính năng nạp tiền nhanh (/wallet/topup),
 * bao gồm các nhánh thành công, lỗi validation, không xác thực hoặc lỗi hệ thống.</p>
 *
 * <p>Module: wallet</p>
 * <p>Chủ sở hữu: Người C (Dev C)</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WalletController Test - Điều khiển hiển thị và nạp tiền Ví")
class WalletControllerTest {

    @Mock
    private WalletService walletService;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private WalletController walletController;

    private Principal principal;
    private User testUser;
    private Wallet testWallet;

    @BeforeEach
    void setUp() {
        principal = () -> "buyer@test.com";

        testUser = new User();
        testUser.setId(10L);
        testUser.setEmail("buyer@test.com");

        testWallet = new Wallet(10L, new BigDecimal("2500000.00"));
        testWallet.setId(1L);
        testWallet.setStatus(WalletStatus.ACTIVE);
    }

    @Nested
    @DisplayName("Hiển thị giao diện Ví cá nhân (GET /wallet)")
    class ShowWalletTests {

        @Test
        @DisplayName("showWallet hiển thị thông tin ví và lịch sử biến động thành công")
        void testShowWalletSuccess() {
            when(userRepository.findByEmail("buyer@test.com")).thenReturn(Optional.of(testUser));
            when(walletService.getOrCreateWallet(10L)).thenReturn(testWallet);

            WalletTransaction tx1 = new WalletTransaction(
                    1L,
                    new BigDecimal("1000000.00"),
                    BigDecimal.ZERO,
                    new BigDecimal("1000000.00"),
                    WalletTransactionType.TOP_UP,
                    "TOPUP_1",
                    "Nạp tiền ví"
            );

            WalletTransaction tx2 = new WalletTransaction(
                    1L,
                    new BigDecimal("500000.00"),
                    new BigDecimal("1000000.00"),
                    new BigDecimal("500000.00"),
                    WalletTransactionType.ESCROW_PAY,
                    "ESCROW_1",
                    "Thanh toán ký quỹ"
            );

            when(walletService.getTransactionHistory(10L, 100)).thenReturn(List.of(tx1, tx2));

            Model model = new ConcurrentModel();
            String viewName = walletController.showWallet(principal, model);

            assertThat(viewName).isEqualTo("wallet/index");
            assertThat(model.getAttribute("wallet")).isInstanceOf(WalletResponse.class);
            assertThat(model.getAttribute("transactions")).isNotNull();
            assertThat(model.getAttribute("totalTopUp")).isEqualTo(new BigDecimal("1000000.00"));
            assertThat(model.getAttribute("totalExpense")).isEqualTo(new BigDecimal("500000.00"));
        }

        @Test
        @DisplayName("showWallet ném ngoại lệ khi Principal là null (chưa đăng nhập)")
        void testShowWalletUnauthenticated() {
            Model model = new ConcurrentModel();
            assertThatThrownBy(() -> walletController.showWallet(null, model))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("đăng nhập");
        }

        @Test
        @DisplayName("showWallet ném ngoại lệ khi không tìm thấy user trong database")
        void testShowWalletUserNotFound() {
            when(userRepository.findByEmail("buyer@test.com")).thenReturn(Optional.empty());

            Model model = new ConcurrentModel();
            assertThatThrownBy(() -> walletController.showWallet(principal, model))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("Nạp tiền nhanh vào Ví (POST /wallet/topup)")
    class TopUpTests {

        @Test
        @DisplayName("handleTopUp nạp tiền thành công và redirect về /wallet kèm flash message")
        void testHandleTopUpSuccess() {
            when(userRepository.findByEmail("buyer@test.com")).thenReturn(Optional.of(testUser));

            TopUpRequest request = new TopUpRequest();
            request.setAmount(new BigDecimal("1000000.00"));
            request.setNote("Sandbox Top-up 1 Triệu");

            BindingResult bindingResult = new BeanPropertyBindingResult(request, "topUpRequest");
            RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

            String viewName = walletController.handleTopUp(request, bindingResult, principal, redirectAttributes);

            assertThat(viewName).isEqualTo("redirect:/wallet");
            assertThat(redirectAttributes.getFlashAttributes()).containsKey("successMessage");
            verify(walletService).topUp(10L, new BigDecimal("1000000.00"), "Sandbox Top-up 1 Triệu");
        }

        @Test
        @DisplayName("handleTopUp nạp tiền lẻ thành công (13k, 17k) và chuyển hướng kèm thông báo")
        void testHandleTopUpSuccess_OddAmount() {
            when(userRepository.findByEmail("buyer@test.com")).thenReturn(Optional.of(testUser));

            TopUpRequest request = new TopUpRequest();
            request.setAmount(new BigDecimal("17000.00"));
            request.setNote("Nạp tiền lẻ 17k");

            BindingResult bindingResult = new BeanPropertyBindingResult(request, "topUpRequest");
            RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

            String viewName = walletController.handleTopUp(request, bindingResult, principal, redirectAttributes);

            assertThat(viewName).isEqualTo("redirect:/wallet");
            assertThat(redirectAttributes.getFlashAttributes()).containsKey("successMessage");
            assertThat(redirectAttributes.getFlashAttributes().get("successMessage").toString())
                    .contains("17,000 VNĐ");
            verify(walletService).topUp(10L, new BigDecimal("17000.00"), "Nạp tiền lẻ 17k");
        }

        @Test
        @DisplayName("handleTopUp redirect với errorMessage khi request vi phạm validation")
        void testHandleTopUpValidationError() {
            TopUpRequest request = new TopUpRequest();
            request.setAmount(new BigDecimal("-50000")); // Số tiền âm vi phạm validation

            BindingResult bindingResult = new BeanPropertyBindingResult(request, "topUpRequest");
            bindingResult.rejectValue("amount", "min", "Số tiền nạp tối thiểu là 10.000 VNĐ.");

            RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

            String viewName = walletController.handleTopUp(request, bindingResult, principal, redirectAttributes);

            assertThat(viewName).isEqualTo("redirect:/wallet");
            assertThat(redirectAttributes.getFlashAttributes()).containsKey("errorMessage");
            assertThat(redirectAttributes.getFlashAttributes().get("errorMessage"))
                    .isEqualTo("Số tiền nạp tối thiểu là 10.000 VNĐ.");
            verify(walletService, never()).topUp(anyLong(), any(), any());
        }

        @Test
        @DisplayName("handleTopUp bắt ngoại lệ khi walletService gặp lỗi nạp tiền")
        void testHandleTopUpServiceException() {
            when(userRepository.findByEmail("buyer@test.com")).thenReturn(Optional.of(testUser));
            doThrow(new IllegalStateException("Ví đã bị khóa"))
                    .when(walletService).topUp(anyLong(), any(), any());

            TopUpRequest request = new TopUpRequest();
            request.setAmount(new BigDecimal("500000.00"));

            BindingResult bindingResult = new BeanPropertyBindingResult(request, "topUpRequest");
            RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

            String viewName = walletController.handleTopUp(request, bindingResult, principal, redirectAttributes);

            assertThat(viewName).isEqualTo("redirect:/wallet");
            assertThat(redirectAttributes.getFlashAttributes()).containsKey("errorMessage");
            assertThat(redirectAttributes.getFlashAttributes().get("errorMessage").toString())
                    .contains("Ví đã bị khóa");
        }
    }
}
