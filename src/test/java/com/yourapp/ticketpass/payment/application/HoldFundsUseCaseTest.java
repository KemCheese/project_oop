package com.yourapp.ticketpass.payment.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.yourapp.ticketpass.payment.domain.EscrowStatus;
import com.yourapp.ticketpass.payment.domain.EscrowTransaction;
import com.yourapp.ticketpass.payment.domain.EscrowTransactionRepository;
import com.yourapp.ticketpass.payment.domain.PaymentGateway;
import com.yourapp.ticketpass.payment.domain.PaymentRequest;
import com.yourapp.ticketpass.payment.domain.PaymentResult;
import com.yourapp.ticketpass.shared.dto.CommissionBreakdown;
import com.yourapp.ticketpass.shared.exception.BusinessRuleViolationException;
import com.yourapp.ticketpass.shared.exception.ResourceNotFoundException;
import com.yourapp.ticketpass.ticket.application.TransferTicketService;

@ExtendWith(MockitoExtension.class)
class HoldFundsUseCaseTest {

    @Mock
    private EscrowTransactionRepository escrowTransactionRepository;

    @Mock
    private PaymentGateway paymentGateway;

    @Mock
    private CommissionCalculatorService commissionCalculatorService;

    @Mock
    private TransferTicketService transferTicketService;

    @InjectMocks
    private HoldFundsUseCase holdFundsUseCase;

    private final Long ticketId = 100L;
    private final Long buyerId = 10L;
    private final Long sellerId = 20L;
    private final BigDecimal salePrice = new BigDecimal("500000");

    private CommissionBreakdown sampleCommission;

    @BeforeEach
    void setUp() {
        sampleCommission = new CommissionBreakdown();
        sampleCommission.setSalePrice(salePrice);
        sampleCommission.setTierCode("UNDER_1M");
        sampleCommission.setTierRatePercent(new BigDecimal("7"));
        sampleCommission.setRawCommission(new BigDecimal("35000"));
        sampleCommission.setTotalCommission(new BigDecimal("35000"));
        sampleCommission.setBuyerShare(new BigDecimal("17500"));
        sampleCommission.setSellerShare(new BigDecimal("17500"));
    }

    @Nested
    @DisplayName("initEscrowPayment Tests")
    class InitEscrowPaymentTests {

        @Test
        @DisplayName("Khởi tạo thành công: tạo escrow PENDING và trả về PaymentResult có payUrl")
        void initEscrowPayment_Success() {
            when(escrowTransactionRepository.findByTicketId(ticketId)).thenReturn(Optional.empty());
            when(commissionCalculatorService.calculate(salePrice)).thenReturn(sampleCommission);
            when(paymentGateway.gatewayName()).thenReturn("MOMO_TEST");

            when(escrowTransactionRepository.save(any(EscrowTransaction.class))).thenAnswer(invocation -> {
                EscrowTransaction entity = invocation.getArgument(0);
                if (entity.getId() == null) {
                    entity.setId(1L);
                }
                return entity;
            });

            PaymentResult gatewayResult = new PaymentResult();
            gatewayResult.setSuccess(true);
            gatewayResult.setPayUrl("https://test-payment.momo.vn/pay/123");
            when(paymentGateway.initPayment(any(PaymentRequest.class))).thenReturn(gatewayResult);

            PaymentResult result = holdFundsUseCase.initEscrowPayment(ticketId, buyerId, sellerId, salePrice);

            assertNotNull(result);
            assertTrue(result.isSuccess());
            assertEquals("https://test-payment.momo.vn/pay/123", result.getPayUrl());

            ArgumentCaptor<PaymentRequest> reqCaptor = ArgumentCaptor.forClass(PaymentRequest.class);
            verify(paymentGateway).initPayment(reqCaptor.capture());
            PaymentRequest sentReq = reqCaptor.getValue();
            assertEquals("1", sentReq.getReferenceId());
            assertEquals(buyerId, sentReq.getPayerUserId());
            assertEquals(salePrice, sentReq.getAmount());
            assertTrue(sentReq.getOrderInfo().contains(String.valueOf(ticketId)));

            ArgumentCaptor<EscrowTransaction> txCaptor = ArgumentCaptor.forClass(EscrowTransaction.class);
            verify(escrowTransactionRepository, atLeastOnce()).save(txCaptor.capture());
            EscrowTransaction savedTx = txCaptor.getValue();
            assertEquals(ticketId, savedTx.getTicketId());
            assertEquals(buyerId, savedTx.getBuyerId());
            assertEquals(sellerId, savedTx.getSellerId());
            assertEquals(EscrowStatus.PENDING, savedTx.getStatus());
            assertEquals(BigDecimal.ZERO, savedTx.getAmountHeld());
            assertEquals(new BigDecimal("17500"), savedTx.getBuyerCommission());
            assertEquals(new BigDecimal("17500"), savedTx.getSellerCommission());
            assertEquals(new BigDecimal("35000"), savedTx.getTotalCommission());
            assertEquals("UNDER_1M", savedTx.getTierCode());
            assertEquals("MOMO_TEST", savedTx.getGatewayName());
        }

        @Test
        @DisplayName("Validation: Tham số null ném BusinessRuleViolationException")
        void initEscrowPayment_NullParams_ThrowsException() {
            assertThrows(BusinessRuleViolationException.class,
                    () -> holdFundsUseCase.initEscrowPayment(null, buyerId, sellerId, salePrice));
            assertThrows(BusinessRuleViolationException.class,
                    () -> holdFundsUseCase.initEscrowPayment(ticketId, null, sellerId, salePrice));
            assertThrows(BusinessRuleViolationException.class,
                    () -> holdFundsUseCase.initEscrowPayment(ticketId, buyerId, null, salePrice));
            assertThrows(BusinessRuleViolationException.class,
                    () -> holdFundsUseCase.initEscrowPayment(ticketId, buyerId, sellerId, null));
        }

        @Test
        @DisplayName("Validation: Giá bán <= 0 ném BusinessRuleViolationException")
        void initEscrowPayment_ZeroOrNegativeSalePrice_ThrowsException() {
            assertThrows(BusinessRuleViolationException.class,
                    () -> holdFundsUseCase.initEscrowPayment(ticketId, buyerId, sellerId, BigDecimal.ZERO));
            assertThrows(BusinessRuleViolationException.class,
                    () -> holdFundsUseCase.initEscrowPayment(ticketId, buyerId, sellerId, new BigDecimal("-100000")));
        }

        @Test
        @DisplayName("Validation: Người mua tự mua vé của mình (buyerId == sellerId) ném BusinessRuleViolationException")
        void initEscrowPayment_BuyerEqualsSeller_ThrowsException() {
            assertThrows(BusinessRuleViolationException.class,
                    () -> holdFundsUseCase.initEscrowPayment(ticketId, buyerId, buyerId, salePrice));
        }

        @Test
        @DisplayName("Chặn khi vé đã có escrow đang hiệu lực (PENDING hoặc HELD)")
        void initEscrowPayment_AlreadyActiveEscrow_ThrowsException() {
            EscrowTransaction existingPending = new EscrowTransaction();
            existingPending.setId(10L);
            existingPending.setStatus(EscrowStatus.PENDING);
            when(escrowTransactionRepository.findByTicketId(ticketId)).thenReturn(Optional.of(existingPending));

            assertThrows(BusinessRuleViolationException.class,
                    () -> holdFundsUseCase.initEscrowPayment(ticketId, buyerId, sellerId, salePrice));

            EscrowTransaction existingHeld = new EscrowTransaction();
            existingHeld.setId(11L);
            existingHeld.setStatus(EscrowStatus.HELD);
            when(escrowTransactionRepository.findByTicketId(ticketId)).thenReturn(Optional.of(existingHeld));

            assertThrows(BusinessRuleViolationException.class,
                    () -> holdFundsUseCase.initEscrowPayment(ticketId, buyerId, sellerId, salePrice));
        }

        @Test
        @DisplayName("Cho phép thử lại khi escrow trước đó ở trạng thái FAILED (tạo bản ghi mới)")
        void initEscrowPayment_ExistingFailedEscrow_CanRetry() {
            EscrowTransaction failedTx = new EscrowTransaction();
            failedTx.setId(99L);
            failedTx.setTicketId(ticketId);
            failedTx.setStatus(EscrowStatus.FAILED);
            failedTx.setFailureReason("Lỗi trước đó");
            when(escrowTransactionRepository.findByTicketId(ticketId)).thenReturn(Optional.of(failedTx));
            when(commissionCalculatorService.calculate(salePrice)).thenReturn(sampleCommission);
            when(paymentGateway.gatewayName()).thenReturn("MOMO_TEST");
            when(escrowTransactionRepository.save(any(EscrowTransaction.class))).thenAnswer(i -> i.getArgument(0));

            PaymentResult gatewayResult = new PaymentResult();
            gatewayResult.setSuccess(true);
            gatewayResult.setPayUrl("https://test-payment.momo.vn/pay/retry");
            when(paymentGateway.initPayment(any(PaymentRequest.class))).thenReturn(gatewayResult);

            PaymentResult result = holdFundsUseCase.initEscrowPayment(ticketId, buyerId, sellerId, salePrice);

            assertTrue(result.isSuccess());
            ArgumentCaptor<EscrowTransaction> txCaptor = ArgumentCaptor.forClass(EscrowTransaction.class);
            verify(escrowTransactionRepository).save(txCaptor.capture());
            EscrowTransaction savedTx = txCaptor.getValue();
            assertEquals(EscrowStatus.PENDING, savedTx.getStatus());
            assertNull(savedTx.getFailureReason());
            assertEquals(buyerId, savedTx.getBuyerId());
        }

        @Test
        @DisplayName("Cổng thanh toán từ chối: Cập nhật status FAILED và lưu failureReason")
        void initEscrowPayment_GatewayReturnsFailure() {
            when(escrowTransactionRepository.findByTicketId(ticketId)).thenReturn(Optional.empty());
            when(commissionCalculatorService.calculate(salePrice)).thenReturn(sampleCommission);
            when(paymentGateway.gatewayName()).thenReturn("MOMO_TEST");
            when(escrowTransactionRepository.save(any(EscrowTransaction.class))).thenAnswer(i -> {
                EscrowTransaction tx = i.getArgument(0);
                if (tx.getId() == null) tx.setId(1L);
                return tx;
            });

            PaymentResult failResult = new PaymentResult();
            failResult.setSuccess(false);
            failResult.setMessage("Thẻ không đủ số dư");
            when(paymentGateway.initPayment(any(PaymentRequest.class))).thenReturn(failResult);

            PaymentResult result = holdFundsUseCase.initEscrowPayment(ticketId, buyerId, sellerId, salePrice);

            assertNotNull(result);
            assertFalse(result.isSuccess());

            ArgumentCaptor<EscrowTransaction> txCaptor = ArgumentCaptor.forClass(EscrowTransaction.class);
            verify(escrowTransactionRepository, times(2)).save(txCaptor.capture());
            EscrowTransaction finalSaved = txCaptor.getAllValues().get(1);
            assertEquals(EscrowStatus.FAILED, finalSaved.getStatus());
            assertEquals("Thẻ không đủ số dư", finalSaved.getFailureReason());
        }

        @Test
        @DisplayName("Cổng thanh toán ném ngoại lệ mạng: Đánh dấu FAILED và ném BusinessRuleViolationException")
        void initEscrowPayment_GatewayException_ThrowsException() {
            when(escrowTransactionRepository.findByTicketId(ticketId)).thenReturn(Optional.empty());
            when(commissionCalculatorService.calculate(salePrice)).thenReturn(sampleCommission);
            when(paymentGateway.gatewayName()).thenReturn("MOMO_TEST");
            when(escrowTransactionRepository.save(any(EscrowTransaction.class))).thenAnswer(i -> {
                EscrowTransaction tx = i.getArgument(0);
                if (tx.getId() == null) tx.setId(1L);
                return tx;
            });

            when(paymentGateway.initPayment(any(PaymentRequest.class)))
                    .thenThrow(new RuntimeException("Connection timeout"));

            assertThrows(BusinessRuleViolationException.class,
                    () -> holdFundsUseCase.initEscrowPayment(ticketId, buyerId, sellerId, salePrice));

            ArgumentCaptor<EscrowTransaction> txCaptor = ArgumentCaptor.forClass(EscrowTransaction.class);
            verify(escrowTransactionRepository, times(2)).save(txCaptor.capture());
            EscrowTransaction finalSaved = txCaptor.getAllValues().get(1);
            assertEquals(EscrowStatus.FAILED, finalSaved.getStatus());
            assertTrue(finalSaved.getFailureReason().contains("Connection timeout"));
        }
    }

    @Nested
    @DisplayName("confirmHeld Tests")
    class ConfirmHeldTests {

        @Test
        @DisplayName("Xác nhận thành công: chuyển sang HELD, gán amountHeld và gọi markPaymentEscrowed")
        void confirmHeld_Success() {
            Map<String, String> callbackParams = new HashMap<>();
            callbackParams.put("orderId", "1");
            callbackParams.put("transId", "momo_tx_999");
            callbackParams.put("resultCode", "0");
            callbackParams.put("signature", "valid_sig");

            PaymentResult verifyResult = new PaymentResult();
            verifyResult.setSuccess(true);
            verifyResult.setGatewayTransactionId("momo_tx_999");
            when(paymentGateway.verifyCallback(callbackParams)).thenReturn(verifyResult);

            EscrowTransaction tx = new EscrowTransaction();
            tx.setId(1L);
            tx.setTicketId(ticketId);
            tx.setBuyerId(buyerId);
            tx.setSellerId(sellerId);
            tx.setSalePrice(salePrice);
            tx.setStatus(EscrowStatus.PENDING);
            tx.setAmountHeld(BigDecimal.ZERO);

            when(escrowTransactionRepository.findByGatewayTransactionId("momo_tx_999")).thenReturn(Optional.of(tx));
            when(escrowTransactionRepository.save(any(EscrowTransaction.class))).thenAnswer(i -> i.getArgument(0));

            EscrowTransaction result = holdFundsUseCase.confirmHeld("momo_tx_999", callbackParams);

            assertNotNull(result);
            assertEquals(EscrowStatus.HELD, result.getStatus());
            assertEquals(salePrice, result.getAmountHeld());
            assertEquals("momo_tx_999", result.getGatewayTransactionId());
            assertNotNull(result.getHeldAt());

            verify(transferTicketService).markPaymentEscrowed(ticketId, buyerId, 1L);
            verify(escrowTransactionRepository).save(tx);
        }

        @Test
        @DisplayName("Tra cứu thành công bằng fallback orderId khi gatewayTransactionId null")
        void confirmHeld_Success_FallbackOrderId() {
            Map<String, String> callbackParams = new HashMap<>();
            callbackParams.put("orderId", "1");
            callbackParams.put("transId", "momo_tx_999");

            PaymentResult verifyResult = new PaymentResult();
            verifyResult.setSuccess(true);
            verifyResult.setGatewayTransactionId("momo_tx_999");
            when(paymentGateway.verifyCallback(callbackParams)).thenReturn(verifyResult);

            EscrowTransaction tx = new EscrowTransaction();
            tx.setId(1L);
            tx.setTicketId(ticketId);
            tx.setBuyerId(buyerId);
            tx.setSalePrice(salePrice);
            tx.setStatus(EscrowStatus.PENDING);

            when(escrowTransactionRepository.findById(1L)).thenReturn(Optional.of(tx));
            when(escrowTransactionRepository.save(any(EscrowTransaction.class))).thenAnswer(i -> i.getArgument(0));

            EscrowTransaction result = holdFundsUseCase.confirmHeld(null, callbackParams);

            assertEquals(EscrowStatus.HELD, result.getStatus());
            assertEquals("momo_tx_999", result.getGatewayTransactionId());
            verify(transferTicketService).markPaymentEscrowed(ticketId, buyerId, 1L);
        }

        @Test
        @DisplayName("Xác thực callback thất bại (chữ ký sai): từ chối xử lý và ném BusinessRuleViolationException")
        void confirmHeld_VerifyCallbackFails_ThrowsException() {
            Map<String, String> callbackParams = Map.of("signature", "invalid");
            PaymentResult badVerify = new PaymentResult();
            badVerify.setSuccess(false);
            badVerify.setMessage("Chữ ký không hợp lệ");
            when(paymentGateway.verifyCallback(callbackParams)).thenReturn(badVerify);

            BusinessRuleViolationException ex = assertThrows(BusinessRuleViolationException.class,
                    () -> holdFundsUseCase.confirmHeld("momo_tx_123", callbackParams));

            assertTrue(ex.getMessage().contains("Xác thực callback cổng thanh toán thất bại"));
            verifyNoInteractions(transferTicketService);
            verify(escrowTransactionRepository, never()).save(any());
        }

        @Test
        @DisplayName("Không tìm thấy giao dịch: ném ResourceNotFoundException")
        void confirmHeld_TransactionNotFound_ThrowsException() {
            Map<String, String> callbackParams = Map.of("orderId", "999");
            PaymentResult verifyResult = new PaymentResult();
            verifyResult.setSuccess(true);
            when(paymentGateway.verifyCallback(callbackParams)).thenReturn(verifyResult);

            when(escrowTransactionRepository.findByGatewayTransactionId("unknown_tx")).thenReturn(Optional.empty());
            when(escrowTransactionRepository.findById(999L)).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class,
                    () -> holdFundsUseCase.confirmHeld("unknown_tx", callbackParams));
        }

        @Test
        @DisplayName("Idempotent: Bản ghi đã ở HELD thì trả về nguyên trạng, không gọi lại transferTicketService")
        void confirmHeld_AlreadyHeld_ReturnsDirectly() {
            Map<String, String> callbackParams = Map.of("orderId", "1");
            PaymentResult verifyResult = new PaymentResult();
            verifyResult.setSuccess(true);
            when(paymentGateway.verifyCallback(callbackParams)).thenReturn(verifyResult);

            EscrowTransaction alreadyHeld = new EscrowTransaction();
            alreadyHeld.setId(1L);
            alreadyHeld.setTicketId(ticketId);
            alreadyHeld.setStatus(EscrowStatus.HELD);
            alreadyHeld.setAmountHeld(salePrice);
            when(escrowTransactionRepository.findByGatewayTransactionId("tx_1")).thenReturn(Optional.of(alreadyHeld));

            EscrowTransaction result = holdFundsUseCase.confirmHeld("tx_1", callbackParams);

            assertSame(alreadyHeld, result);
            verify(transferTicketService, never()).markPaymentEscrowed(any(), any(), any());
            verify(escrowTransactionRepository, never()).save(any());
        }

        @Test
        @DisplayName("Trạng thái không hợp lệ (không phải PENDING/HELD): ném BusinessRuleViolationException")
        void confirmHeld_InvalidStatus_ThrowsException() {
            Map<String, String> callbackParams = Map.of("orderId", "1");
            PaymentResult verifyResult = new PaymentResult();
            verifyResult.setSuccess(true);
            when(paymentGateway.verifyCallback(callbackParams)).thenReturn(verifyResult);

            EscrowTransaction failedTx = new EscrowTransaction();
            failedTx.setId(1L);
            failedTx.setStatus(EscrowStatus.FAILED);
            when(escrowTransactionRepository.findByGatewayTransactionId("tx_failed")).thenReturn(Optional.of(failedTx));

            assertThrows(BusinessRuleViolationException.class,
                    () -> holdFundsUseCase.confirmHeld("tx_failed", callbackParams));
        }
    }
}
