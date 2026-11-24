package com.yourapp.ticketpass.system.flow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yourapp.ticketpass.payment.application.HoldFundsUseCase;
import com.yourapp.ticketpass.payment.domain.Deposit;
import com.yourapp.ticketpass.payment.domain.DepositStatus;
import com.yourapp.ticketpass.payment.domain.EscrowStatus;
import com.yourapp.ticketpass.payment.domain.EscrowTransaction;
import com.yourapp.ticketpass.reputation.domain.UserReputationScore;
import com.yourapp.ticketpass.shared.exception.BusinessRuleViolationException;
import com.yourapp.ticketpass.system.base.AbstractSystemIntegrationTest;
import com.yourapp.ticketpass.ticket.application.TransferTicketService;
import com.yourapp.ticketpass.ticket.domain.Ticket;
import com.yourapp.ticketpass.ticket.domain.TicketStatus;
import com.yourapp.ticketpass.ticket.domain.TicketType;
import com.yourapp.ticketpass.ticket.web.dto.CreateTicketRequest;
import com.yourapp.ticketpass.user.domain.User;
import com.yourapp.ticketpass.user.domain.UserRole;
import com.yourapp.ticketpass.wallet.application.WalletCheckoutUseCase;
import com.yourapp.ticketpass.wallet.application.WalletService;
import com.yourapp.ticketpass.wallet.domain.Wallet;
import com.yourapp.ticketpass.wallet.domain.WalletTransaction;
import com.yourapp.ticketpass.wallet.domain.WalletTransactionType;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

@DisplayName("Hệ Thống - Atomic Wallet Checkout, Anti Double-Debit, Overlap Guard & Hạn Mức Uy Tín 2 Chiều")
class WalletCheckoutAtomicityIntegrationTest extends AbstractSystemIntegrationTest {

    @Autowired
    private WalletCheckoutUseCase walletCheckoutUseCase;

    @Autowired
    private WalletService walletService;

    @Autowired
    private HoldFundsUseCase holdFundsUseCase;

    @Autowired
    private TransferTicketService transferTicketService;

    private MockHttpSession loginAs(String email, String rawPassword) throws Exception {
        MvcResult result = mockMvc.perform(post("/login")
                        .param("username", email)
                        .param("password", rawPassword)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    @Test
    @DisplayName("Anti Double-Debit / Replay: Thanh toán Escrow & Deposit thành công 1 lần duy nhất; gọi lại bị chặn và KHÔNG trừ ví lần 2")
    void antiDoubleDebit_blocksReplayOnEscrowAndDeposit() {
        User seller = createAndSaveUser("atomic-seller-" + System.nanoTime() + "@test.com", "Pass1234@", UserRole.USER);
        User buyer = createAndSaveUser("atomic-buyer-" + System.nanoTime() + "@test.com", "Pass1234@", UserRole.USER);

        walletService.topUp(buyer.getId(), new BigDecimal("2000000.00"), "Nạp tiền người mua");
        walletService.topUp(seller.getId(), new BigDecimal("1000000.00"), "Nạp tiền người bán");

        Ticket ticket = createAndSaveTicket(seller.getId(), "Vé Atomic Anti-Replay", TicketType.MOVIE,
                new BigDecimal("400000.00"), TicketStatus.LISTED);

        walletCheckoutUseCase.payEscrowWithWallet(ticket.getId(), buyer.getId(), true);

        EscrowTransaction escrow = escrowTransactionRepository.findByTicketId(ticket.getId()).orElseThrow();
        createdEscrowIds.add(escrow.getId());
        assertThat(escrow.getStatus()).isEqualTo(EscrowStatus.HELD);
        assertThat(ticketRepository.findById(ticket.getId()).orElseThrow().getStatus())
                .isEqualTo(TicketStatus.PAYMENT_ESCROWED);
        assertThat(walletRepository.findByUserId(buyer.getId()).orElseThrow().getBalance())
                .isEqualByComparingTo("1600000.00");

        assertThatThrownBy(() -> walletCheckoutUseCase.payEscrowWithWallet(ticket.getId(), buyer.getId(), true))
                .isInstanceOf(BusinessRuleViolationException.class);
        assertThatThrownBy(() -> walletCheckoutUseCase.payFromTransfer(escrow.getId(), "escrow", buyer.getId(), true))
                .isInstanceOf(BusinessRuleViolationException.class);

        Wallet buyerWalletAfterReplay = walletRepository.findByUserId(buyer.getId()).orElseThrow();
        assertThat(buyerWalletAfterReplay.getBalance()).isEqualByComparingTo("1600000.00");
        long escrowPayCount = walletTransactionRepository.findByWalletIdOrderByCreatedAtDesc(buyerWalletAfterReplay.getId())
                .stream().filter(tx -> tx.getType() == WalletTransactionType.ESCROW_PAY).count();
        assertThat(escrowPayCount).isEqualTo(1);

        walletCheckoutUseCase.payDepositWithWallet(ticket.getId(), seller.getId());

        Deposit deposit = depositRepository.findByTicketId(ticket.getId()).orElseThrow();
        createdDepositIds.add(deposit.getId());
        assertThat(deposit.getStatus()).isEqualTo(DepositStatus.LOCKED);
        assertThat(ticketRepository.findById(ticket.getId()).orElseThrow().getStatus())
                .isEqualTo(TicketStatus.TRANSFERRED);
        assertThat(walletRepository.findByUserId(seller.getId()).orElseThrow().getBalance())
                .isEqualByComparingTo("800000.00");

        assertThatThrownBy(() -> walletCheckoutUseCase.payDepositWithWallet(ticket.getId(), seller.getId()))
                .isInstanceOf(BusinessRuleViolationException.class);
        assertThatThrownBy(() -> walletCheckoutUseCase.payFromTransfer(deposit.getId(), "deposit", seller.getId(), false))
                .isInstanceOf(BusinessRuleViolationException.class);

        Wallet sellerWalletAfterReplay = walletRepository.findByUserId(seller.getId()).orElseThrow();
        assertThat(sellerWalletAfterReplay.getBalance()).isEqualByComparingTo("800000.00");
        long depositLockCount = walletTransactionRepository.findByWalletIdOrderByCreatedAtDesc(sellerWalletAfterReplay.getId())
                .stream().filter(tx -> tx.getType() == WalletTransactionType.DEPOSIT_LOCK).count();
        assertThat(depositLockCount).isEqualTo(1);
    }

    @Test
    @DisplayName("Fix #5 — /payment/wallet/pay-from-transfer chặn trùng giờ sự kiện khi confirmOverlap=false và cho phép khi confirmOverlap=true")
    void payFromTransfer_enforcesScheduleOverlapGuard() throws Exception {
        String sellerEmail = "ov-seller-" + System.nanoTime() + "@test.com";
        String buyerEmail = "ov-buyer-" + System.nanoTime() + "@test.com";
        User seller = createAndSaveUser(sellerEmail, "Pass1234@", UserRole.USER);
        User buyer = createAndSaveUser(buyerEmail, "Pass1234@", UserRole.USER);

        walletService.topUp(buyer.getId(), new BigDecimal("2000000.00"), "Nạp tiền kiểm thử trùng giờ");

        Instant sharedStart = Instant.now().plus(Duration.ofDays(3));
        Ticket existingTicket = createAndSaveTicket(seller.getId(), "Vé Cũ Đang Giữ", TicketType.MOVIE,
                new BigDecimal("200000.00"), TicketStatus.AWAITING_USE);
        existingTicket.setBuyerId(buyer.getId());
        existingTicket.setEventStartAt(sharedStart);
        existingTicket.setEventEndAt(sharedStart.plus(Duration.ofHours(2)));
        ticketRepository.save(existingTicket);

        Ticket newTicket = createAndSaveTicket(seller.getId(), "Vé Mới Trùng Giờ", TicketType.CONCERT,
                new BigDecimal("500000.00"), TicketStatus.LISTED);
        newTicket.setEventStartAt(sharedStart.plus(Duration.ofMinutes(30)));
        newTicket.setEventEndAt(sharedStart.plus(Duration.ofHours(3)));
        ticketRepository.save(newTicket);

        EscrowTransaction pendingEscrow = createAndSaveEscrow(newTicket.getId(), buyer.getId(),
                new BigDecimal("500000.00"), EscrowStatus.PENDING);

        MockHttpSession buyerSession = loginAs(buyerEmail, "Pass1234@");

        mockMvc.perform(post("/payment/wallet/pay-from-transfer")
                        .session(buyerSession)
                        .param("ref", String.valueOf(pendingEscrow.getId()))
                        .param("type", "escrow")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/tickets/" + newTicket.getId() + "?overlapWarning=true&method=wallet"))
                .andExpect(flash().attribute("overlapWarning", true));

        assertThat(walletRepository.findByUserId(buyer.getId()).orElseThrow().getBalance())
                .isEqualByComparingTo("2000000.00");
        assertThat(escrowTransactionRepository.findById(pendingEscrow.getId()).orElseThrow().getStatus())
                .isEqualTo(EscrowStatus.PENDING);

        mockMvc.perform(post("/payment/wallet/pay-from-transfer")
                        .session(buyerSession)
                        .param("ref", String.valueOf(pendingEscrow.getId()))
                        .param("type", "escrow")
                        .param("confirmOverlap", "true")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/tickets/" + newTicket.getId()))
                .andExpect(flash().attributeExists("successMessage"));

        assertThat(walletRepository.findByUserId(buyer.getId()).orElseThrow().getBalance())
                .isEqualByComparingTo("1500000.00");
        assertThat(escrowTransactionRepository.findById(pendingEscrow.getId()).orElseThrow().getStatus())
                .isEqualTo(EscrowStatus.HELD);
    }

    @Test
    @DisplayName("Hạn mức uy tín 2 chiều: Seller điểm 2 (< 1tr) không đăng được vé 1tr; Buyer điểm 3 (< 6tr) bị chặn trên cả Ví lẫn VietQR khi mua vé 6tr")
    void twoWayReputationLimits_enforcedForSellerListingAndAllBuyerCheckoutChannels() {
        User warningSeller = createAndSaveUser("rep-seller-" + System.nanoTime() + "@test.com", "Pass1234@", UserRole.USER);
        User normalBuyer = createAndSaveUser("rep-buyer-" + System.nanoTime() + "@test.com", "Pass1234@", UserRole.USER);
        User vipSeller = createAndSaveUser("rep-vip-" + System.nanoTime() + "@test.com", "Pass1234@", UserRole.USER);

        UserReputationScore warnRep = reputationRepository.findByUserId(warningSeller.getId()).orElseThrow();
        warnRep.setScore(new BigDecimal("2.00"));
        reputationRepository.save(warnRep);

        UserReputationScore vipRep = reputationRepository.findByUserId(vipSeller.getId()).orElseThrow();
        vipRep.setScore(new BigDecimal("5.00"));
        reputationRepository.save(vipRep);

        walletService.topUp(normalBuyer.getId(), new BigDecimal("10000000.00"), "Nạp 10tr");

        CreateTicketRequest blockedListing = new CreateTicketRequest();
        blockedListing.setTicketType(TicketType.CONCERT);
        blockedListing.setTitle("Vé vượt hạn mức Seller");
        blockedListing.setEventName("Concert VIP");
        blockedListing.setVenue("Mỹ Đình");
        blockedListing.setSeatInfo("A1");
        blockedListing.setEventStartAt(LocalDateTime.now().plusDays(5));
        blockedListing.setEventEndAt(LocalDateTime.now().plusDays(5).plusHours(2));
        blockedListing.setOriginalPrice(new BigDecimal("1200000"));
        blockedListing.setSalePrice(new BigDecimal("1000000"));

        assertThatThrownBy(() -> transferTicketService.createListing(blockedListing, warningSeller.getId()))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("Cảnh Báo");

        Ticket expensiveTicket = createAndSaveTicket(vipSeller.getId(), "Vé VVIP 6 Triệu", TicketType.CONCERT,
                new BigDecimal("6000000.00"), TicketStatus.LISTED);

        assertThatThrownBy(() -> walletCheckoutUseCase.payEscrowWithWallet(expensiveTicket.getId(), normalBuyer.getId(), true))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("Bình thường");

        assertThatThrownBy(() -> holdFundsUseCase.initEscrowPayment(
                expensiveTicket.getId(), normalBuyer.getId(), vipSeller.getId(), new BigDecimal("6000000.00")))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("Bình thường");

        EscrowTransaction pending = createAndSaveEscrow(expensiveTicket.getId(), normalBuyer.getId(),
                new BigDecimal("6000000.00"), EscrowStatus.PENDING);
        assertThatThrownBy(() -> walletCheckoutUseCase.payFromTransfer(pending.getId(), "escrow", normalBuyer.getId(), true))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("Bình thường");

        assertThat(walletRepository.findByUserId(normalBuyer.getId()).orElseThrow().getBalance())
                .isEqualByComparingTo("10000000.00");
        List<WalletTransaction> txs = walletTransactionRepository.findByWalletIdOrderByCreatedAtDesc(
                walletRepository.findByUserId(normalBuyer.getId()).orElseThrow().getId());
        assertThat(txs).noneMatch(tx -> tx.getType() == WalletTransactionType.ESCROW_PAY);
    }
}
