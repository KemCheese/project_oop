package com.yourapp.ticketpass.system.flow;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.yourapp.ticketpass.dispute.application.ReviewDisputeUseCase;
import com.yourapp.ticketpass.dispute.domain.Dispute;
import com.yourapp.ticketpass.dispute.domain.DisputeReason;
import com.yourapp.ticketpass.dispute.domain.DisputeStatus;
import com.yourapp.ticketpass.payment.domain.Deposit;
import com.yourapp.ticketpass.payment.domain.DepositStatus;
import com.yourapp.ticketpass.payment.domain.EscrowStatus;
import com.yourapp.ticketpass.payment.domain.EscrowTransaction;
import com.yourapp.ticketpass.reputation.domain.UserReputationScore;
import com.yourapp.ticketpass.system.base.AbstractSystemIntegrationTest;
import com.yourapp.ticketpass.ticket.domain.Ticket;
import com.yourapp.ticketpass.ticket.domain.TicketStatus;
import com.yourapp.ticketpass.ticket.domain.TicketType;
import com.yourapp.ticketpass.user.domain.User;
import com.yourapp.ticketpass.user.domain.UserRole;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Hệ Thống - Luồng 3: Tranh Chấp Seller Thắng (Buyer Vu Khống -> Giải Ngân & Hoàn Cọc Seller + Phạt Uy Tín Buyer)")
class DisputeSellerWinsPayoutFlowTest extends AbstractSystemIntegrationTest {

    @Autowired
    private ReviewDisputeUseCase reviewDisputeUseCase;

    @Test
    @DisplayName("Admin phán quyết SELLER_WINS: Tiền giải ngân cho Seller, Cọc hoàn trả, Buyer -2đ, Seller giữ nguyên điểm")
    void shouldExecuteSellerWinsDisputeResolutionFlow() {
        User seller = createAndSaveUser("honest_seller@test.com", "Password123@", UserRole.USER);
        User buyer = createAndSaveUser("malicious_buyer@test.com", "Password123@", UserRole.USER);
        User admin = createAndSaveUser("system_admin2@test.com", "Password123@", UserRole.ADMIN);

        UserReputationScore sellerRep = reputationRepository.findByUserId(seller.getId()).orElseThrow();
        sellerRep.setScore(new BigDecimal("4.00"));
        reputationRepository.save(sellerRep);

        UserReputationScore buyerRep = reputationRepository.findByUserId(buyer.getId()).orElseThrow();
        buyerRep.setScore(new BigDecimal("4.00"));
        reputationRepository.save(buyerRep);

        BigDecimal salePrice = new BigDecimal("300000.00");
        Ticket ticket = createAndSaveTicket(seller.getId(), "Vé Xe Khách Hà Nội - SaPa", TicketType.BUS, salePrice, TicketStatus.LISTED);

        EscrowTransaction escrow = createAndSaveEscrow(ticket.getId(), buyer.getId(), salePrice, EscrowStatus.HELD);
        Deposit deposit = createAndSaveDeposit(ticket.getId(), seller.getId(), new BigDecimal("150000.00"), DepositStatus.LOCKED);

        ticket.setBuyerId(buyer.getId());
        ticket.setEscrowTransactionId(escrow.getId());
        ticket.setDepositId(deposit.getId());
        ticket.setStatus(TicketStatus.DISPUTE_UNDER_REVIEW);
        ticket = ticketRepository.save(ticket);

        Dispute dispute = createAndSaveDispute(ticket.getId(), buyer.getId(), seller.getId(), DisputeReason.OTHER, DisputeStatus.UNDER_REVIEW);

        reviewDisputeUseCase.resolveSellerWins(dispute.getId(), admin.getId(), "Người bán cung cấp biên bản nhà xe xác nhận người mua đã lên xe bình thường.");

        Dispute finalDispute = disputeRepository.findById(dispute.getId()).orElseThrow();
        assertThat(finalDispute.getStatus()).isEqualTo(DisputeStatus.RESOLVED_SELLER_WINS);

        Ticket finalTicket = ticketRepository.findById(ticket.getId()).orElseThrow();
        assertThat(finalTicket.getStatus()).isEqualTo(TicketStatus.RESOLVED_SELLER_WINS);

        EscrowTransaction finalEscrow = escrowTransactionRepository.findById(escrow.getId()).orElseThrow();
        assertThat(finalEscrow.getStatus()).isEqualTo(EscrowStatus.RELEASED);

        Deposit finalDeposit = depositRepository.findById(deposit.getId()).orElseThrow();
        assertThat(finalDeposit.getStatus()).isEqualTo(DepositStatus.REFUNDED);

        UserReputationScore updatedBuyerRep = reputationRepository.findByUserId(buyer.getId()).orElseThrow();
        assertThat(updatedBuyerRep.getScore()).isEqualByComparingTo(new BigDecimal("2.00"));
        assertThat(updatedBuyerRep.getDisputesLost()).isEqualTo(1);

        UserReputationScore updatedSellerRep = reputationRepository.findByUserId(seller.getId()).orElseThrow();
        assertThat(updatedSellerRep.getScore()).isEqualByComparingTo(new BigDecimal("4.00"));
        assertThat(updatedSellerRep.getDisputesWon()).isEqualTo(1);
    }
}
