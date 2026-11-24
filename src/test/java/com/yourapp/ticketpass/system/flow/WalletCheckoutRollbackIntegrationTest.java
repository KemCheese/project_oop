package com.yourapp.ticketpass.system.flow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import com.yourapp.ticketpass.payment.application.HoldFundsUseCase;
import com.yourapp.ticketpass.payment.domain.EscrowStatus;
import com.yourapp.ticketpass.payment.domain.EscrowTransaction;
import com.yourapp.ticketpass.system.base.AbstractSystemIntegrationTest;
import com.yourapp.ticketpass.ticket.domain.Ticket;
import com.yourapp.ticketpass.ticket.domain.TicketStatus;
import com.yourapp.ticketpass.ticket.domain.TicketType;
import com.yourapp.ticketpass.user.domain.User;
import com.yourapp.ticketpass.user.domain.UserRole;
import com.yourapp.ticketpass.wallet.application.WalletCheckoutUseCase;
import com.yourapp.ticketpass.wallet.application.WalletService;
import com.yourapp.ticketpass.wallet.domain.Wallet;
import com.yourapp.ticketpass.wallet.domain.WalletTransaction;
import com.yourapp.ticketpass.wallet.domain.WalletTransactionType;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;

@DisplayName("Hệ Thống - Chứng Minh Rollback Nguyên Tử Khi Xác Nhận Escrow Thất Bại Sau Debit")
class WalletCheckoutRollbackIntegrationTest extends AbstractSystemIntegrationTest {

    @Autowired
    private WalletCheckoutUseCase walletCheckoutUseCase;

    @Autowired
    private WalletService walletService;

    @SpyBean
    private HoldFundsUseCase holdFundsUseCase;

    @Test
    @DisplayName("Nếu confirmHeld() ném RuntimeException sau khi debit() đã trừ ví -> số dư ví & sổ cái rollback 100%")
    void payEscrowWithWallet_rollsBackWalletDebitWhenConfirmHeldFails() {
        User seller = createAndSaveUser("rb-seller-" + System.nanoTime() + "@test.com", "Pass1234@", UserRole.USER);
        User buyer = createAndSaveUser("rb-buyer-" + System.nanoTime() + "@test.com", "Pass1234@", UserRole.USER);

        walletService.topUp(buyer.getId(), new BigDecimal("1500000.00"), "Số dư trước lỗi");
        Ticket ticket = createAndSaveTicket(seller.getId(), "Vé Test Rollback", TicketType.CONCERT,
                new BigDecimal("500000.00"), TicketStatus.LISTED);
        EscrowTransaction pendingEscrow = createAndSaveEscrow(ticket.getId(), buyer.getId(),
                new BigDecimal("500000.00"), EscrowStatus.PENDING);

        doThrow(new IllegalStateException("Simulated downstream failure during confirmHeld"))
                .when(holdFundsUseCase).confirmHeld(any(), any());

        assertThatThrownBy(() -> walletCheckoutUseCase.payEscrowWithWallet(ticket.getId(), buyer.getId(), true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Simulated downstream failure");

        Wallet reloadedWallet = walletRepository.findByUserId(buyer.getId()).orElseThrow();
        assertThat(reloadedWallet.getBalance()).isEqualByComparingTo("1500000.00");

        List<WalletTransaction> txs = walletTransactionRepository.findByWalletIdOrderByCreatedAtDesc(reloadedWallet.getId());
        assertThat(txs).noneMatch(tx -> tx.getType() == WalletTransactionType.ESCROW_PAY);

        assertThat(escrowTransactionRepository.findById(pendingEscrow.getId()).orElseThrow().getStatus())
                .isEqualTo(EscrowStatus.PENDING);
        assertThat(ticketRepository.findById(ticket.getId()).orElseThrow().getStatus())
                .isEqualTo(TicketStatus.LISTED);
    }
}
