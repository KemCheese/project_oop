package com.yourapp.ticketpass.wallet.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.yourapp.ticketpass.shared.exception.BusinessRuleViolationException;
import com.yourapp.ticketpass.wallet.domain.InsufficientBalanceException;
import com.yourapp.ticketpass.wallet.domain.Wallet;
import com.yourapp.ticketpass.wallet.domain.WalletRepository;
import com.yourapp.ticketpass.wallet.domain.WalletTransaction;
import com.yourapp.ticketpass.wallet.domain.WalletTransactionRepository;
import com.yourapp.ticketpass.wallet.domain.WalletTransactionType;

@ExtendWith(MockitoExtension.class)
@DisplayName("Kiểm thử Application Service WalletService")
class WalletServiceTest {

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private WalletTransactionRepository walletTransactionRepository;

    @InjectMocks
    private WalletService walletService;

    @Test
    @DisplayName("getOrCreateWallet: trả về ví có sẵn nếu đã tồn tại")
    void testGetOrCreateWallet_Existing() {
        Wallet existing = new Wallet(10L, new BigDecimal("500000.00"));
        when(walletRepository.findByUserId(10L)).thenReturn(Optional.of(existing));

        Wallet result = walletService.getOrCreateWallet(10L);
        assertThat(result.getUserId()).isEqualTo(10L);
        assertThat(result.getBalance()).isEqualByComparingTo(new BigDecimal("500000.00"));
    }

    @Test
    @DisplayName("getOrCreateWallet: tạo mới ví với số dư 0 nếu chưa tồn tại")
    void testGetOrCreateWallet_New() {
        when(walletRepository.findByUserId(20L)).thenReturn(Optional.empty());
        when(walletRepository.save(any(Wallet.class))).thenAnswer(inv -> inv.getArgument(0));

        Wallet result = walletService.getOrCreateWallet(20L);
        assertThat(result.getUserId()).isEqualTo(20L);
        assertThat(result.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        verify(walletRepository).save(any(Wallet.class));
    }

    @Test
    @DisplayName("topUp: nạp tiền thành công và ghi sổ cái giao dịch TOP_UP")
    void testTopUpSuccess() {
        Wallet wallet = new Wallet(10L, new BigDecimal("100000.00"));
        when(walletRepository.findByUserIdWithLock(10L)).thenReturn(Optional.of(wallet));
        when(walletRepository.save(any(Wallet.class))).thenAnswer(inv -> inv.getArgument(0));

        Wallet result = walletService.topUp(10L, new BigDecimal("400000.00"), "Nạp tiền demo");

        assertThat(result.getBalance()).isEqualByComparingTo(new BigDecimal("500000.00"));

        ArgumentCaptor<WalletTransaction> txCaptor = ArgumentCaptor.forClass(WalletTransaction.class);
        verify(walletTransactionRepository).save(txCaptor.capture());
        WalletTransaction tx = txCaptor.getValue();
        assertThat(tx.getType()).isEqualTo(WalletTransactionType.TOP_UP);
        assertThat(tx.getAmount()).isEqualByComparingTo(new BigDecimal("400000.00"));
        assertThat(tx.getBalanceBefore()).isEqualByComparingTo(new BigDecimal("100000.00"));
        assertThat(tx.getBalanceAfter()).isEqualByComparingTo(new BigDecimal("500000.00"));
    }

    @Test
    @DisplayName("topUp: cho phép nạp tiền lẻ (11k, 13k, 16k, 17k...) khi đạt tối thiểu 10k")
    void testTopUpSuccess_OddAmount() {
        Wallet wallet = new Wallet(10L, new BigDecimal("50000.00"));
        when(walletRepository.findByUserIdWithLock(10L)).thenReturn(Optional.of(wallet));
        when(walletRepository.save(any(Wallet.class))).thenAnswer(inv -> inv.getArgument(0));

        // Nạp tiền lẻ 13.000 VNĐ
        Wallet result1 = walletService.topUp(10L, new BigDecimal("13000.00"), "Nạp lẻ 13k");
        assertThat(result1.getBalance()).isEqualByComparingTo(new BigDecimal("63000.00"));

        // Nạp tiếp tiền lẻ 17.000 VNĐ
        Wallet result2 = walletService.topUp(10L, new BigDecimal("17000.00"), "Nạp lẻ 17k");
        assertThat(result2.getBalance()).isEqualByComparingTo(new BigDecimal("80000.00"));

        // Nạp tiếp tiền lẻ 11.000 VNĐ
        Wallet result3 = walletService.topUp(10L, new BigDecimal("11000.00"), "Nạp lẻ 11k");
        assertThat(result3.getBalance()).isEqualByComparingTo(new BigDecimal("91000.00"));
    }

    @Test
    @DisplayName("topUp: ném BusinessRuleViolationException khi số tiền nạp nhỏ hơn 10.000 VNĐ")
    void testTopUp_BelowMinimumAmount_ThrowsException() {
        assertThatThrownBy(() -> walletService.topUp(10L, new BigDecimal("9999.00"), "Nạp dưới 10k"))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("Số tiền nạp tối thiểu là 10.000 VNĐ");

        assertThatThrownBy(() -> walletService.topUp(10L, new BigDecimal("5000.00"), "Nạp 5k"))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("Số tiền nạp tối thiểu là 10.000 VNĐ");

        assertThatThrownBy(() -> walletService.topUp(10L, BigDecimal.ZERO, "Nạp 0đ"))
                .isInstanceOf(BusinessRuleViolationException.class);

        assertThatThrownBy(() -> walletService.topUp(10L, null, "Nạp null"))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    @DisplayName("debit: trừ tiền thành công có khóa bi quan và ghi sổ cái")
    void testDebitSuccess() {
        Wallet wallet = new Wallet(10L, new BigDecimal("1000000.00"));
        when(walletRepository.findByUserIdWithLock(10L)).thenReturn(Optional.of(wallet));
        when(walletRepository.save(any(Wallet.class))).thenAnswer(inv -> inv.getArgument(0));

        Wallet result = walletService.debit(10L, new BigDecimal("300000.00"),
                WalletTransactionType.ESCROW_PAY, "ESCROW_123", "Thanh toán vé");

        assertThat(result.getBalance()).isEqualByComparingTo(new BigDecimal("700000.00"));

        ArgumentCaptor<WalletTransaction> txCaptor = ArgumentCaptor.forClass(WalletTransaction.class);
        verify(walletTransactionRepository).save(txCaptor.capture());
        WalletTransaction tx = txCaptor.getValue();
        assertThat(tx.getType()).isEqualTo(WalletTransactionType.ESCROW_PAY);
        assertThat(tx.getAmount()).isEqualByComparingTo(new BigDecimal("300000.00"));
        assertThat(tx.getBalanceBefore()).isEqualByComparingTo(new BigDecimal("1000000.00"));
        assertThat(tx.getBalanceAfter()).isEqualByComparingTo(new BigDecimal("700000.00"));
    }

    @Test
    @DisplayName("debit: ném InsufficientBalanceException khi không đủ tiền")
    void testDebitInsufficientBalance() {
        Wallet wallet = new Wallet(10L, new BigDecimal("100000.00"));
        when(walletRepository.findByUserIdWithLock(10L)).thenReturn(Optional.of(wallet));

        assertThatThrownBy(() -> walletService.debit(10L, new BigDecimal("500000.00"),
                WalletTransactionType.ESCROW_PAY, "ESCROW_123", "Thanh toán vé"))
                .isInstanceOf(InsufficientBalanceException.class);
    }

    @Test
    @DisplayName("credit: cộng tiền giải ngân vào ví và ghi sổ cái")
    void testCreditSuccess() {
        Wallet wallet = new Wallet(10L, new BigDecimal("200000.00"));
        when(walletRepository.findByUserIdWithLock(10L)).thenReturn(Optional.of(wallet));
        when(walletRepository.save(any(Wallet.class))).thenAnswer(inv -> inv.getArgument(0));

        Wallet result = walletService.credit(10L, new BigDecimal("800000.00"),
                WalletTransactionType.PAYOUT_RECEIVE, "PAYOUT_999", "Giải ngân tiền vé");

        assertThat(result.getBalance()).isEqualByComparingTo(new BigDecimal("1000000.00"));

        ArgumentCaptor<WalletTransaction> txCaptor = ArgumentCaptor.forClass(WalletTransaction.class);
        verify(walletTransactionRepository).save(txCaptor.capture());
        WalletTransaction tx = txCaptor.getValue();
        assertThat(tx.getType()).isEqualTo(WalletTransactionType.PAYOUT_RECEIVE);
        assertThat(tx.getBalanceBefore()).isEqualByComparingTo(new BigDecimal("200000.00"));
        assertThat(tx.getBalanceAfter()).isEqualByComparingTo(new BigDecimal("1000000.00"));
    }

    @Test
    @DisplayName("getTransactionHistory: trả về danh sách lịch sử biến động sổ cái")
    void testGetTransactionHistory() {
        Wallet wallet = new Wallet(10L, new BigDecimal("500000.00"));
        wallet.credit(BigDecimal.TEN);
        when(walletRepository.findByUserId(10L)).thenReturn(Optional.of(wallet));

        WalletTransaction tx1 = new WalletTransaction(wallet.getId(), new BigDecimal("100000.00"),
                BigDecimal.ZERO, new BigDecimal("100000.00"), WalletTransactionType.TOP_UP, "REF_1", "Nạp tiền");
        when(walletTransactionRepository.findByWalletIdOrderByCreatedAtDesc(wallet.getId(), 50))
                .thenReturn(List.of(tx1));

        List<WalletTransaction> list = walletService.getTransactionHistory(10L, 50);
        assertThat(list).hasSize(1);
        assertThat(list.get(0).getReferenceId()).isEqualTo("REF_1");
    }
}
