package com.yourapp.ticketpass.ticket.application;

import com.yourapp.ticketpass.ticket.domain.Ticket;
import com.yourapp.ticketpass.ticket.domain.TicketRepository;
import com.yourapp.ticketpass.ticket.domain.TicketStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TicketReminderScheduler {

    public static final int REMINDER_BATCH_SIZE = 100;

    public static final Set<TicketStatus> REMINDABLE_STATUSES = Collections.unmodifiableSet(EnumSet.of(
            TicketStatus.DEPOSIT_LOCKED,
            TicketStatus.AWAITING_USE,
            TicketStatus.AUTO_COMPLETE,
            TicketStatus.RESOLVED_SELLER_WINS
    ));

    private final TicketRepository ticketRepository;
    private final TicketReminderService ticketReminderService;
    private final Clock clock;

    @Value("${app.reminder.lead-hours:24}")
    private long leadHours = 24L;

    @Scheduled(cron = "${app.scheduler.ticket-reminder-cron:0 */1 * * * *}")
    public void scanAndSendUpcomingReminders() {
        Instant now = resolveNow();
        long effectiveLeadHours = leadHours > 0 ? leadHours : 24L;
        Instant windowEnd = now.plus(Duration.ofHours(effectiveLeadHours));

        List<Ticket> dueTickets = ticketRepository.findTicketsDueFor24hReminder(
                REMINDABLE_STATUSES,
                now,
                windowEnd,
                PageRequest.of(0, REMINDER_BATCH_SIZE)
        );
        if (dueTickets == null || dueTickets.isEmpty()) {
            return;
        }

        log.info("[TicketReminderScheduler] Tìm thấy {} vé cần nhắc lịch trong {} giờ tới.",
                dueTickets.size(), effectiveLeadHours);

        for (Ticket ticket : dueTickets) {
            if (ticket == null) {
                continue;
            }
            try {
                ticketReminderService.triggerReminderIfDue(ticket, false);
            } catch (Exception ex) {
                log.error("[TicketReminderScheduler] Lỗi khi nhắc lịch vé #{}: {}",
                        ticket.getId(), ex.getMessage(), ex);
            }
        }
    }

    void setLeadHours(long leadHours) {
        this.leadHours = leadHours;
    }

    private Instant resolveNow() {
        Instant instant = clock != null ? clock.instant() : null;
        return instant != null ? instant : Instant.now();
    }
}
