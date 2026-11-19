package com.yourapp.ticketpass.notification.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yourapp.ticketpass.notification.domain.NotificationType;
import com.yourapp.ticketpass.shared.event.CompletionReason;
import com.yourapp.ticketpass.shared.event.DepositTimeoutEvent;
import com.yourapp.ticketpass.shared.event.DisputeOpenedEvent;
import com.yourapp.ticketpass.shared.event.DisputeOutcome;
import com.yourapp.ticketpass.shared.event.DisputeResolvedEvent;
import com.yourapp.ticketpass.shared.event.DisputeRespondedEvent;
import com.yourapp.ticketpass.shared.event.TicketDepositLockedEvent;
import com.yourapp.ticketpass.shared.event.TicketEscrowPaidEvent;
import com.yourapp.ticketpass.shared.event.TicketExpiredEvent;
import com.yourapp.ticketpass.shared.event.TicketReceivedEvent;
import com.yourapp.ticketpass.shared.event.TicketTransactionCompletedEvent;
import com.yourapp.ticketpass.shared.event.TicketUpcomingReminderEvent;
import com.yourapp.ticketpass.user.domain.StarredTicketRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationEventListenerTest {

    @Mock
    private NotificationService notificationService;

    @Mock
    private StarredTicketRepository starredTicketRepository;

    @InjectMocks
    private NotificationEventListener listener;

    @Test
    @DisplayName("onTicketEscrowPaid: Bắn thông báo ESCROW_PAID cho Người bán")
    void testOnTicketEscrowPaid() {
        TicketEscrowPaidEvent event = new TicketEscrowPaidEvent(
                1L, 10L, 20L, "Vé Blackpink", BigDecimal.valueOf(1500000), Instant.now()
        );

        listener.onTicketEscrowPaid(event);

        verify(notificationService).sendNotification(
                eq(10L),
                eq("Có người mua vé của bạn!"),
                contains("Vé \"Vé Blackpink\" đã có người mua"),
                eq(NotificationType.ESCROW_PAID),
                eq("/tickets/1")
        );
    }

    @Test
    @DisplayName("onTicketDepositLocked: Bắn thông báo DEPOSIT_LOCKED cho Người mua")
    void testOnTicketDepositLocked() {
        TicketDepositLockedEvent event = new TicketDepositLockedEvent(
                2L, 10L, 20L, "Vé Phim Mai", BigDecimal.valueOf(100000), Instant.now()
        );

        listener.onTicketDepositLocked(event);

        verify(notificationService).sendNotification(
                eq(20L),
                eq("Người bán đã đóng cọc vé!"),
                contains("Vé Phim Mai"),
                eq(NotificationType.DEPOSIT_LOCKED),
                eq("/tickets/2")
        );
    }

    @Test
    @DisplayName("onDisputeOpened: Bắn thông báo DISPUTE_FILED cho Người bán")
    void testOnDisputeOpened() {
        DisputeOpenedEvent event = new DisputeOpenedEvent(
                5L, 2L, 10L, 20L, "Vé Xe Giường Nằm", "Vé không đúng ghế", Instant.now()
        );

        listener.onDisputeOpened(event);

        verify(notificationService).sendNotification(
                eq(10L),
                eq("Vé của bạn bị khiếu nại!"),
                contains("Vé không đúng ghế"),
                eq(NotificationType.DISPUTE_FILED),
                eq("/disputes/5")
        );
    }

    @Test
    @DisplayName("onDisputeResponded: Bắn thông báo DISPUTE_RESPONDED cho Người mua")
    void testOnDisputeResponded() {
        DisputeRespondedEvent event = new DisputeRespondedEvent(
                5L, 2L, 10L, 20L, "Vé Xe Giường Nằm", Instant.now()
        );

        listener.onDisputeResponded(event);

        verify(notificationService).sendNotification(
                eq(20L),
                eq("Người bán đã gửi giải trình"),
                contains("Vé Xe Giường Nằm"),
                eq(NotificationType.DISPUTE_RESPONDED),
                eq("/disputes/5")
        );
    }

    @Test
    @DisplayName("onDisputeResolved: Bắn thông báo phán quyết BUYER_WINS cho cả 2 bên")
    void testOnDisputeResolvedBuyerWins() {
        DisputeResolvedEvent event = new DisputeResolvedEvent(
                5L, 2L, 100L, 200L, 20L, 10L,
                DisputeOutcome.BUYER_WINS, BigDecimal.valueOf(50000), BigDecimal.valueOf(5000), BigDecimal.valueOf(45000),
                999L, Instant.now()
        );

        listener.onDisputeResolved(event);

        verify(notificationService).sendNotification(
                eq(20L),
                contains("BẠN THẮNG"),
                any(),
                eq(NotificationType.DISPUTE_RESOLVED),
                eq("/disputes/5")
        );
        verify(notificationService).sendNotification(
                eq(10L),
                contains("NGƯỜI MUA THẮNG"),
                any(),
                eq(NotificationType.DISPUTE_RESOLVED),
                eq("/disputes/5")
        );
    }

    @Test
    @DisplayName("onDepositTimeout: Bắn thông báo DEPOSIT_TIMEOUT cho cả 2 bên")
    void testOnDepositTimeout() {
        DepositTimeoutEvent event = new DepositTimeoutEvent(
                7L, 10L, 20L, "Vé Concert Anh Trai", Instant.now()
        );

        listener.onDepositTimeout(event);

        verify(notificationService).sendNotification(
                eq(20L),
                contains("Hủy giao dịch"),
                contains("hoàn lại an toàn"),
                eq(NotificationType.DEPOSIT_TIMEOUT),
                eq("/profile")
        );
        verify(notificationService).sendNotification(
                eq(10L),
                contains("quá hạn nộp cọc"),
                contains("bị trừ 10 điểm"),
                eq(NotificationType.DEPOSIT_TIMEOUT),
                eq("/profile")
        );
    }

    @Test
    @DisplayName("onTicketReceived: Bắn thông báo TICKET_RECEIVED cho cả 2 bên")
    void testOnTicketReceived() {
        TicketReceivedEvent event = new TicketReceivedEvent(
                8L, 10L, 20L, "Vé Kpop", false, Instant.now()
        );

        listener.onTicketReceived(event);

        verify(notificationService).sendNotification(
                eq(20L),
                contains("thành công"),
                any(),
                eq(NotificationType.TICKET_RECEIVED),
                eq("/tickets/8")
        );
        verify(notificationService).sendNotification(
                eq(10L),
                contains("Người mua đã xác nhận"),
                any(),
                eq(NotificationType.TICKET_RECEIVED),
                eq("/tickets/8")
        );
    }

    @Test
    @DisplayName("onTicketTransactionCompleted: Bắn thông báo TRANSACTION_COMPLETED cho cả 2 bên")
    void testOnTicketTransactionCompleted() {
        TicketTransactionCompletedEvent event = new TicketTransactionCompletedEvent(
                9L, 100L, 200L, 10L, 20L, BigDecimal.valueOf(500000),
                BigDecimal.valueOf(25000), BigDecimal.valueOf(25000), BigDecimal.ZERO,
                BigDecimal.valueOf(475000), BigDecimal.valueOf(250000),
                CompletionReason.AUTO_COMPLETE, Instant.now()
        );

        listener.onTicketTransactionCompleted(event);

        verify(notificationService).sendNotification(
                eq(10L),
                contains("thành công!"),
                contains("+2 điểm uy tín"),
                eq(NotificationType.TRANSACTION_COMPLETED),
                eq("/profile")
        );
        verify(notificationService).sendNotification(
                eq(20L),
                contains("thành công!"),
                contains("+2 điểm uy tín"),
                eq(NotificationType.TRANSACTION_COMPLETED),
                eq("/profile")
        );
    }

    @Test
    @DisplayName("onTicketEscrowPaid: Báo cho cả Seller và các Watchers (loại trừ Buyer và Seller)")
    void testOnTicketEscrowPaid_NotifiesWatchers() {
        TicketEscrowPaidEvent event = new TicketEscrowPaidEvent(
                15L, 10L, 20L, "Vé Concert Taylor Swift", BigDecimal.valueOf(5000000), Instant.now()
        );

        when(starredTicketRepository.findUserIdsByTicketId(15L)).thenReturn(List.of(30L, 20L, 10L, 40L));

        listener.onTicketEscrowPaid(event);

        // Seller nhận ESCROW_PAID
        verify(notificationService).sendNotification(
                eq(10L),
                eq("Có người mua vé của bạn!"),
                contains("Vé Concert Taylor Swift"),
                eq(NotificationType.ESCROW_PAID),
                eq("/tickets/15")
        );

        // Watchers 30L và 40L nhận STARRED_TICKET_BOUGHT
        verify(notificationService).sendNotification(
                eq(30L),
                eq("Vé theo dõi đã có người mua!"),
                contains("vừa có người mua!"),
                eq(NotificationType.STARRED_TICKET_BOUGHT),
                eq("/tickets/15")
        );
        verify(notificationService).sendNotification(
                eq(40L),
                eq("Vé theo dõi đã có người mua!"),
                contains("vừa có người mua!"),
                eq(NotificationType.STARRED_TICKET_BOUGHT),
                eq("/tickets/15")
        );
    }

    @Test
    @DisplayName("onTicketExpired: Báo TICKET_EXPIRED cho Seller và STARRED_TICKET_EXPIRED cho Watchers")
    void testOnTicketExpired_NotifiesSellerAndWatchers() {
        TicketExpiredEvent event = new TicketExpiredEvent(
                88L, 10L, "Vé Xem Phim Đào Phở", Instant.now()
        );

        when(starredTicketRepository.findUserIdsByTicketId(88L)).thenReturn(List.of(10L, 50L));

        listener.onTicketExpired(event);

        // Seller nhận TICKET_EXPIRED
        verify(notificationService).sendNotification(
                eq(10L),
                eq("Vé đăng bán đã hết hạn!"),
                contains("tự động gỡ khỏi sàn"),
                eq(NotificationType.TICKET_EXPIRED),
                eq("/tickets/88")
        );

        // Watcher 50L nhận STARRED_TICKET_EXPIRED (loại trừ 10L là seller)
        verify(notificationService).sendNotification(
                eq(50L),
                eq("Vé theo dõi đã hết hạn!"),
                contains("không còn khả dụng trên sàn"),
                eq(NotificationType.STARRED_TICKET_EXPIRED),
                eq("/tickets/88")
        );
    }

    @Test
    @DisplayName("onTicketUpcomingReminder: Gửi UPCOMING_EVENT_REMINDER trước 24h cho Buyer")
    void testOnTicketUpcomingReminder_Normal24h() {
        TicketUpcomingReminderEvent event = new TicketUpcomingReminderEvent(
                90L, 10L, 20L, "Concert Anh Trai", "Mỹ Đình",
                Instant.parse("2026-11-16T00:14:00+07:00"),
                Instant.parse("2026-11-16T03:14:00+07:00"),
                false,
                Instant.parse("2026-11-15T00:14:00+07:00")
        );

        listener.onTicketUpcomingReminder(event);

        verify(notificationService).sendNotification(
                eq(20L),
                eq("Nhắc lịch sử dụng vé trong 24 giờ tới!"),
                contains("00:14 ngày 16/11/2026"),
                eq(NotificationType.UPCOMING_EVENT_REMINDER),
                eq("/tickets/90")
        );
    }

    @Test
    @DisplayName("onTicketUpcomingReminder: Gửi thông điệp nhắc sớm khi mua sát giờ <= 24h")
    void testOnTicketUpcomingReminder_LastMinute() {
        TicketUpcomingReminderEvent event = new TicketUpcomingReminderEvent(
                91L, 10L, 20L, "Vé Phim Đêm", "CGV",
                Instant.parse("2026-11-15T22:00:00+07:00"),
                Instant.parse("2026-11-16T00:00:00+07:00"),
                true,
                Instant.parse("2026-11-15T18:00:00+07:00")
        );

        listener.onTicketUpcomingReminder(event);

        verify(notificationService).sendNotification(
                eq(20L),
                eq("Sự kiện sắp diễn ra — Chuẩn bị sử dụng vé!"),
                contains("trong vòng 24 giờ tới"),
                eq(NotificationType.UPCOMING_EVENT_REMINDER),
                eq("/tickets/91")
        );
    }
}
