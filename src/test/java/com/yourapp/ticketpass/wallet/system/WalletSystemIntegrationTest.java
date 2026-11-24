package com.yourapp.ticketpass.wallet.system;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.yourapp.ticketpass.payment.application.ReleaseFundsUseCase;
import com.yourapp.ticketpass.payment.domain.Deposit;
import com.yourapp.ticketpass.payment.domain.DepositStatus;
import com.yourapp.ticketpass.payment.domain.EscrowStatus;
import com.yourapp.ticketpass.payment.domain.EscrowTransaction;
import com.yourapp.ticketpass.shared.event.CompletionReason;
import com.yourapp.ticketpass.system.base.AbstractSystemIntegrationTest;
import com.yourapp.ticketpass.ticket.application.TicketStateMachineService;
import com.yourapp.ticketpass.ticket.domain.Ticket;
import com.yourapp.ticketpass.ticket.domain.TicketStatus;
import com.yourapp.ticketpass.ticket.domain.TicketType;
import com.yourapp.ticketpass.user.domain.User;
import com.yourapp.ticketpass.user.domain.UserRole;
import com.yourapp.ticketpass.wallet.application.WalletService;
import com.yourapp.ticketpass.wallet.domain.Wallet;
import com.yourapp.ticketpass.wallet.domain.WalletRepository;
import com.yourapp.ticketpass.wallet.domain.WalletTransaction;
import com.yourapp.ticketpass.wallet.domain.WalletTransactionRepository;
import com.yourapp.ticketpass.wallet.domain.WalletTransactionType;
import com.yourapp.ticketpass.wallet.infrastructure.WalletJpaRepository;
import com.yourapp.ticketpass.wallet.infrastructure.WalletTransactionJpaRepository;

@DisplayName("Hệ Thống - Kiểm thử tích hợp chu trình Ví điện tử TicketPass (Task C14)")
class WalletSystemIntegrationTest extends AbstractSystemIntegrationTest {

    @Autowired
    private WalletService walletService;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private WalletTransactionRepository walletTransactionRepository;

    @Autowired
    private WalletJpaRepository walletJpaRepository;

    @Autowired
    private WalletTransactionJpaRepository walletTransactionJpaRepository;

    @Autowired
    private ReleaseFundsUseCase releaseFundsUseCase;

    @Autowired
    private TicketStateMachineService ticketStateMachineService;

    @AfterEach
    void cleanWalletData() {
        walletTransactionJpaRepository.deleteAll();
        for (Long uid : createdUserIds) {
            walletRepository.findByUserId(uid).ifPresent(w -> walletJpaRepository.deleteById(w.getId()));
        }
    }

    @Test
    @DisplayName("Chu trình thanh toán ký quỹ, khóa cọc và giải ngân tự động bằng Ví điện tử")
    void testFullWalletEscrowAndPayoutLifecycle() {
        User seller = createAndSaveUser("walletseller@test.com", "Password123@", UserRole.USER);
        User buyer = createAndSaveUser("walletbuyer@test.com", "Password123@", UserRole.USER);

        walletService.topUp(buyer.getId(), new BigDecimal("2000000.00"), "Nạp tiền ví thử nghiệm người mua");
        Wallet buyerWallet = walletRepository.findByUserId(buyer.getId()).orElseThrow();
        assertThat(buyerWallet.getBalance()).isEqualByComparingTo(new BigDecimal("2000000.00"));

        walletService.topUp(seller.getId(), new BigDecimal("1000000.00"), "Nạp tiền ví thử nghiệm người bán");
        Wallet sellerWallet = walletRepository.findByUserId(seller.getId()).orElseThrow();
        assertThat(sellerWallet.getBalance()).isEqualByComparingTo(new BigDecimal("1000000.00"));

        BigDecimal ticketPrice = new BigDecimal("600000.00");
        Ticket ticket = createAndSaveTicket(seller.getId(), "Vé xem phim VIP Avatar 3",
                TicketType.MOVIE, ticketPrice, TicketStatus.LISTED);

        EscrowTransaction escrow = createAndSaveEscrow(ticket.getId(), buyer.getId(), ticketPrice, EscrowStatus.HELD);
        ticket.setBuyerId(buyer.getId());
        ticket.setEscrowTransactionId(escrow.getId());
        ticket.setEscrowedAt(Instant.now());
        ticketRepository.save(ticket);
        ticketStateMachineService.transition(ticket.getId(), TicketStatus.PAYMENT_ESCROWED);

        walletService.debit(buyer.getId(), ticketPrice, WalletTransactionType.ESCROW_PAY,
                "ESCROW_" + escrow.getId(), "Thanh toán ký quỹ vé " + ticket.getTitle());

        buyerWallet = walletRepository.findByUserId(buyer.getId()).orElseThrow();
        assertThat(buyerWallet.getBalance()).isEqualByComparingTo(new BigDecimal("1400000.00"));

        BigDecimal depositAmount = ticketPrice.multiply(new BigDecimal("0.5"));
        Deposit deposit = createAndSaveDeposit(ticket.getId(), seller.getId(), depositAmount, DepositStatus.LOCKED);
        ticket = ticketRepository.findById(ticket.getId()).orElseThrow();
        ticket.setDepositId(deposit.getId());
        ticketRepository.save(ticket);
        ticketStateMachineService.transition(ticket.getId(), TicketStatus.DEPOSIT_LOCKED);

        walletService.debit(seller.getId(), depositAmount, WalletTransactionType.DEPOSIT_LOCK,
                "DEPOSIT_" + deposit.getId(), "Khóa cọc 50% vé " + ticket.getTitle());

        sellerWallet = walletRepository.findByUserId(seller.getId()).orElseThrow();
        assertThat(sellerWallet.getBalance()).isEqualByComparingTo(new BigDecimal("700000.00"));

        ticketStateMachineService.transition(ticket.getId(), TicketStatus.AWAITING_USE);
        ticket = ticketRepository.findById(ticket.getId()).orElseThrow();
        ticket.setReceivedAt(Instant.now());
        ticket.setDisputeWindowClosesAt(Instant.now().minus(1, ChronoUnit.HOURS));
        ticketRepository.save(ticket);

        releaseFundsUseCase.release(ticket.getId(), CompletionReason.AUTO_COMPLETE);

        EscrowTransaction releasedEscrow = escrowTransactionRepository.findById(escrow.getId()).orElseThrow();
        BigDecimal expectedBalance = new BigDecimal("700000.00")
                .add(releasedEscrow.getSellerNetPayout())
                .add(deposit.getAmount());

        sellerWallet = walletRepository.findByUserId(seller.getId()).orElseThrow();
        assertThat(sellerWallet.getBalance()).isEqualByComparingTo(expectedBalance);

        List<WalletTransaction> sellerTxs = walletTransactionRepository.findByWalletIdOrderByCreatedAtDesc(sellerWallet.getId());
        assertThat(sellerTxs).hasSize(4);
    }

    @Test
    @DisplayName("Tài khoản có số dư 0đ không thể thực hiện thanh toán ký quỹ")
    void testBrokeBuyerCannotPayEscrow() {
        User brokeUser = createAndSaveUser("zero_balance_buyer@test.com", "Password123@", UserRole.USER);

        Wallet wallet = walletService.getOrCreateWallet(brokeUser.getId());
        assertThat(wallet.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);

        boolean canAfford = wallet.hasSufficientBalance(new BigDecimal("100000.00"));
        assertThat(canAfford).isFalse();
    }
}
