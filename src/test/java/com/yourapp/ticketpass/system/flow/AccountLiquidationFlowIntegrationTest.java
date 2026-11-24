package com.yourapp.ticketpass.system.flow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yourapp.ticketpass.admin.application.AccountLiquidationOrchestrator;
import com.yourapp.ticketpass.admin.application.AdminDashboardService;
import com.yourapp.ticketpass.admin.web.dto.AdminDashboardSummaryResponse;
import com.yourapp.ticketpass.dispute.application.ReviewDisputeUseCase;
import com.yourapp.ticketpass.dispute.domain.Dispute;
import com.yourapp.ticketpass.dispute.domain.DisputeReason;
import com.yourapp.ticketpass.dispute.domain.DisputeStatus;
import com.yourapp.ticketpass.payment.domain.Deposit;
import com.yourapp.ticketpass.payment.domain.DepositStatus;
import com.yourapp.ticketpass.payment.domain.EscrowStatus;
import com.yourapp.ticketpass.payment.domain.EscrowTransaction;
import com.yourapp.ticketpass.reputation.domain.UserReputationScore;
import com.yourapp.ticketpass.shared.exception.BusinessRuleViolationException;
import com.yourapp.ticketpass.system.base.AbstractSystemIntegrationTest;
import com.yourapp.ticketpass.ticket.domain.Ticket;
import com.yourapp.ticketpass.ticket.domain.TicketStatus;
import com.yourapp.ticketpass.ticket.domain.TicketType;
import com.yourapp.ticketpass.user.application.AccountSecurityService;
import com.yourapp.ticketpass.user.domain.User;
import com.yourapp.ticketpass.user.domain.UserRole;
import com.yourapp.ticketpass.user.domain.UserStatus;
import com.yourapp.ticketpass.wallet.application.WalletService;
import com.yourapp.ticketpass.wallet.domain.AccountLiquidation;
import com.yourapp.ticketpass.wallet.domain.LiquidationStatus;
import com.yourapp.ticketpass.wallet.domain.Wallet;
import com.yourapp.ticketpass.wallet.domain.WalletStatus;
import com.yourapp.ticketpass.wallet.domain.WalletTransaction;
import com.yourapp.ticketpass.wallet.domain.WalletTransactionType;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("Hệ Thống - Auto-Ban & Thanh Lý Ví Khi Điểm Uy Tín Về 0 (E2E)")
class AccountLiquidationFlowIntegrationTest extends AbstractSystemIntegrationTest {

    @Autowired
    private ReviewDisputeUseCase reviewDisputeUseCase;

    @Autowired
    private WalletService walletService;

    @Autowired
    private AccountSecurityService accountSecurityService;

    @Autowired
    private AccountLiquidationOrchestrator accountLiquidationOrchestrator;

    @Autowired
    private AdminDashboardService adminDashboardService;

    @Test
    @DisplayName("Full E2E: Thua tranh chấp (2 -> 0) tự động hủy 4 vé chưa mở QR, giữ nguyên AWAITING_USE/DISPUTE, thanh lý ví đúng phí 5%/10k, khóa vĩnh viễn và lên Dashboard/CSV")
    void resolveBuyerWinsDropsScoreToZero_TriggersFullAutoCancelLiquidationPermanentBanAndDashboardRevenue() {
        LocalDate today = LocalDate.now(AdminDashboardService.VIETNAM_ZONE);
        AdminDashboardSummaryResponse baseline = adminDashboardService.getDashboardSummary(today, today);

        User doomed = createAndSaveUser("doomed-" + System.nanoTime() + "@test.com", "Pass1234@", UserRole.USER);
        User counterparty = createAndSaveUser("counter-" + System.nanoTime() + "@test.com", "Pass1234@", UserRole.USER);
        User admin = createAndSaveUser("liq-admin-" + System.nanoTime() + "@test.com", "Admin123@", UserRole.ADMIN);

        UserReputationScore doomedRep = reputationRepository.findByUserId(doomed.getId()).orElseThrow();
        doomedRep.setScore(new BigDecimal("2.00"));
        reputationRepository.save(doomedRep);

        walletService.topUp(doomed.getId(), new BigDecimal("400000.00"), "Số dư ban đầu");
        walletService.topUp(counterparty.getId(), new BigDecimal("100000.00"), "Số dư đối tác");

        Ticket triggerTicket = createAndSaveTicket(doomed.getId(), "Vé Tranh Chấp Kích Nổ", TicketType.MOVIE,
                new BigDecimal("200000.00"), TicketStatus.DISPUTE_UNDER_REVIEW);
        EscrowTransaction triggerEscrow = createAndSaveEscrow(triggerTicket.getId(), counterparty.getId(),
                new BigDecimal("200000.00"), EscrowStatus.HELD);
        Deposit triggerDeposit = createAndSaveDeposit(triggerTicket.getId(), doomed.getId(),
                new BigDecimal("100000.00"), DepositStatus.LOCKED);
        triggerTicket.setBuyerId(counterparty.getId());
        triggerTicket.setEscrowTransactionId(triggerEscrow.getId());
        triggerTicket.setDepositId(triggerDeposit.getId());
        ticketRepository.save(triggerTicket);
        Dispute triggerDispute = createAndSaveDispute(triggerTicket.getId(), counterparty.getId(), doomed.getId(),
                DisputeReason.TICKET_INVALID, DisputeStatus.UNDER_REVIEW);

        Ticket listedTicket = createAndSaveTicket(doomed.getId(), "Vé Đang Rao Bán", TicketType.MOVIE,
                new BigDecimal("150000.00"), TicketStatus.LISTED);

        Ticket escrowedTicket = createAndSaveTicket(doomed.getId(), "Vé Đã Ký Quỹ Chờ Cọc", TicketType.BUS,
                new BigDecimal("300000.00"), TicketStatus.PAYMENT_ESCROWED);
        EscrowTransaction escrowedTx = createAndSaveEscrow(escrowedTicket.getId(), counterparty.getId(),
                new BigDecimal("300000.00"), EscrowStatus.HELD);
        escrowedTicket.setBuyerId(counterparty.getId());
        escrowedTicket.setEscrowTransactionId(escrowedTx.getId());
        ticketRepository.save(escrowedTicket);

        Ticket depositLockedTicket = createAndSaveTicket(doomed.getId(), "Vé Đã Khóa Cọc", TicketType.CONCERT,
                new BigDecimal("400000.00"), TicketStatus.DEPOSIT_LOCKED);
        EscrowTransaction dlEscrow = createAndSaveEscrow(depositLockedTicket.getId(), counterparty.getId(),
                new BigDecimal("400000.00"), EscrowStatus.HELD);
        Deposit dlDeposit = createAndSaveDeposit(depositLockedTicket.getId(), doomed.getId(),
                new BigDecimal("200000.00"), DepositStatus.LOCKED);
        depositLockedTicket.setBuyerId(counterparty.getId());
        depositLockedTicket.setEscrowTransactionId(dlEscrow.getId());
        depositLockedTicket.setDepositId(dlDeposit.getId());
        ticketRepository.save(depositLockedTicket);

        Ticket transferredTicket = createAndSaveTicket(counterparty.getId(), "Vé Doomed Mua Chưa Xác Nhận", TicketType.MOVIE,
                new BigDecimal("250000.00"), TicketStatus.TRANSFERRED);
        EscrowTransaction trEscrow = createAndSaveEscrow(transferredTicket.getId(), doomed.getId(),
                new BigDecimal("250000.00"), EscrowStatus.HELD);
        Deposit trDeposit = createAndSaveDeposit(transferredTicket.getId(), counterparty.getId(),
                new BigDecimal("125000.00"), DepositStatus.LOCKED);
        transferredTicket.setBuyerId(doomed.getId());
        transferredTicket.setEscrowTransactionId(trEscrow.getId());
        transferredTicket.setDepositId(trDeposit.getId());
        ticketRepository.save(transferredTicket);

        Ticket awaitingUseTicket = createAndSaveTicket(doomed.getId(), "Vé Đã Mở QR Phải Giữ Nguyên", TicketType.CONCERT,
                new BigDecimal("500000.00"), TicketStatus.AWAITING_USE);
        EscrowTransaction auEscrow = createAndSaveEscrow(awaitingUseTicket.getId(), counterparty.getId(),
                new BigDecimal("500000.00"), EscrowStatus.HELD);
        Deposit auDeposit = createAndSaveDeposit(awaitingUseTicket.getId(), doomed.getId(),
                new BigDecimal("250000.00"), DepositStatus.LOCKED);
        awaitingUseTicket.setBuyerId(counterparty.getId());
        awaitingUseTicket.setEscrowTransactionId(auEscrow.getId());
        awaitingUseTicket.setDepositId(auDeposit.getId());
        ticketRepository.save(awaitingUseTicket);

        Ticket otherDisputeTicket = createAndSaveTicket(doomed.getId(), "Vé Tranh Chấp Khác Phải Giữ Nguyên", TicketType.BUS,
                new BigDecimal("350000.00"), TicketStatus.DISPUTE_UNDER_REVIEW);
        otherDisputeTicket.setBuyerId(counterparty.getId());
        ticketRepository.save(otherDisputeTicket);

        reviewDisputeUseCase.resolveBuyerWins(triggerDispute.getId(), admin.getId(),
                "Xác minh vé không hợp lệ tại cổng soát vé.");

        assertThat(ticketRepository.findById(listedTicket.getId()).orElseThrow().getStatus()).isEqualTo(TicketStatus.CANCELLED);
        assertThat(ticketRepository.findById(escrowedTicket.getId()).orElseThrow().getStatus()).isEqualTo(TicketStatus.CANCELLED);
        assertThat(ticketRepository.findById(depositLockedTicket.getId()).orElseThrow().getStatus()).isEqualTo(TicketStatus.CANCELLED);
        assertThat(ticketRepository.findById(transferredTicket.getId()).orElseThrow().getStatus()).isEqualTo(TicketStatus.CANCELLED);

        assertThat(escrowTransactionRepository.findById(escrowedTx.getId()).orElseThrow().getStatus()).isEqualTo(EscrowStatus.REFUNDED);
        assertThat(escrowTransactionRepository.findById(dlEscrow.getId()).orElseThrow().getStatus()).isEqualTo(EscrowStatus.REFUNDED);
        assertThat(depositRepository.findById(dlDeposit.getId()).orElseThrow().getStatus()).isEqualTo(DepositStatus.REFUNDED);
        assertThat(escrowTransactionRepository.findById(trEscrow.getId()).orElseThrow().getStatus()).isEqualTo(EscrowStatus.REFUNDED);
        assertThat(depositRepository.findById(trDeposit.getId()).orElseThrow().getStatus()).isEqualTo(DepositStatus.REFUNDED);

        assertThat(ticketRepository.findById(awaitingUseTicket.getId()).orElseThrow().getStatus()).isEqualTo(TicketStatus.AWAITING_USE);
        assertThat(escrowTransactionRepository.findById(auEscrow.getId()).orElseThrow().getStatus()).isEqualTo(EscrowStatus.HELD);
        assertThat(depositRepository.findById(auDeposit.getId()).orElseThrow().getStatus()).isEqualTo(DepositStatus.LOCKED);
        assertThat(ticketRepository.findById(otherDisputeTicket.getId()).orElseThrow().getStatus()).isEqualTo(TicketStatus.DISPUTE_UNDER_REVIEW);

        AccountLiquidation liq = accountLiquidationRepository.findByTriggerKey("ZERO_REPUTATION_" + doomed.getId())
                .orElseThrow();
        assertThat(liq.getCancelledTicketCount()).isEqualTo(4);
        assertThat(liq.getGrossBalance()).isEqualByComparingTo("850000.00");
        assertThat(liq.getFeeAmount()).isEqualByComparingTo("42500.00");
        assertThat(liq.getNetPayoutAmount()).isEqualByComparingTo("807500.00");
        assertThat(liq.getStatus()).isEqualTo(LiquidationStatus.TRANSFERRED);
        assertThat(liq.getBankName()).isEqualTo("Techcombank");
        assertThat(liq.getBankAccountNumberMasked()).startsWith("****").endsWith("1234")
                .doesNotContain("190367891234");

        Wallet doomedWallet = walletRepository.findByUserId(doomed.getId()).orElseThrow();
        assertThat(doomedWallet.getStatus()).isEqualTo(WalletStatus.FROZEN);
        assertThat(doomedWallet.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);

        List<WalletTransaction> doomedTxs = walletTransactionRepository.findByWalletIdOrderByCreatedAtDesc(doomedWallet.getId());
        assertThat(doomedTxs).anyMatch(tx -> tx.getType() == WalletTransactionType.LIQUIDATION_FEE
                && tx.getAmount().compareTo(new BigDecimal("42500.00")) == 0);
        assertThat(doomedTxs).anyMatch(tx -> tx.getType() == WalletTransactionType.LIQUIDATION_PAYOUT
                && tx.getAmount().compareTo(new BigDecimal("807500.00")) == 0);

        assertThatThrownBy(() -> walletService.topUp(doomed.getId(), new BigDecimal("50000.00"), "Thử nạp"))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("đóng băng");
        assertThatThrownBy(() -> walletService.debit(doomed.getId(), new BigDecimal("10000.00"),
                WalletTransactionType.ESCROW_PAY, "TEST", "Thử trừ"))
                .isInstanceOf(BusinessRuleViolationException.class);

        walletService.credit(doomed.getId(), new BigDecimal("100000.00"),
                WalletTransactionType.PAYOUT_RECEIVE, "POST_BAN_PAYOUT", "Giải ngân vé đã mở QR trước đó");
        assertThat(walletRepository.findByUserId(doomed.getId()).orElseThrow().getBalance())
                .isEqualByComparingTo("100000.00");

        User bannedDoomed = userRepository.findById(doomed.getId()).orElseThrow();
        assertThat(bannedDoomed.getStatus()).isEqualTo(UserStatus.BANNED);
        assertThat(bannedDoomed.isPermanentlyBanned()).isTrue();
        assertThat(bannedDoomed.getModeratedByAdminId()).isNull();

        assertThatThrownBy(() -> accountSecurityService.moderateUserStatus(
                doomed.getId(), admin.getId(), UserStatus.ACTIVE, "Thử mở khóa tài khoản về 0"))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("khóa vĩnh viễn");
        assertThatThrownBy(() -> accountSecurityService.moderateUserStatus(
                doomed.getId(), admin.getId(), UserStatus.BANNED, "Thử ghi đè khóa"))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("khóa vĩnh viễn");

        AdminDashboardSummaryResponse after = adminDashboardService.getDashboardSummary(today, today);
        assertThat(after.getTotalLiquidationFee())
                .isEqualByComparingTo(baseline.getTotalLiquidationFee().add(new BigDecimal("42500.00")));
        assertThat(after.getTotalGmv()).isEqualByComparingTo(baseline.getTotalGmv());
        assertThat(after.getTotalTaxWithheld()).isEqualByComparingTo(baseline.getTotalTaxWithheld());

        String csv = new String(adminDashboardService.exportDashboardCsv(today, today), StandardCharsets.UTF_8);
        assertThat(csv).contains("ACCOUNT_LIQUIDATION_FEE");
        assertThat(csv).contains("LIQ-" + liq.getId() + ",,Thanh lý tài khoản #" + doomed.getId() + ",,0,42500,0");
    }

    @Test
    @DisplayName("Công thức phí thanh lý biên (0đ -> NO_BALANCE, 6k -> phí 6k/net 0, 10k -> phí 10k/net 0, 200k -> phí 10k/net 190k, thiếu bank -> PENDING_BANK_INFO)")
    void liquidationFeeFormulaAndBankInfoEdgeCases() {
        User uZero = createAndSaveUser("liq-zero-" + System.nanoTime() + "@test.com", "Pass1234@", UserRole.USER);
        AccountLiquidation lZero = accountLiquidationOrchestrator.executeZeroReputationLiquidation(uZero.getId(), "Zero balance");
        assertThat(lZero.getStatus()).isEqualTo(LiquidationStatus.NO_BALANCE);
        assertThat(lZero.getFeeAmount()).isEqualByComparingTo("0.00");
        assertThat(lZero.getNetPayoutAmount()).isEqualByComparingTo("0.00");

        User u6k = createAndSaveUser("liq-6k-" + System.nanoTime() + "@test.com", "Pass1234@", UserRole.USER);
        walletService.credit(u6k.getId(), new BigDecimal("6000.00"), WalletTransactionType.ESCROW_REFUND, "REF-6K", "Test 6k");
        AccountLiquidation l6k = accountLiquidationOrchestrator.executeZeroReputationLiquidation(u6k.getId(), "Small balance 6k");
        assertThat(l6k.getGrossBalance()).isEqualByComparingTo("6000.00");
        assertThat(l6k.getFeeAmount()).isEqualByComparingTo("6000.00");
        assertThat(l6k.getNetPayoutAmount()).isEqualByComparingTo("0.00");

        User u10k = createAndSaveUser("liq-10k-" + System.nanoTime() + "@test.com", "Pass1234@", UserRole.USER);
        walletService.topUp(u10k.getId(), new BigDecimal("10000.00"), "Nạp 10k");
        AccountLiquidation l10k = accountLiquidationOrchestrator.executeZeroReputationLiquidation(u10k.getId(), "Boundary 10k");
        assertThat(l10k.getFeeAmount()).isEqualByComparingTo("10000.00");
        assertThat(l10k.getNetPayoutAmount()).isEqualByComparingTo("0.00");

        User u200kNoBank = createAndSaveUser("liq-nobank-" + System.nanoTime() + "@test.com", "Pass1234@", UserRole.USER);
        u200kNoBank.setBankName(null);
        u200kNoBank.setBankAccountNumber(null);
        u200kNoBank.setBankAccountHolder(null);
        userRepository.save(u200kNoBank);
        walletService.topUp(u200kNoBank.getId(), new BigDecimal("200000.00"), "Nạp 200k");

        AccountLiquidation l200k = accountLiquidationOrchestrator.executeZeroReputationLiquidation(u200kNoBank.getId(), "No bank info");
        assertThat(l200k.getFeeAmount()).isEqualByComparingTo("10000.00");
        assertThat(l200k.getNetPayoutAmount()).isEqualByComparingTo("190000.00");
        assertThat(l200k.getStatus()).isEqualTo(LiquidationStatus.PENDING_BANK_INFO);
        assertThat(l200k.getBankAccountNumberMasked()).isNull();

        AccountLiquidation replay = accountLiquidationOrchestrator.executeZeroReputationLiquidation(u200kNoBank.getId(), "Replay");
        assertThat(replay.getId()).isEqualTo(l200k.getId());
    }
}
