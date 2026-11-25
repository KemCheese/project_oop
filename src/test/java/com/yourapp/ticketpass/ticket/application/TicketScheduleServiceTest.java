package com.yourapp.ticketpass.ticket.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.yourapp.ticketpass.shared.dto.ConflictingTicketInfo;
import com.yourapp.ticketpass.ticket.adapter.BusTicketAdapter;
import com.yourapp.ticketpass.ticket.adapter.ConcertTicketAdapter;
import com.yourapp.ticketpass.ticket.adapter.MovieTicketAdapter;
import com.yourapp.ticketpass.ticket.domain.Ticket;
import com.yourapp.ticketpass.ticket.domain.TicketRepository;
import com.yourapp.ticketpass.ticket.domain.TicketStatus;
import com.yourapp.ticketpass.ticket.domain.TicketType;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@DisplayName("TicketScheduleService Test - Trùng giờ, fallback Strategy, 6 trạng thái & excludeSelf")
@ExtendWith(MockitoExtension.class)
class TicketScheduleServiceTest {

        @Mock
        private TicketRepository ticketRepository;

        private TicketScheduleService scheduleService;

        @BeforeEach
        void setUp() {
                scheduleService = new TicketScheduleService(
                                ticketRepository,
                                List.of(new MovieTicketAdapter(), new ConcertTicketAdapter(), new BusTicketAdapter()));
        }

        private Ticket ticket(Long id, TicketType type, TicketStatus status, Instant start, Instant end) {
                Ticket t = new Ticket();
                t.setId(id);
                t.setTicketType(type);
                t.setStatus(status);
                t.setTitle("Vé #" + id);
                t.setEventName("Sự kiện #" + id);
                t.setVenue("Địa điểm #" + id);
                t.setEventStartAt(start);
                t.setEventEndAt(end);
                t.setBuyerId(200L);
                return t;
        }

        @Test
        @DisplayName("Biên tiếp xúc 13:00-15:00 và 15:00-17:00 -> KHÔNG cảnh báo trùng")
        void testBoundaryContactReturnsEmptyConflicts() {
                Instant t13 = Instant.parse("2026-11-18T06:00:00Z");
                Instant t15 = Instant.parse("2026-11-18T08:00:00Z");
                Instant t17 = Instant.parse("2026-11-18T10:00:00Z");

                Ticket existing = ticket(1L, TicketType.MOVIE, TicketStatus.AWAITING_USE, t13, t15);
                Ticket target = ticket(2L, TicketType.MOVIE, TicketStatus.LISTED, t15, t17);

                when(ticketRepository.findPotentialScheduleConflicts(eq(200L), any(), eq(t17), eq(t15), eq(2L)))
                                .thenReturn(List.of(existing));

                assertThat(scheduleService.findConflictingTickets(200L, target)).isEmpty();
        }

        @Test
        @DisplayName("Fallback đa hình khi vé cũ thiếu eventEndAt: Movie 120p, Concert 180p, Bus 240p")
        void testFallbackDefaultDurationByTicketType() {
                Instant t1300 = Instant.parse("2026-11-18T06:00:00Z");
                Instant t1430 = Instant.parse("2026-11-18T07:30:00Z");
                Instant t1530 = Instant.parse("2026-11-18T08:30:00Z");
                Instant t1630 = Instant.parse("2026-11-18T09:30:00Z");
                Instant t1800 = Instant.parse("2026-11-18T11:00:00Z");

                Ticket oldMovie = ticket(10L, TicketType.MOVIE, TicketStatus.DEPOSIT_LOCKED, t1300, null);
                Ticket oldConcert = ticket(11L, TicketType.CONCERT, TicketStatus.AWAITING_USE, t1300, null);
                Ticket oldBus = ticket(12L, TicketType.BUS, TicketStatus.AUTO_COMPLETE, t1300, null);

                Ticket targetAt1430 = ticket(20L, TicketType.MOVIE, TicketStatus.LISTED, t1430, t1530);
                when(ticketRepository.findPotentialScheduleConflicts(eq(200L), any(), eq(t1530), eq(t1430), eq(20L)))
                                .thenReturn(List.of(oldMovie));
                List<ConflictingTicketInfo> movieConflicts = scheduleService.findConflictingTickets(200L, targetAt1430);
                assertThat(movieConflicts).hasSize(1);
                assertThat(movieConflicts.get(0).isEstimatedEndTime()).isTrue();

                Ticket targetAt1530 = ticket(21L, TicketType.MOVIE, TicketStatus.LISTED, t1530, t1630);
                when(ticketRepository.findPotentialScheduleConflicts(eq(200L), any(), eq(t1630), eq(t1530), eq(21L)))
                                .thenReturn(List.of(oldMovie, oldConcert));
                List<ConflictingTicketInfo> concertConflicts = scheduleService.findConflictingTickets(200L,
                                targetAt1530);
                assertThat(concertConflicts).hasSize(1);
                assertThat(concertConflicts.get(0).getTicketId()).isEqualTo(11L);

                Ticket targetAt1630 = ticket(22L, TicketType.MOVIE, TicketStatus.LISTED, t1630, t1800);
                when(ticketRepository.findPotentialScheduleConflicts(eq(200L), any(), eq(t1800), eq(t1630), eq(22L)))
                                .thenReturn(List.of(oldConcert, oldBus));
                List<ConflictingTicketInfo> busConflicts = scheduleService.findConflictingTickets(200L, targetAt1630);
                assertThat(busConflicts).hasSize(1);
                assertThat(busConflicts.get(0).getTicketId()).isEqualTo(12L);
        }

        @Test
        @DisplayName("Loại trừ LISTED, CANCELLED, RESOLVED_BUYER_WINS và chính vé đang xem")
        void testExcludesNonEffectiveStatusesAndSelfTicket() {
                Instant t13 = Instant.parse("2026-11-18T06:00:00Z");
                Instant t15 = Instant.parse("2026-11-18T08:00:00Z");

                Ticket target = ticket(100L, TicketType.MOVIE, TicketStatus.LISTED, t13, t15);

                Ticket self = ticket(100L, TicketType.MOVIE, TicketStatus.PAYMENT_ESCROWED, t13, t15);
                Ticket listed = ticket(104L, TicketType.MOVIE, TicketStatus.LISTED, t13, t15);
                Ticket cancelled = ticket(101L, TicketType.MOVIE, TicketStatus.CANCELLED, t13, t15);
                Ticket buyerWon = ticket(102L, TicketType.MOVIE, TicketStatus.RESOLVED_BUYER_WINS, t13, t15);

                when(ticketRepository.findPotentialScheduleConflicts(eq(200L), any(), eq(t15), eq(t13), eq(100L)))
                                .thenReturn(List.of(self, listed, cancelled, buyerWon));

                assertThat(scheduleService.findConflictingTickets(200L, target)).isEmpty();
        }

        @Test
        @DisplayName("Chạm biên 6 trạng thái buyer hiệu lực đều phát hiện trùng và format khung giờ VN")
        void testAllActiveBuyerStatusesAreDetected() {
                Instant t13 = Instant.parse("2026-11-18T06:00:00Z");
                Instant t15 = Instant.parse("2026-11-18T08:00:00Z");
                Ticket target = ticket(999L, TicketType.CONCERT, TicketStatus.LISTED, t13, t15);

                List<Ticket> activeSix = List.of(
                                ticket(1L, TicketType.MOVIE, TicketStatus.PAYMENT_ESCROWED, t13, t15),
                                ticket(2L, TicketType.MOVIE, TicketStatus.DEPOSIT_LOCKED, t13, t15),
                                ticket(3L, TicketType.MOVIE, TicketStatus.DISPUTE_UNDER_REVIEW, t13, t15),
                                ticket(4L, TicketType.MOVIE, TicketStatus.AWAITING_USE, t13, t15),
                                ticket(5L, TicketType.MOVIE, TicketStatus.AUTO_COMPLETE, t13, t15),
                                ticket(6L, TicketType.MOVIE, TicketStatus.RESOLVED_SELLER_WINS, t13, t15));

                when(ticketRepository.findPotentialScheduleConflicts(
                                eq(200L), eq(TicketScheduleService.ACTIVE_BUYER_SCHEDULE_STATUSES), eq(t15), eq(t13),
                                eq(999L)))
                                .thenReturn(activeSix);

                List<ConflictingTicketInfo> conflicts = scheduleService.findConflictingTickets(200L, target);
                assertThat(conflicts).hasSize(6);
                assertThat(conflicts.get(0).getFormattedTimeRange()).isEqualTo("13:00 - 15:00 18/11/2026");
        }
}
