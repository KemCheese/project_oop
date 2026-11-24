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
    @DisplayName("Scheduler nhận vé: Chuyển vé TRANSFERRED quá 30p sang AWAITING_USE (BR-09 1B) và giữ nguyên vé chưa quá 30p / đã AWAITING_USE")
    void shouldSweepOnlyOverdueTransferredTickets() {
        User seller = createAndSaveUser("sched_deliv_seller@test.com", "Password123@", UserRole.USER);
        User buyer = createAndSaveUser("sched_deliv_buyer@test.com", "Password123@", UserRole.USER);

        Instant overdueEventStart = Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        Ticket overdueTicket = createAndSaveTicket(seller.getId(), "Vé Quá Hạn Giao Nhận", TicketType.MOVIE, new BigDecimal("180000.00"), TicketStatus.TRANSFERRED);
        overdueTicket.setBuyerId(buyer.getId());
        overdueTicket.setEventStartAt(overdueEventStart);
        overdueTicket.setTransferredAt(Instant.now().minus(35, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.SECONDS));
        ticketRepository.save(overdueTicket);

        Ticket freshTicket = createAndSaveTicket(seller.getId(), "Vé Chưa Quá 30 Phút", TicketType.MOVIE, new BigDecimal("180000.00"), TicketStatus.TRANSFERRED);
        freshTicket.setBuyerId(buyer.getId());
        freshTicket.setTransferredAt(Instant.now().minus(10, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.SECONDS));
        ticketRepository.save(freshTicket);

        Instant originalReceivedAt = Instant.now().minus(25, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.SECONDS);
        Ticket alreadyAwaiting = createAndSaveTicket(seller.getId(), "Vé Đã Nhận Rồi", TicketType.MOVIE, new BigDecimal("180000.00"), TicketStatus.AWAITING_USE);
        alreadyAwaiting.setBuyerId(buyer.getId());
        alreadyAwaiting.setTransferredAt(Instant.now().minus(40, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.SECONDS));
        alreadyAwaiting.setReceivedAt(originalReceivedAt);
        ticketRepository.save(alreadyAwaiting);

        deliveryTimeoutScheduler.scanDeliveryTimeouts();

        Ticket finalTicket1 = ticketRepository.findById(overdueTicket.getId()).orElseThrow();
        assertThat(finalTicket1.getStatus()).isEqualTo(TicketStatus.AWAITING_USE);
        assertThat(finalTicket1.getReceivedAt()).isNotNull();
        assertThat(finalTicket1.getDisputeWindowClosesAt())
                .isEqualTo(overdueEventStart.plus(30, ChronoUnit.MINUTES));

        Ticket finalTicket2 = ticketRepository.findById(freshTicket.getId()).orElseThrow();
        assertThat(finalTicket2.getStatus()).isEqualTo(TicketStatus.TRANSFERRED);
        assertThat(finalTicket2.getReceivedAt()).isNull();

        Ticket finalTicket3 = ticketRepository.findById(alreadyAwaiting.getId()).orElseThrow();
        assertThat(finalTicket3.getStatus()).isEqualTo(TicketStatus.AWAITING_USE);
        assertThat(finalTicket3.getReceivedAt()).isEqualTo(originalReceivedAt);
    }
}
