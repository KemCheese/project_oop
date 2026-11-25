package com.yourapp.ticketpass.system.flow;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.yourapp.ticketpass.payment.application.DepositTimeoutScheduler;
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

@DisplayName("Hệ Thống - Luồng 4: Seller Quá Hạn Nạp Cọc 30 Phút (UC-04b -> Tự Hủy Vé, Hoàn 100% Tiền Buyer, Phạt -1đ Seller)")
class DepositTimeoutCancellationFlowTest extends AbstractSystemIntegrationTest {

    @Autowired
    private DepositTimeoutScheduler depositTimeoutScheduler;

    @Test
    @DisplayName("Scheduler quét vé quá hạn cọc 30p: Vé chuyển CANCELLED, Escrow hoàn tiền, Seller bị phạt -1đ uy tín")
    void shouldCancelTicketAndRefundBuyerWhenSellerDepositTimesOut() {
        User seller = createAndSaveUser("flaky_seller@test.com", "Password123@", UserRole.USER);
        User buyer = createAndSaveUser("waiting_buyer@test.com", "Password123@", UserRole.USER);

        UserReputationScore sellerRep = reputationRepository.findByUserId(seller.getId()).orElseThrow();
        sellerRep.setScore(new BigDecimal("4.00"));
        reputationRepository.save(sellerRep);

        BigDecimal salePrice = new BigDecimal("600000.00");
        Ticket ticket = createAndSaveTicket(seller.getId(), "Vé Concert Ca Sĩ Quốc Tế", TicketType.CONCERT, salePrice, TicketStatus.PAYMENT_ESCROWED);

        EscrowTransaction escrow = createAndSaveEscrow(ticket.getId(), buyer.getId(), salePrice, EscrowStatus.HELD);
        ticket.setBuyerId(buyer.getId());
        ticket.setEscrowTransactionId(escrow.getId());
        ticket.setEscrowedAt(Instant.now().minus(35, ChronoUnit.MINUTES));
        ticket = ticketRepository.save(ticket);

        depositTimeoutScheduler.scanDepositTimeouts();

        Ticket finalTicket = ticketRepository.findById(ticket.getId()).orElseThrow();
        assertThat(finalTicket.getStatus()).isEqualTo(TicketStatus.CANCELLED);

        EscrowTransaction finalEscrow = escrowTransactionRepository.findById(escrow.getId()).orElseThrow();
        assertThat(finalEscrow.getStatus()).isEqualTo(EscrowStatus.REFUNDED);

        UserReputationScore updatedSellerRep = reputationRepository.findByUserId(seller.getId()).orElseThrow();
        assertThat(updatedSellerRep.getScore()).isEqualByComparingTo(new BigDecimal("3.00"));
    }
}
