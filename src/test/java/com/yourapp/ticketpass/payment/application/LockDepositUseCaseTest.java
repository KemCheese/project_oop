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

import com.yourapp.ticketpass.payment.domain.Deposit;
import com.yourapp.ticketpass.payment.domain.DepositRepository;
import com.yourapp.ticketpass.payment.domain.DepositStatus;
import com.yourapp.ticketpass.payment.domain.PaymentGateway;
import com.yourapp.ticketpass.payment.domain.PaymentRequest;
import com.yourapp.ticketpass.payment.domain.PaymentResult;
import com.yourapp.ticketpass.shared.exception.BusinessRuleViolationException;
import com.yourapp.ticketpass.shared.exception.ResourceNotFoundException;
import com.yourapp.ticketpass.ticket.application.TransferTicketService;

@ExtendWith(MockitoExtension.class)
class LockDepositUseCaseTest {

    @Mock
    private DepositRepository depositRepository;

    @Mock
    private PaymentGateway paymentGateway;

    @Mock
    private TransferTicketService transferTicketService;

    @InjectMocks
    private LockDepositUseCase lockDepositUseCase;

    private final Long ticketId = 100L;
    private final Long sellerId = 20L;
    private final BigDecimal salePrice = new BigDecimal("300000");

    @BeforeEach
    void setUp() {
        lockDepositUseCase.setDepositPercent(new BigDecimal("50"));
    }

    @Nested
    @DisplayName("initDepositPayment Tests")
    class InitDepositPaymentTests {

        @Test
        @DisplayName("Khởi tạo cọc thành công: tính đúng 50% (150.000đ), tạo PENDING, trả về payUrl")
        void initDepositPayment_Success() {
            when(depositRepository.findByTicketId(ticketId)).thenReturn(Optional.empty());
            when(paymentGateway.gatewayName()).thenReturn("MOMO_TEST");

            when(depositRepository.save(any(Deposit.class))).thenAnswer(invocation -> {
                Deposit d = invocation.getArgument(0);
                if (d.getId() == null) {
                    d.setId(5L);
                }
                return d;
            });

            PaymentResult gatewayResult = new PaymentResult();
            gatewayResult.setSuccess(true);
            gatewayResult.setPayUrl("https://test-payment.momo.vn/pay/deposit-123");
            when(paymentGateway.initPayment(any(PaymentRequest.class))).thenReturn(gatewayResult);

            PaymentResult result = lockDepositUseCase.initDepositPayment(ticketId, sellerId, salePrice);

            assertNotNull(result);
            assertTrue(result.isSuccess());
            assertEquals("https://test-payment.momo.vn/pay/deposit-123", result.getPayUrl());

            ArgumentCaptor<PaymentRequest> reqCaptor = ArgumentCaptor.forClass(PaymentRequest.class);
            verify(paymentGateway).initPayment(reqCaptor.capture());
            PaymentRequest sentReq = reqCaptor.getValue();
            assertEquals("5", sentReq.getReferenceId());
            assertEquals(sellerId, sentReq.getPayerUserId());
            assertEquals(new BigDecimal("150000.00"), sentReq.getAmount());
            assertTrue(sentReq.getOrderInfo().contains(String.valueOf(ticketId)));

            ArgumentCaptor<Deposit> depositCaptor = ArgumentCaptor.forClass(Deposit.class);
            verify(depositRepository, atLeastOnce()).save(depositCaptor.capture());
            Deposit savedDeposit = depositCaptor.getValue();
            assertEquals(ticketId, savedDeposit.getTicketId());
            assertEquals(sellerId, savedDeposit.getSellerId());
            assertEquals(DepositStatus.PENDING, savedDeposit.getStatus());
            assertEquals(new BigDecimal("50"), savedDeposit.getDepositPercent());
            assertEquals(new BigDecimal("150000.00"), savedDeposit.getAmount());
            assertEquals("MOMO_TEST", savedDeposit.getGatewayName());
        }

        @Test
        @DisplayName("Validation: Tham số null ném BusinessRuleViolationException")
        void initDepositPayment_NullParams_ThrowsException() {
            assertThrows(BusinessRuleViolationException.class,
                    () -> lockDepositUseCase.initDepositPayment(null, sellerId, salePrice));
            assertThrows(BusinessRuleViolationException.class,
                    () -> lockDepositUseCase.initDepositPayment(ticketId, null, salePrice));
            assertThrows(BusinessRuleViolationException.class,
                    () -> lockDepositUseCase.initDepositPayment(ticketId, sellerId, null));
        }

        @Test
        @DisplayName("Validation: Giá bán <= 0 ném BusinessRuleViolationException")
        void initDepositPayment_NonPositivePrice_ThrowsException() {
            assertThrows(BusinessRuleViolationException.class,
                    () -> lockDepositUseCase.initDepositPayment(ticketId, sellerId, BigDecimal.ZERO));
            assertThrows(BusinessRuleViolationException.class,
                    () -> lockDepositUseCase.initDepositPayment(ticketId, sellerId, new BigDecimal("-50000")));
        }

        @Test
        @DisplayName("Chặn khi vé đã có cọc đang hiệu lực (PENDING hoặc LOCKED)")
        void initDepositPayment_AlreadyActiveDeposit_ThrowsException() {
            Deposit existingPending = new Deposit();
            existingPending.setId(1L);
            existingPending.setStatus(DepositStatus.PENDING);
            when(depositRepository.findByTicketId(ticketId)).thenReturn(Optional.of(existingPending));

            assertThrows(BusinessRuleViolationException.class,
                    () -> lockDepositUseCase.initDepositPayment(ticketId, sellerId, salePrice));

            Deposit existingLocked = new Deposit();
            existingLocked.setId(2L);
            existingLocked.setStatus(DepositStatus.LOCKED);
            when(depositRepository.findByTicketId(ticketId)).thenReturn(Optional.of(existingLocked));

            assertThrows(BusinessRuleViolationException.class,
                    () -> lockDepositUseCase.initDepositPayment(ticketId, sellerId, salePrice));
        }

        @Test
        @DisplayName("Cho phép thử lại khi cọc trước đó ở trạng thái FAILED (tái sử dụng bản ghi)")
        void initDepositPayment_ExistingFailedDeposit_CanRetry() {
            Deposit failedDeposit = new Deposit();
            failedDeposit.setId(9L);
            failedDeposit.setTicketId(ticketId);
            failedDeposit.setStatus(DepositStatus.FAILED);
            failedDeposit.setForfeitReason("Thanh toan that bai truoc do");
            when(depositRepository.findByTicketId(ticketId)).thenReturn(Optional.of(failedDeposit));
            when(paymentGateway.gatewayName()).thenReturn("MOMO_TEST");
            when(depositRepository.save(any(Deposit.class))).thenAnswer(i -> i.getArgument(0));

            PaymentResult gatewayResult = new PaymentResult();
            gatewayResult.setSuccess(true);
            gatewayResult.setPayUrl("https://test-payment.momo.vn/pay/retry-deposit");
            when(paymentGateway.initPayment(any(PaymentRequest.class))).thenReturn(gatewayResult);

            PaymentResult result = lockDepositUseCase.initDepositPayment(ticketId, sellerId, salePrice);

            assertTrue(result.isSuccess());
            assertEquals(DepositStatus.PENDING, failedDeposit.getStatus());
            assertNull(failedDeposit.getForfeitReason());
            assertEquals(new BigDecimal("150000.00"), failedDeposit.getAmount());
        }

        @Test
        @DisplayName("Cổng thanh toán từ chối: Cập nhật status FAILED và lưu lý do")
        void initDepositPayment_GatewayReturnsFailure() {
            when(depositRepository.findByTicketId(ticketId)).thenReturn(Optional.empty());
            when(paymentGateway.gatewayName()).thenReturn("MOMO_TEST");
            when(depositRepository.save(any(Deposit.class))).thenAnswer(i -> {
                Deposit d = i.getArgument(0);
                if (d.getId() == null) d.setId(1L);
                return d;
            });

            PaymentResult failResult = new PaymentResult();
            failResult.setSuccess(false);
            failResult.setMessage("Tài khoản MoMo không đủ số dư để khoá cọc");
            when(paymentGateway.initPayment(any(PaymentRequest.class))).thenReturn(failResult);

            PaymentResult result = lockDepositUseCase.initDepositPayment(ticketId, sellerId, salePrice);

            assertNotNull(result);
            assertFalse(result.isSuccess());

            ArgumentCaptor<Deposit> depositCaptor = ArgumentCaptor.forClass(Deposit.class);
            verify(depositRepository, times(2)).save(depositCaptor.capture());
            Deposit finalSaved = depositCaptor.getAllValues().get(1);
            assertEquals(DepositStatus.FAILED, finalSaved.getStatus());
            assertEquals("Tài khoản MoMo không đủ số dư để khoá cọc", finalSaved.getForfeitReason());
        }

        @Test
        @DisplayName("Cổng thanh toán lỗi mạng: Đánh dấu FAILED và ném BusinessRuleViolationException")
        void initDepositPayment_GatewayException_ThrowsException() {
            when(depositRepository.findByTicketId(ticketId)).thenReturn(Optional.empty());
            when(paymentGateway.gatewayName()).thenReturn("MOMO_TEST");
            when(depositRepository.save(any(Deposit.class))).thenAnswer(i -> {
                Deposit d = i.getArgument(0);
                if (d.getId() == null) d.setId(1L);
                return d;
            });

            when(paymentGateway.initPayment(any(PaymentRequest.class)))
                    .thenThrow(new RuntimeException("Gateway connection timeout"));

            assertThrows(BusinessRuleViolationException.class,
                    () -> lockDepositUseCase.initDepositPayment(ticketId, sellerId, salePrice));

            ArgumentCaptor<Deposit> depositCaptor = ArgumentCaptor.forClass(Deposit.class);
            verify(depositRepository, times(2)).save(depositCaptor.capture());
            Deposit finalSaved = depositCaptor.getAllValues().get(1);
            assertEquals(DepositStatus.FAILED, finalSaved.getStatus());
            assertTrue(finalSaved.getForfeitReason().contains("Gateway connection timeout"));
        }
    }

    @Nested
    @DisplayName("confirmLocked Tests")
    class ConfirmLockedTests {

        @Test
        @DisplayName("Xác nhận thành công: chuyển sang LOCKED, lưu lockedAt và gọi markDepositLocked")
        void confirmLocked_Success() {
            Map<String, String> callbackParams = new HashMap<>();
            callbackParams.put("orderId", "5");
            callbackParams.put("transId", "momo_dep_tx_123");
            callbackParams.put("resultCode", "0");
            callbackParams.put("signature", "valid_signature");

            PaymentResult verifyResult = new PaymentResult();
            verifyResult.setSuccess(true);
            verifyResult.setGatewayTransactionId("momo_dep_tx_123");
            when(paymentGateway.verifyCallback(callbackParams)).thenReturn(verifyResult);

            Deposit deposit = new Deposit();
            deposit.setId(5L);
            deposit.setTicketId(ticketId);
            deposit.setSellerId(sellerId);
            deposit.setStatus(DepositStatus.PENDING);
            deposit.setAmount(new BigDecimal("150000.00"));

            when(depositRepository.findById(5L)).thenReturn(Optional.of(deposit));
            when(depositRepository.save(any(Deposit.class))).thenAnswer(i -> i.getArgument(0));

            Deposit result = lockDepositUseCase.confirmLocked("momo_dep_tx_123", callbackParams);

            assertNotNull(result);
            assertEquals(DepositStatus.LOCKED, result.getStatus());
            assertEquals("momo_dep_tx_123", result.getGatewayTransactionId());
            assertNotNull(result.getLockedAt());

            verify(transferTicketService).markDepositLocked(ticketId, 5L);
            verify(depositRepository).save(deposit);
        }

        @Test
        @DisplayName("Xác thực callback thất bại (chữ ký sai): từ chối xử lý và ném BusinessRuleViolationException")
        void confirmLocked_VerifyCallbackFails_ThrowsException() {
            Map<String, String> callbackParams = Map.of("signature", "invalid");
            PaymentResult badVerify = new PaymentResult();
            badVerify.setSuccess(false);
            badVerify.setMessage("Chữ ký IPN không hợp lệ");
            when(paymentGateway.verifyCallback(callbackParams)).thenReturn(badVerify);

            BusinessRuleViolationException ex = assertThrows(BusinessRuleViolationException.class,
                    () -> lockDepositUseCase.confirmLocked("tx_123", callbackParams));

            assertTrue(ex.getMessage().contains("Xác thực callback cổng thanh toán thất bại"));
            verifyNoInteractions(transferTicketService);
            verify(depositRepository, never()).save(any());
        }

        @Test
        @DisplayName("Không tìm thấy khoản cọc theo referenceId: ném ResourceNotFoundException")
        void confirmLocked_DepositNotFound_ThrowsException() {
            Map<String, String> callbackParams = Map.of("orderId", "999");
            PaymentResult verifyResult = new PaymentResult();
            verifyResult.setSuccess(true);
            when(paymentGateway.verifyCallback(callbackParams)).thenReturn(verifyResult);

            when(depositRepository.findById(999L)).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class,
                    () -> lockDepositUseCase.confirmLocked("tx_123", callbackParams));
        }

        @Test
        @DisplayName("Idempotent: Bản ghi đã ở LOCKED thì trả về ngay, không gọi lại markDepositLocked")
        void confirmLocked_AlreadyLocked_ReturnsDirectly() {
            Map<String, String> callbackParams = Map.of("orderId", "5");
            PaymentResult verifyResult = new PaymentResult();
            verifyResult.setSuccess(true);
            when(paymentGateway.verifyCallback(callbackParams)).thenReturn(verifyResult);

            Deposit alreadyLocked = new Deposit();
            alreadyLocked.setId(5L);
            alreadyLocked.setTicketId(ticketId);
            alreadyLocked.setStatus(DepositStatus.LOCKED);
            when(depositRepository.findById(5L)).thenReturn(Optional.of(alreadyLocked));

            Deposit result = lockDepositUseCase.confirmLocked("tx_123", callbackParams);

            assertSame(alreadyLocked, result);
            verify(transferTicketService, never()).markDepositLocked(any(), any());
            verify(depositRepository, never()).save(any());
        }

        @Test
        @DisplayName("Trạng thái không hợp lệ (không phải PENDING/LOCKED): ném BusinessRuleViolationException")
        void confirmLocked_InvalidStatus_ThrowsException() {
            Map<String, String> callbackParams = Map.of("orderId", "5");
            PaymentResult verifyResult = new PaymentResult();
            verifyResult.setSuccess(true);
            when(paymentGateway.verifyCallback(callbackParams)).thenReturn(verifyResult);

            Deposit failedDeposit = new Deposit();
            failedDeposit.setId(5L);
            failedDeposit.setStatus(DepositStatus.FAILED);
            when(depositRepository.findById(5L)).thenReturn(Optional.of(failedDeposit));

            assertThrows(BusinessRuleViolationException.class,
                    () -> lockDepositUseCase.confirmLocked("tx_123", callbackParams));
        }
    }
}
