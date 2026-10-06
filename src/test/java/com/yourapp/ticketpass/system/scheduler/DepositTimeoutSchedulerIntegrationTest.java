package com.yourapp.ticketpass.system.scheduler;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.yourapp.ticketpass.payment.application.DepositTimeoutScheduler;
import com.yourapp.ticketpass.payment.domain.EscrowStatus;
import com.yourapp.ticketpass.payment.domain.EscrowTransaction;
import com.yourapp.ticketpass.system.base.AbstractSystemIntegrationTest;
import com.yourapp.ticketpass.ticket.domain.Ticket;
import com.yourapp.ticketpass.ticket.domain.TicketStatus;
import com.yourapp.ticketpass.ticket.domain.TicketType;
import com.yourapp.ticketpass.user.domain.User;
import com.yourapp.ticketpass.user.domain.UserRole;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Phi Chức Năng - Scheduler 1: Tính Toàn Vẹn & Idempotent Của Tác Vụ Quét Cọc (DepositTimeoutScheduler)")
class DepositTimeoutSchedulerIntegrationTest extends AbstractSystemIntegrationTest {

    @Autowired
    private DepositTimeoutScheduler depositTimeoutScheduler;

    @Test
    @DisplayName("Tính Idempotent: Quét nhiều lần không gây lỗi và không quét lại vé đã bị hủy")
    void shouldBeIdempotentWhenRunningMultipleTimes() {
        // Arrange
        User seller = createAndSaveUser("sched_seller@test.com", "Password123@", UserRole.USER);
        User buyer = createAndSaveUser("sched_buyer@test.com", "Password123@", UserRole.USER);

        // Vé 1: Đã quá hạn 35 phút
        Ticket overdueTicket = createAndSaveTicket(seller.getId(), "Vé Quá Hạn Cọc", TicketType.CONCERT, new BigDecimal("400000.00"), TicketStatus.PAYMENT_ESCROWED);
        EscrowTransaction escrow1 = createAndSaveEscrow(overdueTicket.getId(), buyer.getId(), new BigDecimal("400000.00"), EscrowStatus.HELD);
        overdueTicket.setBuyerId(buyer.getId());
        overdueTicket.setEscrowTransactionId(escrow1.getId());
        overdueTicket.setEscrowedAt(Instant.now().minus(35, ChronoUnit.MINUTES));
        ticketRepository.save(overdueTicket);

        // Vé 2: Mới nạp tiền 10 phút trước (Chưa quá hạn 30p)
        Ticket freshTicket = createAndSaveTicket(seller.getId(), "Vé Mới Nạp Escrow", TicketType.CONCERT, new BigDecimal("400000.00"), TicketStatus.PAYMENT_ESCROWED);
        EscrowTransaction escrow2 = createAndSaveEscrow(freshTicket.getId(), buyer.getId(), new BigDecimal("400000.00"), EscrowStatus.HELD);
        freshTicket.setBuyerId(buyer.getId());
        freshTicket.setEscrowTransactionId(escrow2.getId());
        freshTicket.setEscrowedAt(Instant.now().minus(10, ChronoUnit.MINUTES));
        ticketRepository.save(freshTicket);

        // Act 1: Lần quét đầu tiên
        depositTimeoutScheduler.scanDepositTimeouts();

        // Assert 1: Vé 1 bị hủy, Vé 2 giữ nguyên PAYMENT_ESCROWED
        Ticket ticket1AfterFirstRun = ticketRepository.findById(overdueTicket.getId()).orElseThrow();
        assertThat(ticket1AfterFirstRun.getStatus()).isEqualTo(TicketStatus.CANCELLED);

        Ticket ticket2AfterFirstRun = ticketRepository.findById(freshTicket.getId()).orElseThrow();
        assertThat(ticket2AfterFirstRun.getStatus()).isEqualTo(TicketStatus.PAYMENT_ESCROWED);

        // Act 2: Chạy lại lần quét thứ hai ngay lập tức (Test Idempotency)
        depositTimeoutScheduler.scanDepositTimeouts();

        // Assert 2: Trạng thái không đổi, không có lỗi RuntimeException nào xảy ra
        Ticket ticket1AfterSecondRun = ticketRepository.findById(overdueTicket.getId()).orElseThrow();
        assertThat(ticket1AfterSecondRun.getStatus()).isEqualTo(TicketStatus.CANCELLED);
    }
}
