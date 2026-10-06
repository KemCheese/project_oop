package com.yourapp.ticketpass.system.scheduler;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.yourapp.ticketpass.system.base.AbstractSystemIntegrationTest;
import com.yourapp.ticketpass.ticket.application.DeliveryTimeoutScheduler;
import com.yourapp.ticketpass.ticket.domain.Ticket;
import com.yourapp.ticketpass.ticket.domain.TicketStatus;
import com.yourapp.ticketpass.ticket.domain.TicketType;
import com.yourapp.ticketpass.user.domain.User;
import com.yourapp.ticketpass.user.domain.UserRole;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Phi Chức Năng - Scheduler 2: Tính Toàn Vẹn & Cách Ly Của Tác Vụ Nhận Vé (DeliveryTimeoutScheduler)")
class DeliveryTimeoutSchedulerIntegrationTest extends AbstractSystemIntegrationTest {

    @Autowired
    private DeliveryTimeoutScheduler deliveryTimeoutScheduler;

    @Test
    @DisplayName("Scheduler nhận vé: Chuyển vé quá hạn sang AWAITING_USE và bỏ qua hoàn toàn các vé TECHNICAL_ISSUE")
    void shouldSweepOverdueTicketsAndSkipTechnicalIssueTickets() {
        // Arrange
        User seller = createAndSaveUser("sched_deliv_seller@test.com", "Password123@", UserRole.USER);
        User buyer = createAndSaveUser("sched_deliv_buyer@test.com", "Password123@", UserRole.USER);

        // Vé 1: TRANSFERRED quá 35 phút
        Ticket overdueTicket = createAndSaveTicket(seller.getId(), "Vé Quá Hạn Giao Nhận", TicketType.MOVIE, new BigDecimal("180000.00"), TicketStatus.TRANSFERRED);
        overdueTicket.setBuyerId(buyer.getId());
        overdueTicket.setTransferredAt(Instant.now().minus(35, ChronoUnit.MINUTES));
        ticketRepository.save(overdueTicket);

        // Vé 2: Đang báo lỗi kỹ thuật TECHNICAL_ISSUE nhưng transferredAt cũng quá 35 phút
        Ticket issueTicket = createAndSaveTicket(seller.getId(), "Vé Báo Lỗi QR", TicketType.MOVIE, new BigDecimal("180000.00"), TicketStatus.TECHNICAL_ISSUE);
        issueTicket.setBuyerId(buyer.getId());
        issueTicket.setTransferredAt(Instant.now().minus(35, ChronoUnit.MINUTES));
        ticketRepository.save(issueTicket);

        // Act
        deliveryTimeoutScheduler.scanDeliveryTimeouts();

        // Assert
        Ticket finalTicket1 = ticketRepository.findById(overdueTicket.getId()).orElseThrow();
        assertThat(finalTicket1.getStatus()).isEqualTo(TicketStatus.AWAITING_USE);
        assertThat(finalTicket1.getReceivedAt()).isNotNull();

        // Vé 2 phải được bảo vệ nguyên vẹn ở trạng thái TECHNICAL_ISSUE
        Ticket finalTicket2 = ticketRepository.findById(issueTicket.getId()).orElseThrow();
        assertThat(finalTicket2.getStatus()).isEqualTo(TicketStatus.TECHNICAL_ISSUE);
    }
}
