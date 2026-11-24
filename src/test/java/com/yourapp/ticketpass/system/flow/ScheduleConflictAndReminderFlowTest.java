package com.yourapp.ticketpass.system.flow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.yourapp.ticketpass.notification.domain.Notification;
import com.yourapp.ticketpass.notification.domain.NotificationRepository;
import com.yourapp.ticketpass.notification.domain.NotificationType;
import com.yourapp.ticketpass.notification.infrastructure.NotificationJpaRepository;
import com.yourapp.ticketpass.shared.dto.ConflictingTicketInfo;
import com.yourapp.ticketpass.system.base.AbstractSystemIntegrationTest;
import com.yourapp.ticketpass.ticket.application.TicketReminderScheduler;
import com.yourapp.ticketpass.ticket.application.TicketScheduleService;
import com.yourapp.ticketpass.ticket.application.TransferTicketService;
import com.yourapp.ticketpass.ticket.domain.Ticket;
import com.yourapp.ticketpass.ticket.domain.TicketStatus;
import com.yourapp.ticketpass.ticket.domain.TicketType;
import com.yourapp.ticketpass.user.domain.User;
import com.yourapp.ticketpass.user.domain.UserRole;
import com.yourapp.ticketpass.wallet.application.WalletService;
import com.yourapp.ticketpass.wallet.infrastructure.WalletJpaRepository;
import com.yourapp.ticketpass.wallet.infrastructure.WalletTransactionJpaRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

@DisplayName("System Integration Test - Check trùng giờ mua vé & Nhắc lịch trước 24h")
class ScheduleConflictAndReminderFlowTest extends AbstractSystemIntegrationTest {

    @Autowired
    private WalletService walletService;

    @Autowired
    private TicketScheduleService ticketScheduleService;

    @Autowired
    private TicketReminderScheduler ticketReminderScheduler;

    @Autowired
    private TransferTicketService transferTicketService;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private NotificationJpaRepository notificationJpaRepository;

    @Autowired
    private WalletJpaRepository walletJpaRepository;

    @Autowired
    private WalletTransactionJpaRepository walletTransactionJpaRepository;

    private final List<Long> testUserIdsForExtraCleanup = new ArrayList<>();

    @AfterEach
    void cleanupNotificationsAndWallets() {
        for (Long ticketId : createdTicketIds) {
            escrowTransactionRepository.findByTicketId(ticketId)
                    .ifPresent(tx -> createdEscrowIds.add(tx.getId()));
        }
        for (Long uid : testUserIdsForExtraCleanup) {
            List<Notification> notifs = notificationRepository.findRecentByUserId(uid, 100);
            notifs.forEach(n -> notificationJpaRepository.deleteById(n.getId()));
            walletTransactionJpaRepository.findAll().stream()
                    .filter(wt -> wt.getWalletId() != null && walletJpaRepository.findByUserId(uid)
                            .map(w -> w.getId().equals(wt.getWalletId())).orElse(false))
                    .forEach(wt -> walletTransactionJpaRepository.deleteById(wt.getId()));
            walletJpaRepository.findByUserId(uid).ifPresent(w -> walletJpaRepository.deleteById(w.getId()));
        }
        testUserIdsForExtraCleanup.clear();
    }

    private Ticket createTimedTicket(Long sellerId, Long buyerId, String title, TicketType type,
                                   TicketStatus status, Instant startAt, Instant endAt) {
        Ticket ticket = createAndSaveTicket(sellerId, title, type, new BigDecimal("200000.00"), status);
        ticket.setBuyerId(buyerId);
        ticket.setEventStartAt(startAt);
        ticket.setEventEndAt(endAt);
        return ticketRepository.save(ticket);
    }

    private User newBuyer(String prefix) {
        User u = createAndSaveUser(prefix + "-" + System.nanoTime() + "@test.com", "Pass123@", UserRole.USER);
        testUserIdsForExtraCleanup.add(u.getId());
        return u;
    }

    @Test
    @DisplayName("E2E Trùng giờ: Cảnh báo 2 lớp UI, Guard Ví/VietQR/API, tiếp xúc biên & trạng thái loại trừ")
    void testEndToEndScheduleConflictDetectionAndGuards() throws Exception {
        User seller = createAndSaveUser("sched-seller-" + System.nanoTime() + "@test.com", "Pass123@", UserRole.USER);
        testUserIdsForExtraCleanup.add(seller.getId());
        User buyer = newBuyer("sched-buyer");
        walletService.topUp(buyer.getId(), new BigDecimal("2000000.00"), "Nạp test trùng giờ");

        Instant baseStart = Instant.now().plus(Duration.ofDays(3));
        Instant baseEnd = baseStart.plus(Duration.ofHours(2));

        createTimedTicket(seller.getId(), buyer.getId(), "Vé Phim Đã Mua 13h-15h",
                TicketType.MOVIE, TicketStatus.AWAITING_USE, baseStart, baseEnd);

        createTimedTicket(seller.getId(), buyer.getId(), "Vé Đã Hủy 13h-15h",
                TicketType.MOVIE, TicketStatus.CANCELLED, baseStart, baseEnd);

        Ticket overlapping = createTimedTicket(seller.getId(), null, "Concert Trùng Giờ 14h-16h",
                TicketType.CONCERT, TicketStatus.LISTED,
                baseStart.plus(Duration.ofHours(1)), baseStart.plus(Duration.ofHours(3)));

        Ticket adjacent = createTimedTicket(seller.getId(), null, "Xe Khách Kế Tiếp 15h-19h",
                TicketType.BUS, TicketStatus.LISTED, baseEnd, baseEnd.plus(Duration.ofHours(4)));

        mockMvc.perform(get("/tickets/" + overlapping.getId()).with(user(buyer.getEmail()).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("hasScheduleConflict", true))
                .andExpect(content().string(not(containsString("scheduleConflictBanner"))))
                .andExpect(content().string(containsString("overlapConfirmModal")))
                .andExpect(content().string(containsString("data-has-conflict=\"true\"")))
                .andExpect(content().string(containsString("Vé Phim Đã Mua 13h-15h")));

        mockMvc.perform(get("/tickets/" + overlapping.getId()).with(user(seller.getEmail()).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("hasScheduleConflict", false))
                .andExpect(content().string(not(containsString("data-has-conflict=\"true\""))));

        mockMvc.perform(get("/tickets/" + adjacent.getId()).with(user(buyer.getEmail()).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("hasScheduleConflict", false))
                .andExpect(content().string(not(containsString("data-has-conflict=\"true\""))));

        mockMvc.perform(post("/payment/wallet/pay-escrow")
                        .with(user(buyer.getEmail()).roles("USER"))
                        .with(csrf())
                        .param("ticketId", String.valueOf(overlapping.getId())))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/tickets/" + overlapping.getId() + "?overlapWarning=true&method=wallet"))
                .andExpect(flash().attribute("overlapWarning", true));

        assertThat(walletService.getOrCreateWallet(buyer.getId()).getBalance())
                .isEqualByComparingTo("2000000.00");

        mockMvc.perform(post("/payment/escrow/buy")
                        .with(user(buyer.getEmail()).roles("USER"))
                        .with(csrf())
                        .param("ticketId", String.valueOf(overlapping.getId())))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/tickets/" + overlapping.getId() + "?overlapWarning=true&method=vietqr"));

        String jsonBody = String.format("{\"ticketId\":%d,\"paymentMethod\":\"MANUAL_TRANSFER\",\"confirmOverlap\":false}",
                overlapping.getId());
        mockMvc.perform(post("/payment/escrow/init")
                        .with(user(buyer.getEmail()).roles("USER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Accept", MediaType.APPLICATION_JSON_VALUE)
                        .content(jsonBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("trùng khung giờ")));

        mockMvc.perform(post("/payment/wallet/pay-escrow")
                        .with(user(buyer.getEmail()).roles("USER"))
                        .with(csrf())
                        .param("ticketId", String.valueOf(overlapping.getId()))
                        .param("confirmOverlap", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/tickets/" + overlapping.getId()));

        Ticket purchased = ticketRepository.findById(overlapping.getId()).orElseThrow();
        assertThat(purchased.getStatus()).isEqualTo(TicketStatus.PAYMENT_ESCROWED);
        assertThat(walletService.getOrCreateWallet(buyer.getId()).getBalance())
                .isEqualByComparingTo("1800000.00");
    }

    @Test
    @DisplayName("Fallback vé cũ thiếu eventEndAt + Validate form đăng vé eventEndAt <= eventStartAt")
    void testLegacyNullEndAtAndListingFormValidation() throws Exception {
        User seller = newBuyer("legacy-seller");
        User buyer = newBuyer("legacy-buyer");

        Instant start = Instant.now().plus(Duration.ofDays(5));
        createTimedTicket(seller.getId(), buyer.getId(), "Vé Phim Cũ (No EndAt)",
                TicketType.MOVIE, TicketStatus.DEPOSIT_LOCKED, start, null);

        Ticket newTicketOverlap = createTimedTicket(seller.getId(), null, "Vé Mới Chồng Lấn Phút 60",
                TicketType.MOVIE, TicketStatus.LISTED,
                start.plus(Duration.ofMinutes(60)), start.plus(Duration.ofMinutes(180)));

        List<ConflictingTicketInfo> conflicts = ticketScheduleService.findConflictingTickets(buyer.getId(), newTicketOverlap);
        assertThat(conflicts).hasSize(1);
        assertThat(conflicts.get(0).isEstimatedEndTime()).isTrue();

        long ticketCountBefore = ticketRepository.findBySellerId(seller.getId()).size();
        LocalDateTime startLdt = LocalDateTime.now().plusDays(6).withSecond(0).withNano(0);
        LocalDateTime endLdt = startLdt.minusHours(1);
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

        MockMultipartFile pubImg = new MockMultipartFile("ticketImage", "ticket.png", "image/png", new byte[] {1, 2});
        MockMultipartFile qrImg = new MockMultipartFile("qrImage", "qr.png", "image/png", new byte[] {3, 4});

        mockMvc.perform(multipart("/tickets")
                        .file(pubImg)
                        .file(qrImg)
                        .with(user(seller.getEmail()).roles("USER"))
                        .with(csrf())
                        .param("ticketType", "MOVIE")
                        .param("title", "Vé Sai Giờ Kết Thúc")
                        .param("eventName", "Dune")
                        .param("seatInfo", "A1")
                        .param("eventStartAt", fmt.format(startLdt))
                        .param("eventEndAt", fmt.format(endLdt))
                        .param("originalPrice", "200000")
                        .param("salePrice", "150000"))
                .andExpect(status().isOk())
                .andExpect(view().name("ticket/create"))
                .andExpect(model().attributeHasFieldErrors("createTicketRequest", "eventEndAt"));

        assertThat(ticketRepository.findBySellerId(seller.getId())).hasSize((int) ticketCountBefore);
    }

    @Test
    @DisplayName("Nhắc trước 24h Idempotent trên H2 DB + Nhắc sớm 1 lần khi mua sát giờ (< 24h)")
    void testReminder24hIdempotencyAndLastMinutePurchase() {
        User seller = newBuyer("rem-seller");
        User buyer = newBuyer("rem-buyer");
        Instant now = Instant.now();

        Ticket dueTicket = createTimedTicket(seller.getId(), buyer.getId(), "Vé Nhắc 24h",
                TicketType.CONCERT, TicketStatus.TRANSFERRED,
                now.plus(Duration.ofHours(23)), now.plus(Duration.ofHours(26)));

        Ticket farTicket = createTimedTicket(seller.getId(), buyer.getId(), "Vé Còn Xa",
                TicketType.CONCERT, TicketStatus.DEPOSIT_LOCKED,
                now.plus(Duration.ofHours(26)), now.plus(Duration.ofHours(29)));

        Ticket pastTicket = createTimedTicket(seller.getId(), buyer.getId(), "Vé Quá Khứ",
                TicketType.MOVIE, TicketStatus.AWAITING_USE,
                now.minus(Duration.ofHours(2)), now);

        ticketReminderScheduler.scanAndSendUpcomingReminders();
        ticketReminderScheduler.scanAndSendUpcomingReminders();

        assertThat(ticketRepository.findById(dueTicket.getId()).orElseThrow().getReminded24hAt()).isNotNull();
        assertThat(ticketRepository.findById(farTicket.getId()).orElseThrow().getReminded24hAt()).isNull();
        assertThat(ticketRepository.findById(pastTicket.getId()).orElseThrow().getReminded24hAt()).isNull();

        List<Notification> buyerNotifsAfterScheduler = notificationRepository.findRecentByUserId(buyer.getId(), 50);
        long reminderCount1 = buyerNotifsAfterScheduler.stream()
                .filter(n -> n.getType() == NotificationType.UPCOMING_EVENT_REMINDER)
                .count();
        assertThat(reminderCount1).isEqualTo(1);

        Ticket urgentTicket = createTimedTicket(seller.getId(), buyer.getId(), "Vé Mua Sát Giờ 10h",
                TicketType.MOVIE, TicketStatus.PAYMENT_ESCROWED,
                now.plus(Duration.ofHours(10)), now.plus(Duration.ofHours(12)));

        transferTicketService.markDepositLocked(urgentTicket.getId(), 888L);

        Ticket lockedUrgent = ticketRepository.findById(urgentTicket.getId()).orElseThrow();
        assertThat(lockedUrgent.getStatus()).isEqualTo(TicketStatus.TRANSFERRED);
        assertThat(lockedUrgent.getReminded24hAt()).isNotNull();

        ticketReminderScheduler.scanAndSendUpcomingReminders();

        List<Notification> finalNotifs = notificationRepository.findRecentByUserId(buyer.getId(), 50);
        long finalReminderCount = finalNotifs.stream()
                .filter(n -> n.getType() == NotificationType.UPCOMING_EVENT_REMINDER)
                .count();
        assertThat(finalReminderCount).isEqualTo(2);
        assertThat(finalNotifs)
                .anyMatch(n -> n.getType() == NotificationType.UPCOMING_EVENT_REMINDER
                        && n.getContent().contains("trong vòng 24 giờ tới"));
    }

    @Test
    @DisplayName("Vé đã hết hạn hoặc quá 30p mở QR -> ẩn hoàn toàn khung xanh và khung khóa QR trên trang chi tiết")
    void testExpiredTicketHidesGreenQrBox() throws Exception {
        User seller = newBuyer("qr-seller");
        User buyer = newBuyer("qr-buyer");
        Instant now = Instant.now();

        Ticket expiredAutoComplete = createTimedTicket(seller.getId(), buyer.getId(), "Vé Xe Hết Hạn",
                TicketType.BUS, TicketStatus.AUTO_COMPLETE,
                now.minus(Duration.ofDays(10)), now.minus(Duration.ofDays(10)).plus(Duration.ofHours(4)));
        expiredAutoComplete.setReceivedAt(now.minus(Duration.ofDays(11)));
        expiredAutoComplete = ticketRepository.save(expiredAutoComplete);

        mockMvc.perform(get("/tickets/" + expiredAutoComplete.getId()).with(user(buyer.getEmail()).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("class=\"qr-unlocked-box\""))))
                .andExpect(content().string(not(containsString("class=\"qr-locked-box\""))))
                .andExpect(content().string(not(containsString("Mã QR Đã Bị Ẩn Bảo Mật"))))
                .andExpect(content().string(not(containsString("Mã QR Check-in Đang Được Khóa Bảo Mật"))));

        Ticket activeTicket = createTimedTicket(seller.getId(), buyer.getId(), "Vé Đang Mở QR",
                TicketType.BUS, TicketStatus.AWAITING_USE,
                now.plus(Duration.ofHours(4)), now.plus(Duration.ofHours(8)));
        activeTicket.setReceivedAt(now.minus(Duration.ofMinutes(5)));
        activeTicket.setDisputeWindowClosesAt(now.plus(Duration.ofHours(10)));
        activeTicket = ticketRepository.save(activeTicket);

        mockMvc.perform(get("/tickets/" + activeTicket.getId()).with(user(buyer.getEmail()).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("class=\"qr-unlocked-box\"")))
                .andExpect(content().string(containsString("Mã Check-in QR Đã Mở Khóa")))
                .andExpect(content().string(containsString("Vé có vấn đề? Mở khiếu nại ngay")));

        Ticket qrExpiredTicket = createTimedTicket(seller.getId(), buyer.getId(), "Vé Quá 30p Mở QR",
                TicketType.BUS, TicketStatus.AWAITING_USE,
                now.plus(Duration.ofHours(4)), now.plus(Duration.ofHours(8)));
        qrExpiredTicket.setReceivedAt(now.minus(Duration.ofMinutes(40)));
        qrExpiredTicket.setDisputeWindowClosesAt(now.plus(Duration.ofHours(10)));
        qrExpiredTicket = ticketRepository.save(qrExpiredTicket);

        mockMvc.perform(get("/tickets/" + qrExpiredTicket.getId()).with(user(buyer.getEmail()).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("class=\"qr-unlocked-box\""))))
                .andExpect(content().string(not(containsString("class=\"qr-locked-box\""))))
                .andExpect(content().string(containsString("Vé có vấn đề? Mở khiếu nại ngay")));
    }
}
