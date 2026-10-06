package com.yourapp.ticketpass.system.flow;

import java.math.BigDecimal;
import java.time.Instant;

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

@DisplayName("Hệ Thống - Luồng 2: Tranh Chấp Buyer Thắng (Vé Giả / Lỗi -> Hoàn 100% + Bồi Thường Cọc + Phạt Uy Tín Seller)")
class DisputeBuyerWinsRefundFlowTest extends AbstractSystemIntegrationTest {

    @Autowired
    private ReviewDisputeUseCase reviewDisputeUseCase;

    @Test
    @DisplayName("Admin phán quyết BUYER_WINS: Escrow hoàn trả, Cọc bị tịch thu bồi thường, Seller -15đ, Buyer +1đ uy tín")
    void shouldExecuteBuyerWinsDisputeResolutionFlow() {
        // 1. Arrange: Tạo User
        User seller = createAndSaveUser("dishonest_seller@test.com", "Password123@", UserRole.USER);
        User buyer = createAndSaveUser("victimized_buyer@test.com", "Password123@", UserRole.USER);
        User admin = createAndSaveUser("system_admin@test.com", "Password123@", UserRole.ADMIN);

        // Thiết lập điểm ban đầu
        UserReputationScore sellerRep = reputationRepository.findByUserId(seller.getId()).orElseThrow();
        sellerRep.setScore(new BigDecimal("90.00"));
        reputationRepository.save(sellerRep);

        UserReputationScore buyerRep = reputationRepository.findByUserId(buyer.getId()).orElseThrow();
        buyerRep.setScore(new BigDecimal("90.00"));
        reputationRepository.save(buyerRep);

        // 2. Tạo vé và giao dịch đã nạp tiền + cọc
        BigDecimal salePrice = new BigDecimal("400000.00");
        Ticket ticket = createAndSaveTicket(seller.getId(), "Vé Xem Phim Dune Part 2", TicketType.MOVIE, salePrice, TicketStatus.LISTED);

        EscrowTransaction escrow = createAndSaveEscrow(ticket.getId(), buyer.getId(), salePrice, EscrowStatus.HELD);
        Deposit deposit = createAndSaveDeposit(ticket.getId(), seller.getId(), new BigDecimal("200000.00"), DepositStatus.LOCKED);

        ticket.setBuyerId(buyer.getId());
        ticket.setEscrowTransactionId(escrow.getId());
        ticket.setDepositId(deposit.getId());
        ticket.setTransferredAt(Instant.now());
        ticket.setStatus(TicketStatus.DISPUTE_UNDER_REVIEW);
        ticket = ticketRepository.save(ticket);

        // 3. Khởi tạo hồ sơ tranh chấp
        Dispute dispute = createAndSaveDispute(ticket.getId(), buyer.getId(), seller.getId(), DisputeReason.TICKET_INVALID, DisputeStatus.UNDER_REVIEW);

        // 4. Admin phán quyết BUYER_WINS
        reviewDisputeUseCase.resolveBuyerWins(dispute.getId(), admin.getId(), "Mã vé đã qua sử dụng tại quầy rạp chiếu. Khiếu nại hợp lệ.");

        // 5. Assertions
        // 5.1 Hồ sơ Dispute
        Dispute finalDispute = disputeRepository.findById(dispute.getId()).orElseThrow();
        assertThat(finalDispute.getStatus()).isEqualTo(DisputeStatus.RESOLVED_BUYER_WINS);
        assertThat(finalDispute.getReviewedByAdminId()).isEqualTo(admin.getId());
        assertThat(finalDispute.getBuyerCompensation()).isGreaterThan(BigDecimal.ZERO);

        // 5.2 Trạng thái vé
        Ticket finalTicket = ticketRepository.findById(ticket.getId()).orElseThrow();
        assertThat(finalTicket.getStatus()).isEqualTo(TicketStatus.RESOLVED_BUYER_WINS);

        // 5.3 Trạng thái Escrow (Hoàn tiền) và Deposit (Tịch thu)
        EscrowTransaction finalEscrow = escrowTransactionRepository.findById(escrow.getId()).orElseThrow();
        assertThat(finalEscrow.getStatus()).isEqualTo(EscrowStatus.REFUNDED);

        Deposit finalDeposit = depositRepository.findById(deposit.getId()).orElseThrow();
        assertThat(finalDeposit.getStatus()).isEqualTo(DepositStatus.FORFEITED);

        // 5.4 Observer Pattern: Seller bị trừ -15đ (90 -> 75), Buyer được cộng +1đ (90 -> 91)
        UserReputationScore updatedSellerRep = reputationRepository.findByUserId(seller.getId()).orElseThrow();
        assertThat(updatedSellerRep.getScore()).isEqualByComparingTo(new BigDecimal("75.00"));
        assertThat(updatedSellerRep.getDisputesLost()).isEqualTo(1);

        UserReputationScore updatedBuyerRep = reputationRepository.findByUserId(buyer.getId()).orElseThrow();
        assertThat(updatedBuyerRep.getScore()).isEqualByComparingTo(new BigDecimal("91.00"));
        assertThat(updatedBuyerRep.getDisputesWon()).isEqualTo(1);
    }
}
