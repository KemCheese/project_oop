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

@DisplayName("Hệ Thống - Luồng 4: Seller Quá Hạn Nạp Cọc 30 Phút (UC-04b -> Tự Hủy Vé, Hoàn 100% Tiền Buyer, Phạt -15đ Seller)")
class DepositTimeoutCancellationFlowTest extends AbstractSystemIntegrationTest {

    @Autowired
    private DepositTimeoutScheduler depositTimeoutScheduler;

    @Test
    @DisplayName("Scheduler quét vé quá hạn cọc 30p: Vé chuyển CANCELLED, Escrow hoàn tiền, Seller bị phạt -15đ uy tín")
    void shouldCancelTicketAndRefundBuyerWhenSellerDepositTimesOut() {
        // 1. Arrange: Tạo User
        User seller = createAndSaveUser("flaky_seller@test.com", "Password123@", UserRole.USER);
        User buyer = createAndSaveUser("waiting_buyer@test.com", "Password123@", UserRole.USER);

        UserReputationScore sellerRep = reputationRepository.findByUserId(seller.getId()).orElseThrow();
        sellerRep.setScore(new BigDecimal("90.00"));
        reputationRepository.save(sellerRep);

        // 2. Tạo vé và giao dịch ký quỹ Escrow
        BigDecimal salePrice = new BigDecimal("600000.00");
        Ticket ticket = createAndSaveTicket(seller.getId(), "Vé Concert Ca Sĩ Quốc Tế", TicketType.CONCERT, salePrice, TicketStatus.PAYMENT_ESCROWED);

        EscrowTransaction escrow = createAndSaveEscrow(ticket.getId(), buyer.getId(), salePrice, EscrowStatus.HELD);
        ticket.setBuyerId(buyer.getId());
        ticket.setEscrowTransactionId(escrow.getId());
        // Giả lập thời điểm escrowedAt cách đây 35 phút (quá hạn 30 phút theo UC-04b)
        ticket.setEscrowedAt(Instant.now().minus(35, ChronoUnit.MINUTES));
        ticket = ticketRepository.save(ticket);

        // 3. Kích hoạt Scheduler quét tác vụ nền
        depositTimeoutScheduler.scanDepositTimeouts();

        // 4. Assertions: Kiểm tra tính đúng đắn theo UC-04b
        // 4.1 Vé chuyển CANCELLED (tuyệt đối không quay lại LISTED)
        Ticket finalTicket = ticketRepository.findById(ticket.getId()).orElseThrow();
        assertThat(finalTicket.getStatus()).isEqualTo(TicketStatus.CANCELLED);

        // 4.2 Escrow hoàn tiền 100% cho Buyer
        EscrowTransaction finalEscrow = escrowTransactionRepository.findById(escrow.getId()).orElseThrow();
        assertThat(finalEscrow.getStatus()).isEqualTo(EscrowStatus.REFUNDED);

        // 4.3 Phạt trừ -15 điểm uy tín đối với Seller do bỏ cọc giao dịch (90.00 -> 75.00)
        UserReputationScore updatedSellerRep = reputationRepository.findByUserId(seller.getId()).orElseThrow();
        assertThat(updatedSellerRep.getScore()).isEqualByComparingTo(new BigDecimal("75.00"));
    }
}
