package com.yourapp.ticketpass.wallet.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.yourapp.ticketpass.shared.exception.BusinessRuleViolationException;

@DisplayName("Kiểm thử Domain Entity Wallet")
class WalletTest {

    @Test
    @DisplayName("Khởi tạo ví mới: số dư mặc định là 0 và trạng thái ACTIVE")
    void testWalletInitialization() {
        Wallet wallet = new Wallet(100L);
        assertThat(wallet.getUserId()).isEqualTo(100L);
        assertThat(wallet.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(wallet.getStatus()).isEqualTo(WalletStatus.ACTIVE);
    }

    @Test
    @DisplayName("Cộng tiền (credit) hợp lệ: số dư tăng lên chính xác")
    void testCreditSuccess() {
        Wallet wallet = new Wallet(100L);
        wallet.credit(new BigDecimal("500000.00"));
        assertThat(wallet.getBalance()).isEqualByComparingTo(new BigDecimal("500000.00"));

        wallet.credit(new BigDecimal("200000.00"));
        assertThat(wallet.getBalance()).isEqualByComparingTo(new BigDecimal("700000.00"));
    }

    @Test
    @DisplayName("Cộng tiền số tiền <= 0: ném BusinessRuleViolationException")
    void testCreditInvalidAmount() {
        Wallet wallet = new Wallet(100L);
        assertThatThrownBy(() -> wallet.credit(BigDecimal.ZERO))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("Số tiền cộng vào ví phải lớn hơn 0");

        assertThatThrownBy(() -> wallet.credit(new BigDecimal("-10000")))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    @DisplayName("Trừ tiền (debit) hợp lệ: số dư giảm xuống chính xác")
    void testDebitSuccess() {
        Wallet wallet = new Wallet(100L, new BigDecimal("1000000.00"));
        wallet.debit(new BigDecimal("400000.00"));
        assertThat(wallet.getBalance()).isEqualByComparingTo(new BigDecimal("600000.00"));
    }

    @Test
    @DisplayName("Trừ tiền khi số dư không đủ: ném InsufficientBalanceException")
    void testDebitInsufficientBalance() {
        Wallet wallet = new Wallet(100L, new BigDecimal("100000.00"));
        assertThatThrownBy(() -> wallet.debit(new BigDecimal("150000.00")))
                .isInstanceOf(InsufficientBalanceException.class)
                .hasMessageContaining("Số dư ví không đủ");
    }

    @Test
    @DisplayName("Trừ tiền khi ví bị LOCKED: ném BusinessRuleViolationException")
    void testDebitWhenLocked() {
        Wallet wallet = new Wallet(100L, new BigDecimal("500000.00"));
        wallet.setStatus(WalletStatus.LOCKED);
        assertThatThrownBy(() -> wallet.debit(new BigDecimal("100000.00")))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("Ví đang bị khóa");
    }

    @Test
    @DisplayName("Kiểm tra hasSufficientBalance chính xác")
    void testHasSufficientBalance() {
        Wallet wallet = new Wallet(100L, new BigDecimal("300000.00"));
        assertThat(wallet.hasSufficientBalance(new BigDecimal("200000.00"))).isTrue();
        assertThat(wallet.hasSufficientBalance(new BigDecimal("300000.00"))).isTrue();
        assertThat(wallet.hasSufficientBalance(new BigDecimal("400000.00"))).isFalse();

        wallet.setStatus(WalletStatus.LOCKED);
        assertThat(wallet.hasSufficientBalance(new BigDecimal("200000.00"))).isFalse();
    }
}
