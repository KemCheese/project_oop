package com.yourapp.ticketpass.system.flow;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.yourapp.ticketpass.payment.application.ReleaseFundsUseCase;
import com.yourapp.ticketpass.payment.domain.Deposit;
import com.yourapp.ticketpass.payment.domain.DepositStatus;
import com.yourapp.ticketpass.payment.domain.EscrowStatus;
import com.yourapp.ticketpass.payment.domain.EscrowTransaction;
import com.yourapp.ticketpass.reputation.domain.UserReputationScore;
import com.yourapp.ticketpass.shared.event.CompletionReason;
import com.yourapp.ticketpass.system.base.AbstractSystemIntegrationTest;
import com.yourapp.ticketpass.ticket.application.TicketStateMachineService;
import com.yourapp.ticketpass.ticket.domain.Ticket;
import com.yourapp.ticketpass.ticket.domain.TicketStatus;
import com.yourapp.ticketpass.ticket.domain.TicketType;
import com.yourapp.ticketpass.user.domain.User;
import com.yourapp.ticketpass.user.domain.UserRole;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Hệ Thống - Luồng 1: Happy Path Giao Dịch Vé Toàn Diện (Escrow -> Cọc -> Hoàn Tất -> Giải Ngân & Điểm Uy Tín)")
class HappyPathEscrowTransactionFlowTest extends AbstractSystemIntegrationTest {

    @Autowired
    private ReleaseFundsUseCase releaseFundsUseCase;

    @Autowired
    private TicketStateMachineService ticketStateMachineService;

    @Test
    @DisplayName("Chu trình giao dịch vé thành công: Tiền giải ngân cho Seller, Cọc hoàn trả, Điểm uy tín 2 bên tăng +2.00")
    void shouldExecuteFullHappyPathTransactionLifecycleSuccessfully() {
        User seller = createAndSaveUser("happy_seller@test.com", "Password123@", UserRole.USER);
        User buyer = createAndSaveUser("happy_buyer@test.com", "Password123@", UserRole.USER);

        UserReputationScore sellerRep = reputationRepository.findByUserId(seller.getId()).orElseThrow();
        sellerRep.setScore(new BigDecimal("3.00"));
        reputationRepository.save(sellerRep);

        UserReputationScore buyerRep = reputationRepository.findByUserId(buyer.getId()).orElseThrow();
        buyerRep.setScore(new BigDecimal("3.00"));
        reputationRepository.save(buyerRep);

        BigDecimal salePrice = new BigDecimal("500000.00");
        Ticket ticket = createAndSaveTicket(seller.getId(), "Concert Anh Trai Vượt Ngàn Chông Gai", TicketType.CONCERT, salePrice, TicketStatus.LISTED);

        EscrowTransaction escrow = createAndSaveEscrow(ticket.getId(), buyer.getId(), salePrice, EscrowStatus.HELD);
        ticket.setBuyerId(buyer.getId());
        ticket.setEscrowTransactionId(escrow.getId());
        ticket.setEscrowedAt(Instant.now());
        ticketRepository.save(ticket);
        ticketStateMachineService.transition(ticket.getId(), TicketStatus.PAYMENT_ESCROWED);

        BigDecimal depositAmount = salePrice.multiply(new BigDecimal("0.5"));
        Deposit deposit = createAndSaveDeposit(ticket.getId(), seller.getId(), depositAmount, DepositStatus.LOCKED);
        ticket = ticketRepository.findById(ticket.getId()).orElseThrow();
        ticket.setDepositId(deposit.getId());
        ticketRepository.save(ticket);
        ticketStateMachineService.transition(ticket.getId(), TicketStatus.DEPOSIT_LOCKED);

        ticketStateMachineService.transition(ticket.getId(), TicketStatus.AWAITING_USE);
        ticket = ticketRepository.findById(ticket.getId()).orElseThrow();
        ticket.setReceivedAt(Instant.now());
        ticket.setDisputeWindowClosesAt(Instant.now().minus(1, ChronoUnit.HOURS));
        ticketRepository.save(ticket);

        releaseFundsUseCase.release(ticket.getId(), CompletionReason.AUTO_COMPLETE);

        Ticket finalTicket = ticketRepository.findById(ticket.getId()).orElseThrow();
        assertThat(finalTicket.getStatus()).isEqualTo(TicketStatus.AUTO_COMPLETE);

        EscrowTransaction finalEscrow = escrowTransactionRepository.findById(escrow.getId()).orElseThrow();
        assertThat(finalEscrow.getStatus()).isEqualTo(EscrowStatus.RELEASED);

        Deposit finalDeposit = depositRepository.findById(deposit.getId()).orElseThrow();
        assertThat(finalDeposit.getStatus()).isEqualTo(DepositStatus.REFUNDED);

        UserReputationScore updatedSellerRep = reputationRepository.findByUserId(seller.getId()).orElseThrow();
        assertThat(updatedSellerRep.getScore()).isEqualByComparingTo(new BigDecimal("4.00"));
        assertThat(updatedSellerRep.getSuccessfulTransactions()).isEqualTo(1);
        assertThat(updatedSellerRep.getTotalTransactions()).isEqualTo(1);

        UserReputationScore updatedBuyerRep = reputationRepository.findByUserId(buyer.getId()).orElseThrow();
        assertThat(updatedBuyerRep.getScore()).isEqualByComparingTo(new BigDecimal("4.00"));
        assertThat(updatedBuyerRep.getSuccessfulTransactions()).isEqualTo(1);
        assertThat(updatedBuyerRep.getTotalTransactions()).isEqualTo(1);
    }
}
