package com.yourapp.ticketpass.ticket.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.yourapp.ticketpass.ticket.adapter.MovieTicketAdapter;
import com.yourapp.ticketpass.ticket.domain.Ticket;
import com.yourapp.ticketpass.ticket.domain.TicketRepository;
import com.yourapp.ticketpass.ticket.domain.TicketStatus;
import com.yourapp.ticketpass.ticket.domain.TicketType;
import com.yourapp.ticketpass.ticket.domain.ValidationResult;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@DisplayName("ValidateTicketService Test - Kiểm tra bắt buộc eventEndAt > eventStartAt (1.PA B)")
@ExtendWith(MockitoExtension.class)
class ValidateTicketServiceTest {

    @Mock
    private TicketRepository ticketRepository;

    private Clock fixedClock;
    private ValidateTicketService validateTicketService;

    @BeforeEach
    void setUp() {
        fixedClock = Clock.fixed(Instant.parse("2026-11-15T10:00:00Z"), ZoneOffset.UTC);
        validateTicketService = new ValidateTicketService(
                ticketRepository,
                List.of(new MovieTicketAdapter()),
                fixedClock
        );
    }

    private Ticket buildValidMovieTicket() {
        Instant now = fixedClock.instant();
        Ticket ticket = new Ticket();
        ticket.setId(1L);
        ticket.setSellerId(10L);
        ticket.setTicketType(TicketType.MOVIE);
        ticket.setStatus(TicketStatus.LISTED);
        ticket.setTitle("Vé Dune IMAX");
        ticket.setEventName("Dune Part Three");
        ticket.setSeatInfo("Phòng 1 - H10");
        ticket.setOriginalPrice(new BigDecimal("200000"));
        ticket.setSalePrice(new BigDecimal("180000"));
        ticket.setTicketImageStorageKey("tickets/pub.png");
        ticket.setQrImageStorageKey("tickets/qr.png");
        ticket.setEventStartAt(now.plus(Duration.ofHours(5)));
        ticket.setEventEndAt(now.plus(Duration.ofHours(7)));
        return ticket;
    }

    @Test
    @DisplayName("Đăng vé thiếu eventEndAt -> FAIL validation")
    void testValidateForListingRejectsNullEventEndAt() {
        Ticket ticket = buildValidMovieTicket();
        ticket.setEventEndAt(null);

        ValidationResult result = validateTicketService.validateForListing(ticket);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(e -> e.contains("ket thuc"));
    }

    @Test
    @DisplayName("Đăng vé có eventEndAt <= eventStartAt -> FAIL validation")
    void testValidateForListingRejectsEndBeforeOrEqualStart() {
        Ticket equalTicket = buildValidMovieTicket();
        equalTicket.setEventEndAt(equalTicket.getEventStartAt());
        assertThat(validateTicketService.validateForListing(equalTicket).isValid()).isFalse();

        Ticket reversedTicket = buildValidMovieTicket();
        reversedTicket.setEventEndAt(reversedTicket.getEventStartAt().minus(Duration.ofMinutes(30)));
        assertThat(validateTicketService.validateForListing(reversedTicket).isValid()).isFalse();
    }

    @Test
    @DisplayName("Đăng vé hợp lệ có eventEndAt > eventStartAt -> PASS validation")
    void testValidateForListingAcceptsValidEndTime() {
        Ticket ticket = buildValidMovieTicket();

        ValidationResult result = validateTicketService.validateForListing(ticket);

        assertThat(result.isValid()).isTrue();
    }

    @Test
    @DisplayName("validateForPurchase dùng Clock tiêm vào để chặn vé đã quá giờ")
    void testValidateForPurchaseUsesInjectedClock() {
        Ticket expiredTicket = buildValidMovieTicket();
        expiredTicket.setEventStartAt(fixedClock.instant()); // không sau now

        when(ticketRepository.findById(1L)).thenReturn(Optional.of(expiredTicket));

        ValidationResult result = validateTicketService.validateForPurchase(1L, 99L);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(e -> e.contains("qua thoi gian"));
    }
}
