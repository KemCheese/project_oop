package com.yourapp.ticketpass.ticket.application;

import com.yourapp.ticketpass.shared.event.TicketUpcomingReminderEvent;
import com.yourapp.ticketpass.ticket.domain.Ticket;
import com.yourapp.ticketpass.ticket.domain.TicketRepository;
import com.yourapp.ticketpass.ticket.domain.TimeSlot;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TicketReminderService {

    private final TicketRepository ticketRepository;
    private final TicketScheduleService ticketScheduleService;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    @Value("${app.reminder.lead-hours:24}")
    private long leadHours = 24L;

    @Transactional
    public boolean triggerReminderIfDue(Ticket ticket, boolean lastMinutePurchase) {
        if (ticket == null || ticket.getId() == null || ticket.getBuyerId() == null
                || ticket.getEventStartAt() == null || ticket.getReminded24hAt() != null) {
            return false;
        }

        Instant now = resolveNow();
        Instant windowEnd = now.plus(Duration.ofHours(resolveLeadHours()));

        if (!ticket.getEventStartAt().isAfter(now) || ticket.getEventStartAt().isAfter(windowEnd)) {
            return false;
        }

        Long ticketId = ticket.getId();
        Long sellerId = ticket.getSellerId();
        Long buyerId = ticket.getBuyerId();
        String title = ticket.getTitle();
        String venue = ticket.getVenue();
        Instant eventStartAt = ticket.getEventStartAt();
        Instant effectiveEndAt = ticketScheduleService != null
                ? ticketScheduleService.resolveTimeSlot(ticket).map(TimeSlot::end).orElse(ticket.getEventEndAt())
                : ticket.getEventEndAt();

        int updatedRows = ticketRepository.markReminded24hIfUnset(ticketId, now);
        if (updatedRows != 1) {
            return false;
        }
        ticket.setReminded24hAt(now);

        if (eventPublisher != null) {
            eventPublisher.publishEvent(new TicketUpcomingReminderEvent(
                    ticketId,
                    sellerId,
                    buyerId,
                    title,
                    venue,
                    eventStartAt,
                    effectiveEndAt,
                    lastMinutePurchase,
                    now
            ));
        }

        log.info("[TicketReminderService] Đã phát sự kiện nhắc lịch vé #{} cho buyerId={} (lastMinute={}).",
                ticketId, buyerId, lastMinutePurchase);
        return true;
    }

    @Transactional
    public boolean triggerEarlyReminderIfWithinLeadWindow(Ticket ticket) {
        return triggerReminderIfDue(ticket, true);
    }

    void setLeadHours(long leadHours) {
        this.leadHours = leadHours;
    }

    long resolveLeadHours() {
        return leadHours > 0 ? leadHours : 24L;
    }

    private Instant resolveNow() {
        Instant instant = clock != null ? clock.instant() : null;
        return instant != null ? instant : Instant.now();
    }
}
