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
    @DisplayName("Admin phán quyết SELLER_WINS: Tiền giải ngân cho Seller, Cọc hoàn trả, Buyer -15đ, Seller +1đ uy tín")
    void shouldExecuteSellerWinsDisputeResolutionFlow() {
        // 1. Arrange: Tạo User
        User seller = createAndSaveUser("honest_seller@test.com", "Password123@", UserRole.USER);
        User buyer = createAndSaveUser("malicious_buyer@test.com", "Password123@", UserRole.USER);
        User admin = createAndSaveUser("system_admin2@test.com", "Password123@", UserRole.ADMIN);

        // Thiết lập điểm ban đầu
        UserReputationScore sellerRep = reputationRepository.findByUserId(seller.getId()).orElseThrow();
        sellerRep.setScore(new BigDecimal("90.00"));
        reputationRepository.save(sellerRep);

        UserReputationScore buyerRep = reputationRepository.findByUserId(buyer.getId()).orElseThrow();
        buyerRep.setScore(new BigDecimal("90.00"));
        reputationRepository.save(buyerRep);

        // 2. Tạo vé và giao dịch
        BigDecimal salePrice = new BigDecimal("300000.00");
        Ticket ticket = createAndSaveTicket(seller.getId(), "Vé Xe Khách Hà Nội - SaPa", TicketType.BUS, salePrice, TicketStatus.LISTED);

        EscrowTransaction escrow = createAndSaveEscrow(ticket.getId(), buyer.getId(), salePrice, EscrowStatus.HELD);
        Deposit deposit = createAndSaveDeposit(ticket.getId(), seller.getId(), new BigDecimal("150000.00"), DepositStatus.LOCKED);

        ticket.setBuyerId(buyer.getId());
        ticket.setEscrowTransactionId(escrow.getId());
        ticket.setDepositId(deposit.getId());
        ticket.setStatus(TicketStatus.DISPUTE_UNDER_REVIEW);
        ticket = ticketRepository.save(ticket);

        // 3. Khởi tạo khiếu nại
        Dispute dispute = createAndSaveDispute(ticket.getId(), buyer.getId(), seller.getId(), DisputeReason.OTHER, DisputeStatus.UNDER_REVIEW);

        // 4. Admin phán quyết SELLER_WINS (Người bán cung cấp hình ảnh check-in thực tế khớp danh sách hành khách)
        reviewDisputeUseCase.resolveSellerWins(dispute.getId(), admin.getId(), "Người bán cung cấp biên bản nhà xe xác nhận người mua đã lên xe bình thường.");

        // 5. Assertions
        // 5.1 Dispute status
        Dispute finalDispute = disputeRepository.findById(dispute.getId()).orElseThrow();
        assertThat(finalDispute.getStatus()).isEqualTo(DisputeStatus.RESOLVED_SELLER_WINS);

        // 5.2 Trạng thái vé
        Ticket finalTicket = ticketRepository.findById(ticket.getId()).orElseThrow();
        assertThat(finalTicket.getStatus()).isEqualTo(TicketStatus.RESOLVED_SELLER_WINS);

        // 5.3 Escrow (Giải ngân) & Deposit (Hoàn cọc)
        EscrowTransaction finalEscrow = escrowTransactionRepository.findById(escrow.getId()).orElseThrow();
        assertThat(finalEscrow.getStatus()).isEqualTo(EscrowStatus.RELEASED);

        Deposit finalDeposit = depositRepository.findById(deposit.getId()).orElseThrow();
        assertThat(finalDeposit.getStatus()).isEqualTo(DepositStatus.REFUNDED);

        // 5.4 Observer Pattern: Buyer bị trừ -15đ (90 -> 75), Seller được cộng +1đ (90 -> 91)
        UserReputationScore updatedBuyerRep = reputationRepository.findByUserId(buyer.getId()).orElseThrow();
        assertThat(updatedBuyerRep.getScore()).isEqualByComparingTo(new BigDecimal("75.00"));
        assertThat(updatedBuyerRep.getDisputesLost()).isEqualTo(1);

        UserReputationScore updatedSellerRep = reputationRepository.findByUserId(seller.getId()).orElseThrow();
        assertThat(updatedSellerRep.getScore()).isEqualByComparingTo(new BigDecimal("91.00"));
        assertThat(updatedSellerRep.getDisputesWon()).isEqualTo(1);
    }
}
