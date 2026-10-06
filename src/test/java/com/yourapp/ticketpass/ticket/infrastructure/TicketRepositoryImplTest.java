package com.yourapp.ticketpass.ticket.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yourapp.ticketpass.ticket.domain.Ticket;
import com.yourapp.ticketpass.ticket.domain.TicketStatus;
import com.yourapp.ticketpass.ticket.domain.TicketType;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit test cho {@link TicketRepositoryImpl} - Phương thức searchListed.
 */
@ExtendWith(MockitoExtension.class)
class TicketRepositoryImplTest {

    @Mock
    private TicketJpaRepository ticketJpaRepository;

    @InjectMocks
    private TicketRepositoryImpl repository;

    @Test
    @DisplayName("searchListed chuẩn hóa keyword và ủy quyền sang ticketJpaRepository")
    void testSearchListedTrimsKeyword() {
        Ticket t = new Ticket();
        t.setId(1L);
        t.setStatus(TicketStatus.LISTED);
        t.setEventName("Anh Trai");

        when(ticketJpaRepository.searchListedTickets(TicketStatus.LISTED, TicketType.CONCERT, "Anh Trai"))
                .thenReturn(List.of(t));

        List<Ticket> results = repository.searchListed("  Anh Trai  ", TicketType.CONCERT);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getEventName()).isEqualTo("Anh Trai");
        verify(ticketJpaRepository).searchListedTickets(TicketStatus.LISTED, TicketType.CONCERT, "Anh Trai");
    }

    @Test
    @DisplayName("searchListed khi keyword null hoặc chỉ có khoảng trắng thì truyền null vào JPA query")
    void testSearchListedBlankKeywordPassesNull() {
        when(ticketJpaRepository.searchListedTickets(TicketStatus.LISTED, null, null))
                .thenReturn(List.of());

        List<Ticket> results = repository.searchListed("   ", null);

        assertThat(results).isEmpty();
        verify(ticketJpaRepository).searchListedTickets(TicketStatus.LISTED, null, null);
    }

    @Test
    @DisplayName("findTopSuggestions chuẩn hóa keyword và giới hạn kết quả bằng PageRequest")
    void testFindTopSuggestionsDelegation() {
        Ticket t = new Ticket();
        t.setId(10L);
        t.setStatus(TicketStatus.LISTED);
        t.setEventName("Dune");

        when(ticketJpaRepository.findTopSuggestions(TicketStatus.LISTED, "Dune", org.springframework.data.domain.PageRequest.of(0, 5)))
                .thenReturn(List.of(t));

        List<Ticket> suggestions = repository.findTopSuggestions("  Dune  ", 5);

        assertThat(suggestions).hasSize(1);
        assertThat(suggestions.get(0).getEventName()).isEqualTo("Dune");
        verify(ticketJpaRepository).findTopSuggestions(TicketStatus.LISTED, "Dune", org.springframework.data.domain.PageRequest.of(0, 5));
    }

    @Test
    @DisplayName("findExpiredListedTickets ủy quyền sang ticketJpaRepository với trạng thái LISTED")
    void testFindExpiredListedTicketsDelegation() {
        java.time.Instant now = java.time.Instant.now();
        Ticket t = new Ticket();
        t.setId(99L);
        when(ticketJpaRepository.findByStatusAndEventStartAtBefore(TicketStatus.LISTED, now))
                .thenReturn(List.of(t));

        List<Ticket> results = repository.findExpiredListedTickets(now);

        assertThat(results).containsExactly(t);
        verify(ticketJpaRepository).findByStatusAndEventStartAtBefore(TicketStatus.LISTED, now);
    }
}
