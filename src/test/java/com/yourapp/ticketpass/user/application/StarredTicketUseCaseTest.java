package com.yourapp.ticketpass.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yourapp.ticketpass.shared.exception.ResourceNotFoundException;
import com.yourapp.ticketpass.shared.storage.FileStorageService;
import com.yourapp.ticketpass.ticket.domain.Ticket;
import com.yourapp.ticketpass.ticket.domain.TicketRepository;
import com.yourapp.ticketpass.ticket.domain.TicketStatus;
import com.yourapp.ticketpass.ticket.domain.TicketType;
import com.yourapp.ticketpass.user.domain.StarredTicket;
import com.yourapp.ticketpass.user.domain.StarredTicketRepository;
import com.yourapp.ticketpass.user.web.dto.StarredTicketItemResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("StarredTicketUseCase Unit Tests")
class StarredTicketUseCaseTest {

    @Mock
    private StarredTicketRepository starredTicketRepository;

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private FileStorageService fileStorageService;

    @InjectMocks
    private StarredTicketUseCase starredTicketUseCase;

    private Ticket buildSampleTicket(Long id, String title, TicketType type, TicketStatus status) {
        Ticket t = new Ticket();
        t.setId(id);
        t.setTitle(title);
        t.setEventName("Sự kiện " + title);
        t.setVenue("Hà Nội");
        t.setSeatInfo("Ghế A1");
        t.setTicketType(type);
        t.setStatus(status);
        t.setSalePrice(new BigDecimal("150000"));
        t.setOriginalPrice(new BigDecimal("200000"));
        t.setEventStartAt(Instant.now().plusSeconds(86400));
        t.setTicketImageStorageKey("tickets/sample.jpg");
        return t;
    }

    @Nested
    @DisplayName("toggleStar - Bật/tắt đánh sao vé")
    class ToggleStarTests {

        @Test
        @DisplayName("Ném ResourceNotFoundException nếu vé không tồn tại")
        void toggleStar_TicketNotFound_ThrowsException() {
            when(ticketRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> starredTicketUseCase.toggleStar(1L, 999L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("999");

            verify(starredTicketRepository, never()).save(any());
            verify(starredTicketRepository, never()).deleteByUserIdAndTicketId(any(), any());
        }

        @Test
        @DisplayName("Lưu mới và trả về true nếu vé chưa được đánh sao")
        void toggleStar_NotStarredYet_SavesAndReturnsTrue() {
            Ticket ticket = buildSampleTicket(10L, "Concert Blackpink", TicketType.CONCERT, TicketStatus.LISTED);
            when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));
            when(starredTicketRepository.existsByUserIdAndTicketId(1L, 10L)).thenReturn(false);

            boolean result = starredTicketUseCase.toggleStar(1L, 10L);

            assertThat(result).isTrue();
            verify(starredTicketRepository).save(any(StarredTicket.class));
            verify(starredTicketRepository, never()).deleteByUserIdAndTicketId(any(), any());
        }

        @Test
        @DisplayName("Xóa và trả về false nếu vé đã được đánh sao trước đó")
        void toggleStar_AlreadyStarred_DeletesAndReturnsFalse() {
            Ticket ticket = buildSampleTicket(10L, "Concert Blackpink", TicketType.CONCERT, TicketStatus.LISTED);
            when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));
            when(starredTicketRepository.existsByUserIdAndTicketId(1L, 10L)).thenReturn(true);

            boolean result = starredTicketUseCase.toggleStar(1L, 10L);

            assertThat(result).isFalse();
            verify(starredTicketRepository).deleteByUserIdAndTicketId(1L, 10L);
            verify(starredTicketRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("isStarred & getStarredTicketIds - Tra cứu trạng thái")
    class QueryTests {

        @Test
        @DisplayName("isStarred trả về đúng trạng thái từ repository")
        void isStarred_ReturnsBoolean() {
            when(starredTicketRepository.existsByUserIdAndTicketId(1L, 10L)).thenReturn(true);
            when(starredTicketRepository.existsByUserIdAndTicketId(1L, 20L)).thenReturn(false);

            assertThat(starredTicketUseCase.isStarred(1L, 10L)).isTrue();
            assertThat(starredTicketUseCase.isStarred(1L, 20L)).isFalse();
            assertThat(starredTicketUseCase.isStarred(null, 10L)).isFalse();
        }

        @Test
        @DisplayName("getStarredTicketIds trả về Set ID các vé đã sao")
        void getStarredTicketIds_ReturnsSet() {
            when(starredTicketRepository.findStarredTicketIdsByUserId(1L)).thenReturn(Set.of(10L, 20L));

            Set<Long> ids = starredTicketUseCase.getStarredTicketIds(1L);

            assertThat(ids).containsExactlyInAnyOrder(10L, 20L);
        }

        @Test
        @DisplayName("getStarredTicketIds trả về Set rỗng nếu userId null")
        void getStarredTicketIds_NullUserId_ReturnsEmpty() {
            Set<Long> ids = starredTicketUseCase.getStarredTicketIds(null);
            assertThat(ids).isEmpty();
        }
    }

    @Nested
    @DisplayName("getStarredTicketsForUser - Lấy danh sách hiển thị hồ sơ cá nhân")
    class GetStarredTicketsTests {

        @Test
        @DisplayName("Trả về danh sách rỗng nếu user chưa sao vé nào")
        void getStarredTickets_EmptyList() {
            when(starredTicketRepository.findTicketIdsByUserId(1L)).thenReturn(Collections.emptyList());

            List<StarredTicketItemResponse> result = starredTicketUseCase.getStarredTicketsForUser(1L);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Ghép nối thông tin vé và dựng URL ảnh đại diện chính xác")
        void getStarredTickets_SuccessMapping() {
            when(starredTicketRepository.findTicketIdsByUserId(1L)).thenReturn(List.of(10L, 20L));

            Ticket t1 = buildSampleTicket(10L, "Phim Dune 2", TicketType.MOVIE, TicketStatus.LISTED);
            Ticket t2 = buildSampleTicket(20L, "Xe Hà Nội - Sapa", TicketType.BUS, TicketStatus.AWAITING_USE);

            when(ticketRepository.findById(10L)).thenReturn(Optional.of(t1));
            when(ticketRepository.findById(20L)).thenReturn(Optional.of(t2));
            when(fileStorageService.buildPublicUrl("tickets/sample.jpg")).thenReturn("/files/tickets/sample.jpg");

            List<StarredTicketItemResponse> result = starredTicketUseCase.getStarredTicketsForUser(1L);

            assertThat(result).hasSize(2);
            assertThat(result.get(0).ticketId()).isEqualTo(10L);
            assertThat(result.get(0).title()).isEqualTo("Phim Dune 2");
            assertThat(result.get(0).thumbnailUrl()).isEqualTo("/files/tickets/sample.jpg");
            assertThat(result.get(0).status()).isEqualTo(TicketStatus.LISTED);

            assertThat(result.get(1).ticketId()).isEqualTo(20L);
            assertThat(result.get(1).title()).isEqualTo("Xe Hà Nội - Sapa");
            assertThat(result.get(1).status()).isEqualTo(TicketStatus.AWAITING_USE);
        }
    }

    @Test
    @DisplayName("countStarredTickets trả về tổng số vé đã sao")
    void countStarredTickets_ReturnsCount() {
        when(starredTicketRepository.countByUserId(1L)).thenReturn(5L);

        long count = starredTicketUseCase.countStarredTickets(1L);

        assertThat(count).isEqualTo(5L);
        assertThat(starredTicketUseCase.countStarredTickets(null)).isEqualTo(0L);
    }
}
