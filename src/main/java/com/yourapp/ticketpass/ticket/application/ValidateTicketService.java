package com.yourapp.ticketpass.ticket.application;

import com.yourapp.ticketpass.shared.exception.ResourceNotFoundException;
import com.yourapp.ticketpass.ticket.domain.Ticket;
import com.yourapp.ticketpass.ticket.domain.TicketAdapter;
import com.yourapp.ticketpass.ticket.domain.TicketRepository;
import com.yourapp.ticketpass.ticket.domain.TicketStatus;
import com.yourapp.ticketpass.ticket.domain.ValidationResult;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ValidateTicketService {

    public static final BigDecimal MIN_SALE_PRICE = new BigDecimal("10000");

    private static final Set<TicketStatus> ACTIVE_DUPLICATE_STATUSES = EnumSet.of(
            TicketStatus.LISTED,
            TicketStatus.PAYMENT_ESCROWED,
            TicketStatus.DEPOSIT_LOCKED,
            TicketStatus.AWAITING_USE,
            TicketStatus.DISPUTE_UNDER_REVIEW
    );

    private final TicketRepository ticketRepository;

    private final List<TicketAdapter> ticketAdapters;

    private final Clock clock;

    @Value("${app.ticket.allow-markup:false}")
    private boolean allowMarkup;

    @Value("${app.ticket.min-hours-before-event:1}")
    private long minHoursBeforeEvent = 1L;

    public ValidationResult validateForListing(Ticket ticket) {
        List<String> errors = new ArrayList<>();

        if (ticket.getSalePrice() == null || ticket.getSalePrice().compareTo(MIN_SALE_PRICE) < 0) {
            errors.add("Gia ban phai tu 10.000 VND tro len.");
        }

        if (!allowMarkup
                && ticket.getSalePrice() != null
                && ticket.getOriginalPrice() != null
                && ticket.getSalePrice().compareTo(ticket.getOriginalPrice()) > 0) {
            errors.add("Gia ban khong duoc vuot qua gia goc (quy tac chong dau co).");
        }

        Instant now = resolveNow();
        long effectiveMinHours = Math.max(0L, minHoursBeforeEvent);
        Instant minAllowedEventStart = now.plus(Duration.ofHours(effectiveMinHours));
        if (ticket.getEventStartAt() == null) {
            errors.add("Thoi gian su kien khong duoc de trong.");
        } else if (!ticket.getEventStartAt().isAfter(now)) {
            errors.add("Thoi gian su kien phai nam o tuong lai.");
        } else if (effectiveMinHours > 0 && ticket.getEventStartAt().isBefore(minAllowedEventStart)) {
            errors.add("Thoi gian bat dau su kien phai cach hien tai it nhat " + effectiveMinHours + " gio.");
        }

        if (ticket.getEventEndAt() == null) {
            errors.add("Thoi gian ket thuc su kien khong duoc de trong.");
        } else if (ticket.getEventStartAt() != null && !ticket.getEventEndAt().isAfter(ticket.getEventStartAt())) {
            errors.add("Thoi gian ket thuc su kien phai sau thoi gian bat dau.");
        }

        if (ticket.getProviderOrderCode() != null && !ticket.getProviderOrderCode().isBlank()
                && ticketRepository.existsActiveByProviderOrderCode(
                        ticket.getProviderOrderCode(), ACTIVE_DUPLICATE_STATUSES)) {
            errors.add("Ma don hang ve nay dang ton tai trong mot tin dang hoac giao dich khac tren san.");
        }

        if (ticket.getEventStartAt() != null
                && ticket.getVenue() != null && !ticket.getVenue().isBlank()
                && ticket.getSeatInfo() != null && !ticket.getSeatInfo().isBlank()
                && ticketRepository.existsActiveByEventStartVenueAndSeat(
                        ticket.getEventStartAt(), ticket.getVenue(), ticket.getSeatInfo(), ACTIVE_DUPLICATE_STATUSES)) {
            errors.add("Ve trung suat dien, dia diem va so ghe nay dang ton tai tren san.");
        }

        if (ticket.getTicketImageStorageKey() == null || ticket.getTicketImageStorageKey().isBlank()) {
            errors.add("Phai upload anh ve cong khai.");
        }

        if (ticket.getQrImageStorageKey() == null || ticket.getQrImageStorageKey().isBlank()) {
            errors.add("Phai upload anh QR an (anh check-in bi mat).");
        }

        TicketAdapter adapter = findAdapter(ticket);
        if (adapter != null) {
            ValidationResult adapterResult = adapter.validateListing(ticket);
            if (!adapterResult.isValid()) {
                errors.addAll(adapterResult.getErrors());
            }
        } else {
            errors.add("Loai ve khong duoc ho tro: " + ticket.getTicketType());
        }

        if (errors.isEmpty()) {
            return ValidationResult.ok();
        }
        return ValidationResult.fail(errors);
    }

    public ValidationResult validateForPurchase(Long ticketId, Long buyerId) {
        List<String> errors = new ArrayList<>();

        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket", ticketId));

        if (ticket.getStatus() != TicketStatus.LISTED) {
            errors.add("Ve khong o trang thai co the mua (trang thai hien tai: " + ticket.getStatus() + ").");
        }

        if (ticket.getSellerId() != null && ticket.getSellerId().equals(buyerId)) {
            errors.add("Ban khong the mua ve cua chinh minh.");
        }

        if (ticket.getEventStartAt() != null && !ticket.getEventStartAt().isAfter(resolveNow())) {
            errors.add("Ve da qua thoi gian su kien, khong the mua.");
        }

        if (errors.isEmpty()) {
            return ValidationResult.ok();
        }
        return ValidationResult.fail(errors);
    }

    private TicketAdapter findAdapter(Ticket ticket) {
        if (ticket.getTicketType() == null) {
            return null;
        }
        return ticketAdapters.stream()
                .filter(adapter -> adapter.supportedType() == ticket.getTicketType())
                .findFirst()
                .orElse(null);
    }

    private Instant resolveNow() {
        Instant instant = clock != null ? clock.instant() : null;
        return instant != null ? instant : Instant.now();
    }
}
