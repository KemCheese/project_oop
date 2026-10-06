package com.yourapp.ticketpass.system.flow;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.yourapp.ticketpass.system.base.AbstractSystemIntegrationTest;
import com.yourapp.ticketpass.ticket.application.DeliveryTimeoutScheduler;
import com.yourapp.ticketpass.ticket.application.TransferTicketService;
import com.yourapp.ticketpass.ticket.domain.Ticket;
import com.yourapp.ticketpass.ticket.domain.TicketStatus;
import com.yourapp.ticketpass.ticket.domain.TicketType;
import com.yourapp.ticketpass.user.domain.User;
import com.yourapp.ticketpass.user.domain.UserRole;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Hệ Thống - Luồng 5: Quản Lý Giao Nhận & Sự Cố QR (UC-06b Auto-Confirm 30p & Báo Lỗi TECHNICAL_ISSUE)")
class DeliveryTimeoutAndQrIssueFlowTest extends AbstractSystemIntegrationTest {

    @Autowired
    private DeliveryTimeoutScheduler deliveryTimeoutScheduler;

    @Autowired
    private TransferTicketService transferTicketService;

    @Test
    @DisplayName("Nhánh 1 (Auto-Confirm): Sau 30p Buyer không phản hồi -> Scheduler tự chuyển sang AWAITING_USE")
    void shouldAutoConfirmDeliveryAfterThirtyMinutesTimeout() {
        // 1. Arrange
        User seller = createAndSaveUser("seller_deliv1@test.com", "Password123@", UserRole.USER);
        User buyer = createAndSaveUser("buyer_deliv1@test.com", "Password123@", UserRole.USER);

        Ticket ticket = createAndSaveTicket(seller.getId(), "Vé Xem Phim Avatar 3", TicketType.MOVIE, new BigDecimal("200000.00"), TicketStatus.TRANSFERRED);
        ticket.setBuyerId(buyer.getId());
        // Giả lập transferredAt cách đây 35 phút
        ticket.setTransferredAt(Instant.now().minus(35, ChronoUnit.MINUTES));
        ticket = ticketRepository.save(ticket);

        // 2. Kích hoạt Scheduler
        deliveryTimeoutScheduler.scanDeliveryTimeouts();

        // 3. Assert: Vé tự chuyển sang AWAITING_USE, ghi nhận receivedAt
        Ticket finalTicket = ticketRepository.findById(ticket.getId()).orElseThrow();
        assertThat(finalTicket.getStatus()).isEqualTo(TicketStatus.AWAITING_USE);
        assertThat(finalTicket.getReceivedAt()).isNotNull();
        assertThat(finalTicket.getDisputeWindowClosesAt()).isNotNull();
    }

    @Test
    @DisplayName("Nhánh 2 (Báo lỗi kỹ thuật QR): Trong 30p Buyer báo ảnh QR hỏng -> Vé chuyển TECHNICAL_ISSUE & đóng băng Scheduler")
    void shouldFreezeTimeoutWhenBuyerReportsQrTechnicalIssue() {
        // 1. Arrange
        User seller = createAndSaveUser("seller_deliv2@test.com", "Password123@", UserRole.USER);
        User buyer = createAndSaveUser("buyer_deliv2@test.com", "Password123@", UserRole.USER);

        Ticket ticket = createAndSaveTicket(seller.getId(), "Vé Xe Khách Đi Đà Lạt", TicketType.BUS, new BigDecimal("350000.00"), TicketStatus.TRANSFERRED);
        ticket.setBuyerId(buyer.getId());
        ticket.setTransferredAt(Instant.now().minus(10, ChronoUnit.MINUTES)); // Đang trong cửa sổ 30 phút
        ticket = ticketRepository.save(ticket);

        // 2. Buyer báo lỗi kỹ thuật ảnh QR không quét được
        transferTicketService.reportTechnicalIssue(ticket.getId(), buyer.getId(), "Ảnh QR bị mờ nét, không thể trích xuất mã vạch.");

        // 3. Assert: Vé chuyển sang TECHNICAL_ISSUE
        Ticket issueTicket = ticketRepository.findById(ticket.getId()).orElseThrow();
        assertThat(issueTicket.getStatus()).isEqualTo(TicketStatus.TECHNICAL_ISSUE);

        // 4. Giả lập thời gian trôi qua 40 phút và Scheduler quét -> Không được tự động chuyển AWAITING_USE
        issueTicket.setTransferredAt(Instant.now().minus(40, ChronoUnit.MINUTES));
        ticketRepository.save(issueTicket);

        deliveryTimeoutScheduler.scanDeliveryTimeouts();

        Ticket frozenTicket = ticketRepository.findById(ticket.getId()).orElseThrow();
        assertThat(frozenTicket.getStatus()).isEqualTo(TicketStatus.TECHNICAL_ISSUE);
    }
}
