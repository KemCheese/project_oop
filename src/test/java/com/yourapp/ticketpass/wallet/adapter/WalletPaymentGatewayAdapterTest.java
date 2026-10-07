package com.yourapp.ticketpass.wallet.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.yourapp.ticketpass.payment.domain.EscrowTransaction;
import com.yourapp.ticketpass.payment.domain.EscrowTransactionRepository;
import com.yourapp.ticketpass.payment.domain.PaymentRequest;
import com.yourapp.ticketpass.payment.domain.PaymentResult;
import com.yourapp.ticketpass.wallet.application.WalletService;
import com.yourapp.ticketpass.wallet.domain.WalletTransactionType;

/**
 * Unit Test cho {@link WalletPaymentGatewayAdapter} — Task C14.
 *
 * <p>Kiểm thử việc chuyển đổi hợp đồng PaymentGateway của Dev B sang nghiệp vụ
 * ghi nợ/ghi có của Ví điện tử TicketPass (Dev C), bảo đảm tính tương thích hoàn toàn.</p>
 *
 * <p>Module: wallet</p>
 * <p>Chủ sở hữu: Người C (Dev C)</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WalletPaymentGatewayAdapter Test - Bộ điều hợp cổng thanh toán Ví")
class WalletPaymentGatewayAdapterTest {

    @Mock
    private WalletService walletService;

    @Mock
    private EscrowTransactionRepository escrowTransactionRepository;

    @InjectMocks
    private WalletPaymentGatewayAdapter adapter;

    @Test
    @DisplayName("gatewayName() trả về định danh WALLET")
    void testGatewayName() {
        assertThat(adapter.gatewayName()).isEqualTo("WALLET");
    }

    @Nested
    @DisplayName("Khởi tạo thanh toán & Xác nhận Callback")
    class InitAndCallbackTests {

        @Test
        @DisplayName("initPayment tạo PaymentResult thành công và điều hướng sang /wallet")
        void testInitPayment() {
            PaymentRequest request = new PaymentRequest();
            request.setReferenceId("101");
            request.setAmount(new BigDecimal("500000.00"));

            PaymentResult result = adapter.initPayment(request);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getGatewayTransactionId()).startsWith("WALLET_101_");
            assertThat(result.getGatewayStatus()).isEqualTo("PENDING_WALLET");
            assertThat(result.getPayUrl()).isEqualTo("/wallet");
        }

        @Test
        @DisplayName("verifyCallback trả về kết quả WALLET_VERIFIED")
        void testVerifyCallback() {
            Map<String, String> params = Map.of("referenceId", "TX_999");

            PaymentResult result = adapter.verifyCallback(params);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getGatewayTransactionId()).isEqualTo("TX_999");
            assertThat(result.getGatewayStatus()).isEqualTo("WALLET_VERIFIED");
        }

        @Test
        @DisplayName("capture ghi nhận trạng thái CAPTURED")
        void testCapture() {
            PaymentResult result = adapter.capture("TX_WALLET_123", new BigDecimal("300000"));

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getGatewayTransactionId()).isEqualTo("TX_WALLET_123");
            assertThat(result.getGatewayStatus()).isEqualTo("CAPTURED");
        }
    }

    @Nested
    @DisplayName("Hoàn tiền vào ví (Refund)")
    class RefundTests {

        @Test
        @DisplayName("refund thành công khi tìm thấy EscrowTransaction tương ứng với gatewayTransactionId")
        void testRefundWithExistingEscrow() {
            String gatewayTxId = "ESCROW_GATEWAY_789";
            BigDecimal amount = new BigDecimal("750000.00");
            String reason = "Người bán không giao vé";

            EscrowTransaction escrow = new EscrowTransaction();
            escrow.setId(10L);
            escrow.setBuyerId(42L);
            escrow.setGatewayTransactionId(gatewayTxId);

            when(escrowTransactionRepository.findByGatewayTransactionId(gatewayTxId))
                    .thenReturn(Optional.of(escrow));

            PaymentResult result = adapter.refund(gatewayTxId, amount, reason);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getGatewayStatus()).isEqualTo("REFUNDED");
            verify(walletService).credit(
                    eq(42L),
                    eq(amount),
                    eq(WalletTransactionType.ESCROW_REFUND),
                    eq(gatewayTxId),
                    eq("Hoàn tiền ký quỹ: " + reason)
            );
        }

        @Test
        @DisplayName("refund an toàn khi không tìm thấy EscrowTransaction, không gọi walletService")
        void testRefundWithoutEscrow() {
            String gatewayTxId = "NON_EXISTING_TX";
            BigDecimal amount = new BigDecimal("200000.00");

            when(escrowTransactionRepository.findByGatewayTransactionId(gatewayTxId))
                    .thenReturn(Optional.empty());

            PaymentResult result = adapter.refund(gatewayTxId, amount, "Test");

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getGatewayStatus()).isEqualTo("REFUNDED");
            verify(walletService, never()).credit(any(), any(), any(), any(), any());
        }
    }

    @Nested
    @DisplayName("Giải ngân và chi trả vào ví (Payout)")
    class PayoutTests {

        @Test
        @DisplayName("payout tiêu chuẩn cho người bán giải ngân tiền vé")
        void testPayoutStandard() {
            Long sellerId = 55L;
            BigDecimal amount = new BigDecimal("1200000.00");
            String ref = "ESCROW_123";

            PaymentResult result = adapter.payout(sellerId, amount, ref);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getGatewayStatus()).isEqualTo("PAID");
            verify(walletService).credit(
                    eq(sellerId),
                    eq(amount),
                    eq(WalletTransactionType.PAYOUT_RECEIVE),
                    eq(ref),
                    eq("Nhận tiền giải ngân bán vé / hoàn cọc bảo đảm (" + ref + ")")
            );
        }

        @Test
        @DisplayName("payout hoàn cọc bảo đảm khi reference chứa REFUND_DEPOSIT")
        void testPayoutDepositRefund() {
            Long sellerId = 55L;
            BigDecimal amount = new BigDecimal("600000.00");
            String ref = "REFUND_DEPOSIT_456";

            PaymentResult result = adapter.payout(sellerId, amount, ref);

            assertThat(result.isSuccess()).isTrue();
            verify(walletService).credit(
                    eq(sellerId),
                    eq(amount),
                    eq(WalletTransactionType.DEPOSIT_REFUND),
                    eq(ref),
                    eq("Hoàn cọc bảo đảm 50% (" + ref + ")")
            );
        }

        @Test
        @DisplayName("payout bồi thường khiếu nại khi reference chứa BUYER_COMPENSATION")
        void testPayoutBuyerCompensation() {
            Long buyerId = 77L;
            BigDecimal amount = new BigDecimal("400000.00");
            String ref = "BUYER_COMPENSATION_DISPUTE_88";

            PaymentResult result = adapter.payout(buyerId, amount, ref);

            assertThat(result.isSuccess()).isTrue();
            verify(walletService).credit(
                    eq(buyerId),
                    eq(amount),
                    eq(WalletTransactionType.COMPENSATION_RECEIVE),
                    eq(ref),
                    eq("Nhận tiền bồi thường khiếu nại (" + ref + ")")
            );
        }
    }
}
