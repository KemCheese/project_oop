package com.yourapp.ticketpass.system.flow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yourapp.ticketpass.dispute.application.FileDisputeUseCase;
import com.yourapp.ticketpass.dispute.application.ReviewDisputeUseCase;
import com.yourapp.ticketpass.dispute.domain.Dispute;
import com.yourapp.ticketpass.dispute.domain.DisputeReason;
import com.yourapp.ticketpass.payment.domain.Deposit;
import com.yourapp.ticketpass.payment.domain.DepositStatus;
import com.yourapp.ticketpass.payment.domain.EscrowStatus;
import com.yourapp.ticketpass.payment.domain.EscrowTransaction;
import com.yourapp.ticketpass.reputation.domain.UserReputationScore;
import com.yourapp.ticketpass.system.base.AbstractSystemIntegrationTest;
import com.yourapp.ticketpass.ticket.application.DeliveryTimeoutScheduler;
import com.yourapp.ticketpass.ticket.application.TransferTicketService;
import com.yourapp.ticketpass.ticket.domain.Ticket;
import com.yourapp.ticketpass.ticket.domain.TicketStatus;
import com.yourapp.ticketpass.ticket.domain.TicketType;
import com.yourapp.ticketpass.user.domain.User;
import com.yourapp.ticketpass.user.domain.UserRole;
import com.yourapp.ticketpass.wallet.application.WalletService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

@DisplayName("Hệ Thống - Luồng 5: Auto-Confirm 30p & Lộ Trình Khiếu Nại Tranh Chấp Sau Khi Mở QR")
class DeliveryTimeoutAndQrIssueFlowTest extends AbstractSystemIntegrationTest {

    @Autowired
    private DeliveryTimeoutScheduler deliveryTimeoutScheduler;

    @Autowired
    private TransferTicketService transferTicketService;

    @Autowired
    private FileDisputeUseCase fileDisputeUseCase;

    @Autowired
    private ReviewDisputeUseCase reviewDisputeUseCase;

    @Autowired
    private WalletService walletService;

    private MockHttpSession loginAs(String email, String rawPassword) throws Exception {
        MvcResult result = mockMvc.perform(post("/login")
                        .param("username", email)
                        .param("password", rawPassword)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        assertThat(result.getResponse().getRedirectedUrl()).doesNotContain("error");
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    @Test
    @DisplayName("Nhánh 1 (Auto-Confirm 30p): Sau 30p Buyer không phản hồi -> Scheduler tự chuyển AWAITING_USE đúng hạn BR-09 1B")
    void shouldAutoConfirmDeliveryAfterThirtyMinutesTimeout() {
        User seller = createAndSaveUser("seller_deliv1_" + System.nanoTime() + "@test.com", "Password123@", UserRole.USER);
        User buyer = createAndSaveUser("buyer_deliv1_" + System.nanoTime() + "@test.com", "Password123@", UserRole.USER);

        Instant eventStart = Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        Ticket ticket = createAndSaveTicket(seller.getId(), "Vé Xem Phim Avatar 3", TicketType.MOVIE,
                new BigDecimal("200000.00"), TicketStatus.TRANSFERRED);
        ticket.setBuyerId(buyer.getId());
        ticket.setEventStartAt(eventStart);
        ticket.setTransferredAt(Instant.now().minus(35, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.SECONDS));
        ticket = ticketRepository.save(ticket);

        deliveryTimeoutScheduler.scanDeliveryTimeouts();

        Ticket finalTicket = ticketRepository.findById(ticket.getId()).orElseThrow();
        assertThat(finalTicket.getStatus()).isEqualTo(TicketStatus.AWAITING_USE);
        assertThat(finalTicket.getReceivedAt()).isNotNull();
        assertThat(finalTicket.getDisputeWindowClosesAt())
                .isEqualTo(eventStart.plus(30, ChronoUnit.MINUTES));
    }

    @Test
    @DisplayName("Nhánh 2 (Lộ trình khiếu nại thay thế không dead-end): TRANSFERRED -> Xác nhận (AWAITING_USE + thấy QR) -> Mở tranh chấp TICKET_INVALID -> Admin xử BUYER_WINS hoàn tiền & phạt Seller")
    void shouldAllowBuyerToConfirmAndFileDisputeEndToEndWithoutTechnicalIssuePath() throws Exception {
        String sellerEmail = "seller_alt_disp_" + System.nanoTime() + "@test.com";
        String buyerEmail = "buyer_alt_disp_" + System.nanoTime() + "@test.com";
        User seller = createAndSaveUser(sellerEmail, "Password123@", UserRole.USER);
        User buyer = createAndSaveUser(buyerEmail, "Password123@", UserRole.USER);
        User admin = createAndSaveUser("admin_alt_disp_" + System.nanoTime() + "@test.com", "Admin123@", UserRole.ADMIN);

        walletService.getOrCreateWallet(buyer.getId());
        walletService.getOrCreateWallet(seller.getId());

        BigDecimal salePrice = new BigDecimal("400000.00");
        BigDecimal depositAmount = new BigDecimal("200000.00");
        Instant eventStart = Instant.now().plus(12, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);
        Ticket ticket = createAndSaveTicket(seller.getId(), "Vé Concert Tranh Chấp E2E", TicketType.CONCERT,
                salePrice, TicketStatus.TRANSFERRED);
        ticket.setBuyerId(buyer.getId());
        ticket.setEventStartAt(eventStart);
        ticket.setTransferredAt(Instant.now().minus(5, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.SECONDS));
        ticket.setQrImageStorageKey("tickets-qr/e2e-sample-qr.png");
        ticket = ticketRepository.save(ticket);

        EscrowTransaction escrow = createAndSaveEscrow(ticket.getId(), buyer.getId(), salePrice, EscrowStatus.HELD);
        Deposit deposit = createAndSaveDeposit(ticket.getId(), seller.getId(), depositAmount, DepositStatus.LOCKED);
        ticket.setEscrowTransactionId(escrow.getId());
        ticket.setDepositId(deposit.getId());
        ticketRepository.save(ticket);

        MockHttpSession buyerSession = loginAs(buyerEmail, "Password123@");
        mockMvc.perform(get("/tickets/" + ticket.getId()).session(buyerSession))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Xác nhận vé hợp lệ")))
                .andExpect(content().string(not(containsString("Báo lỗi vé không hợp lệ"))));

        mockMvc.perform(post("/tickets/" + ticket.getId() + "/received")
                        .session(buyerSession)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());
        Ticket awaitingTicket = ticketRepository.findById(ticket.getId()).orElseThrow();
        assertThat(awaitingTicket.getStatus()).isEqualTo(TicketStatus.AWAITING_USE);
        assertThat(awaitingTicket.getReceivedAt()).isNotNull();
        assertThat(awaitingTicket.getDisputeWindowClosesAt())
                .isEqualTo(awaitingTicket.getEventStartAt().plus(30, ChronoUnit.MINUTES));

        mockMvc.perform(get("/tickets/" + ticket.getId()).session(buyerSession))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Mã QR đang hiển thị")))
                .andExpect(content().string(containsString("e2e-sample-qr.png")));

        Dispute dispute = fileDisputeUseCase.fileDispute(
                ticket.getId(), buyer.getId(), DisputeReason.TICKET_INVALID,
                "Mã QR bị mờ/sai thông tin ghế so với bài đăng, yêu cầu hoàn tiền.");
        createdDisputeIds.add(dispute.getId());
        assertThat(ticketRepository.findById(ticket.getId()).orElseThrow().getStatus())
                .isEqualTo(TicketStatus.DISPUTE_UNDER_REVIEW);

        reviewDisputeUseCase.resolveBuyerWins(dispute.getId(), admin.getId(),
                "Xác minh ảnh QR không hợp lệ so với vé đăng bán.");

        assertThat(ticketRepository.findById(ticket.getId()).orElseThrow().getStatus())
                .isEqualTo(TicketStatus.RESOLVED_BUYER_WINS);
        assertThat(escrowTransactionRepository.findById(escrow.getId()).orElseThrow().getStatus())
                .isEqualTo(EscrowStatus.REFUNDED);
        Deposit forfeited = depositRepository.findById(deposit.getId()).orElseThrow();
        assertThat(forfeited.getStatus()).isEqualTo(DepositStatus.FORFEITED);
        assertThat(forfeited.getFounderCut()).isEqualByComparingTo("14000.00");
        assertThat(forfeited.getBuyerCompensation()).isEqualByComparingTo("186000.00");

        assertThat(walletRepository.findByUserId(buyer.getId()).orElseThrow().getBalance())
                .isEqualByComparingTo("586000.00");

        UserReputationScore sellerRep = reputationRepository.findByUserId(seller.getId()).orElseThrow();
        assertThat(sellerRep.getScore()).isEqualByComparingTo("1.00");
        assertThat(sellerRep.getDisputesLost()).isEqualTo(1);

        UserReputationScore buyerRep = reputationRepository.findByUserId(buyer.getId()).orElseThrow();
        assertThat(buyerRep.getScore()).isEqualByComparingTo("3.00");
        assertThat(buyerRep.getDisputesWon()).isEqualTo(1);
    }
}
