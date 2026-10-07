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

import com.yourapp.ticketpass.payment.application.HoldFundsUseCase;
import com.yourapp.ticketpass.payment.application.LockDepositUseCase;
import com.yourapp.ticketpass.payment.application.ReleaseFundsUseCase;
import com.yourapp.ticketpass.payment.domain.Deposit;
import com.yourapp.ticketpass.payment.domain.DepositRepository;
import com.yourapp.ticketpass.payment.domain.DepositStatus;
import com.yourapp.ticketpass.payment.domain.EscrowStatus;
import com.yourapp.ticketpass.payment.domain.EscrowTransaction;
import com.yourapp.ticketpass.payment.domain.EscrowTransactionRepository;
import com.yourapp.ticketpass.shared.event.CompletionReason;
import com.yourapp.ticketpass.system.base.AbstractSystemIntegrationTest;
import com.yourapp.ticketpass.ticket.application.TicketStateMachineService;
import com.yourapp.ticketpass.ticket.domain.Ticket;
import com.yourapp.ticketpass.ticket.domain.TicketRepository;
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

/**
 * Kiểm thử tích hợp toàn sàn (System Integration Test) cho Hệ thống Ví Điện Tử (Task C14).
 *
 * <p>Kiểm chứng trọn vẹn chu trình tiền tệ trên sàn TicketPass sử dụng Ví điện tử:
 * <ol>
 *   <li>Nạp tiền vào ví (Quick Top-up Sandbox)</li>
 *   <li>Người mua thanh toán ký quỹ 100% bằng Ví (trừ ví, ghi nhận Ledger ESCROW_PAY)</li>
 *   <li>Người bán nộp cọc bảo đảm 50% bằng Ví (trừ ví, ghi nhận Ledger DEPOSIT_LOCK)</li>
 *   <li>Giải ngân và hoàn cọc tự động qua DelegatingPaymentGateway về Ví người bán</li>
 *   <li>Kịch bản Người mua có số dư 0đ bị từ chối thanh toán</li>
 * </ol>
 *
 * <p>Module: wallet / shared</p>
 * <p>Chủ sở hữu: Người C (Dev C)</p>
 */
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
        // 1. Tạo Người bán và Người mua
        User seller = createAndSaveUser("walletseller@test.com", "Password123@", UserRole.USER);
        User buyer = createAndSaveUser("walletbuyer@test.com", "Password123@", UserRole.USER);

        // 2. Nạp tiền vào ví Sandbox cho cả 2 tài khoản
        // Người mua nạp 2,000,000 VNĐ
        walletService.topUp(buyer.getId(), new BigDecimal("2000000.00"), "Nạp tiền ví thử nghiệm người mua");
        Wallet buyerWallet = walletRepository.findByUserId(buyer.getId()).orElseThrow();
        assertThat(buyerWallet.getBalance()).isEqualByComparingTo(new BigDecimal("2000000.00"));

        // Người bán nạp 1,000,000 VNĐ
        walletService.topUp(seller.getId(), new BigDecimal("1000000.00"), "Nạp tiền ví thử nghiệm người bán");
        Wallet sellerWallet = walletRepository.findByUserId(seller.getId()).orElseThrow();
        assertThat(sellerWallet.getBalance()).isEqualByComparingTo(new BigDecimal("1000000.00"));

        // 3. Người bán niêm yết vé trị giá 600,000 VNĐ
        BigDecimal ticketPrice = new BigDecimal("600000.00");
        Ticket ticket = createAndSaveTicket(seller.getId(), "Vé xem phim VIP Avatar 3",
                TicketType.MOVIE, ticketPrice, TicketStatus.LISTED);

        // 4. Người mua thanh toán ký quỹ 100% bằng Ví (600,000 VNĐ)
        EscrowTransaction escrow = createAndSaveEscrow(ticket.getId(), buyer.getId(), ticketPrice, EscrowStatus.HELD);
        ticket.setBuyerId(buyer.getId());
        ticket.setEscrowTransactionId(escrow.getId());
        ticket.setEscrowedAt(Instant.now());
        ticketRepository.save(ticket);
        ticketStateMachineService.transition(ticket.getId(), TicketStatus.PAYMENT_ESCROWED);

        // Ghi nợ ví Người mua
        walletService.debit(buyer.getId(), ticketPrice, WalletTransactionType.ESCROW_PAY,
                "ESCROW_" + escrow.getId(), "Thanh toán ký quỹ vé " + ticket.getTitle());

        buyerWallet = walletRepository.findByUserId(buyer.getId()).orElseThrow();
        // 2,000,000 - 600,000 = 1,400,000 VNĐ
        assertThat(buyerWallet.getBalance()).isEqualByComparingTo(new BigDecimal("1400000.00"));

        // 5. Người bán nộp cọc bảo đảm 50% bằng Ví (300,000 VNĐ)
        BigDecimal depositAmount = ticketPrice.multiply(new BigDecimal("0.5"));
        Deposit deposit = createAndSaveDeposit(ticket.getId(), seller.getId(), depositAmount, DepositStatus.LOCKED);
        ticket = ticketRepository.findById(ticket.getId()).orElseThrow();
        ticket.setDepositId(deposit.getId());
        ticketRepository.save(ticket);
        ticketStateMachineService.transition(ticket.getId(), TicketStatus.DEPOSIT_LOCKED);

        // Ghi nợ cọc ví Người bán
        walletService.debit(seller.getId(), depositAmount, WalletTransactionType.DEPOSIT_LOCK,
                "DEPOSIT_" + deposit.getId(), "Khóa cọc 50% vé " + ticket.getTitle());

        sellerWallet = walletRepository.findByUserId(seller.getId()).orElseThrow();
        // 1,000,000 - 300,000 = 700,000 VNĐ
        assertThat(sellerWallet.getBalance()).isEqualByComparingTo(new BigDecimal("700000.00"));

        // 6. Chuyển giao vé và kết thúc giao dịch
        ticketStateMachineService.transition(ticket.getId(), TicketStatus.TRANSFERRED);
        ticket = ticketRepository.findById(ticket.getId()).orElseThrow();
        ticket.setTransferredAt(Instant.now());
        ticketRepository.save(ticket);

        ticketStateMachineService.transition(ticket.getId(), TicketStatus.AWAITING_USE);
        ticket = ticketRepository.findById(ticket.getId()).orElseThrow();
        ticket.setReceivedAt(Instant.now());
        ticket.setDisputeWindowClosesAt(Instant.now().minus(1, ChronoUnit.HOURS));
        ticketRepository.save(ticket);

        // 7. Giải ngân tiền vé và hoàn cọc: Gọi ReleaseFundsUseCase
        // DelegatingPaymentGateway sẽ tự động chuyển hướng payout về WalletPaymentGatewayAdapter
        releaseFundsUseCase.release(ticket.getId(), CompletionReason.AUTO_COMPLETE);

        // 8. Xác minh số dư ví người bán sau khi giải ngân:
        // Ban đầu: 1,000,000 VNĐ
        // Trừ cọc 50%: -300,000 VNĐ -> Còn lại 700,000 VNĐ
        // Nhận tiền bán vé thực nhận (sau khi trừ phí hoa hồng sàn và khấu thuế): sellerNetPayout (579,000 VNĐ)
        // Nhận hoàn cọc bảo đảm 50%: 300,000 VNĐ
        // Tổng số dư = 700,000 + 579,000 + 300,000 = 1,579,000 VNĐ
        EscrowTransaction releasedEscrow = escrowTransactionRepository.findById(escrow.getId()).orElseThrow();
        BigDecimal expectedBalance = new BigDecimal("700000.00")
                .add(releasedEscrow.getSellerNetPayout())
                .add(deposit.getAmount());

        sellerWallet = walletRepository.findByUserId(seller.getId()).orElseThrow();
        assertThat(sellerWallet.getBalance()).isEqualByComparingTo(expectedBalance);

        // 9. Xác minh sổ cái Ledger (wallet_transactions) của Người bán
        List<WalletTransaction> sellerTxs = walletTransactionRepository.findByWalletIdOrderByCreatedAtDesc(sellerWallet.getId());
        assertThat(sellerTxs).hasSize(4); // TOP_UP, DEPOSIT_LOCK, PAYOUT_RECEIVE, DEPOSIT_REFUND
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
