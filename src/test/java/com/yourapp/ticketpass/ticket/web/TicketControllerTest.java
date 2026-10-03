package com.yourapp.ticketpass.ticket.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yourapp.ticketpass.shared.storage.FileStorageService;
import com.yourapp.ticketpass.ticket.application.TransferTicketService;
import com.yourapp.ticketpass.ticket.domain.Ticket;
import com.yourapp.ticketpass.ticket.domain.TicketRepository;
import com.yourapp.ticketpass.ticket.domain.TicketStatus;
import com.yourapp.ticketpass.ticket.domain.TicketType;
import com.yourapp.ticketpass.ticket.web.dto.TicketSummaryResponse;
import com.yourapp.ticketpass.user.domain.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;

/**
 * Unit test cho {@link TicketController} - Tính năng tìm kiếm vé theo tên sự kiện và lọc theo loại vé.
 *
 * <p>Sử dụng Mockito thuần để kiểm thử độc lập tầng Controller.</p>
 */
@ExtendWith(MockitoExtension.class)
class TicketControllerTest {

    @Mock
    private TransferTicketService transferTicketService;

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private TicketController controller;

    private Ticket buildTicket(Long id, String typeStr, String title, String eventName) {
        Ticket t = new Ticket();
        t.setId(id);
        t.setSellerId(10L);
        if (typeStr != null) {
            t.setTicketType(TicketType.valueOf(typeStr));
        }
        t.setStatus(TicketStatus.LISTED);
        t.setTitle(title);
        t.setEventName(eventName);
        t.setSalePrice(new BigDecimal("500000"));
        t.setOriginalPrice(new BigDecimal("600000"));
        t.setEventStartAt(Instant.now().plusSeconds(86400));
        return t;
    }

    @Nested
    @DisplayName("GET /tickets - Tìm kiếm và hiển thị danh sách vé")
    class SearchAndListTests {

        @Test
        @DisplayName("Khi không truyền param: gọi searchListed(null, null) và trả về view ticket/list")
        void testListDefault() {
            Ticket t = buildTicket(1L, "MOVIE", "Vé xem phim Dune", "Dune: Part Three");
            when(ticketRepository.searchListed(null, null)).thenReturn(List.of(t));

            Model model = new ConcurrentModel();
            String view = controller.list(null, null, model);

            assertThat(view).isEqualTo("ticket/list");
            assertThat(model.getAttribute("selectedType")).isNull();
            assertThat(model.getAttribute("keyword")).isEqualTo("");
            @SuppressWarnings("unchecked")
            List<TicketSummaryResponse> tickets = (List<TicketSummaryResponse>) model.getAttribute("tickets");
            assertThat(tickets).hasSize(1);
            assertThat(tickets.get(0).getEventName()).isEqualTo("Dune: Part Three");
            verify(ticketRepository).searchListed(null, null);
        }

        @Test
        @DisplayName("Khi truyền từ khóa keyword: gọi searchListed với keyword và trim khoảng trắng")
        void testListWithKeyword() {
            Ticket t = buildTicket(2L, "CONCERT", "Vé concert Anh Trai", "Anh Trai Vượt Ngàn Chông Gai");
            when(ticketRepository.searchListed("Chông Gai", null)).thenReturn(List.of(t));

            Model model = new ConcurrentModel();
            String view = controller.list(null, "  Chông Gai  ", model);

            assertThat(view).isEqualTo("ticket/list");
            assertThat(model.getAttribute("keyword")).isEqualTo("Chông Gai");
            @SuppressWarnings("unchecked")
            List<TicketSummaryResponse> tickets = (List<TicketSummaryResponse>) model.getAttribute("tickets");
            assertThat(tickets).hasSize(1);
            assertThat(tickets.get(0).getEventName()).isEqualTo("Anh Trai Vượt Ngàn Chông Gai");
            verify(ticketRepository).searchListed("Chông Gai", null);
        }

        @Test
        @DisplayName("Khi truyền cả keyword và ticketType hợp lệ: gọi searchListed với cả 2 điều kiện")
        void testListWithKeywordAndValidType() {
            Ticket t = buildTicket(3L, "BUS", "Vé xe Đà Lạt", "Tuyến Sài Gòn - Đà Lạt");
            when(ticketRepository.searchListed("Đà Lạt", TicketType.BUS)).thenReturn(List.of(t));

            Model model = new ConcurrentModel();
            String view = controller.list("BUS", "Đà Lạt", model);

            assertThat(view).isEqualTo("ticket/list");
            assertThat(model.getAttribute("selectedType")).isEqualTo("BUS");
            assertThat(model.getAttribute("keyword")).isEqualTo("Đà Lạt");
            @SuppressWarnings("unchecked")
            List<TicketSummaryResponse> tickets = (List<TicketSummaryResponse>) model.getAttribute("tickets");
            assertThat(tickets).hasSize(1);
            assertThat(tickets.get(0).getEventName()).isEqualTo("Tuyến Sài Gòn - Đà Lạt");
            verify(ticketRepository).searchListed("Đà Lạt", TicketType.BUS);
        }

        @Test
        @DisplayName("Khi truyền ticketType không hợp lệ: bỏ qua lọc loại vé, type = null")
        void testListWithInvalidType() {
            when(ticketRepository.searchListed("Hà Nội", null)).thenReturn(Collections.emptyList());

            Model model = new ConcurrentModel();
            String view = controller.list("UNKNOWN_TYPE", "Hà Nội", model);

            assertThat(view).isEqualTo("ticket/list");
            assertThat(model.getAttribute("selectedType")).isNull();
            assertThat(model.getAttribute("keyword")).isEqualTo("Hà Nội");
            @SuppressWarnings("unchecked")
            List<TicketSummaryResponse> tickets = (List<TicketSummaryResponse>) model.getAttribute("tickets");
            assertThat(tickets).isEmpty();
            verify(ticketRepository).searchListed("Hà Nội", null);
        }

        @Test
        @DisplayName("Khi không tìm thấy vé: trả về danh sách rỗng để view hiển thị Empty State")
        void testListEmptyResult() {
            when(ticketRepository.searchListed("Vé không tồn tại", null)).thenReturn(Collections.emptyList());

            Model model = new ConcurrentModel();
            String view = controller.list(null, "Vé không tồn tại", model);

            assertThat(view).isEqualTo("ticket/list");
            assertThat(model.getAttribute("keyword")).isEqualTo("Vé không tồn tại");
            @SuppressWarnings("unchecked")
            List<TicketSummaryResponse> tickets = (List<TicketSummaryResponse>) model.getAttribute("tickets");
            assertThat(tickets).isEmpty();
        }
    }

    @Nested
    @DisplayName("GET /tickets/suggestions - API gợi ý tìm kiếm trực tiếp")
    class SuggestionsApiTests {

        @Test
        @DisplayName("Khi keyword rỗng hoặc null: trả về danh sách rỗng [] ngay lập tức")
        void testSuggestionsWithEmptyKeyword() {
            var response1 = controller.suggestions(null);
            var response2 = controller.suggestions("   ");

            assertThat(response1.getStatusCode().is2xxSuccessful()).isTrue();
            assertThat(response1.getBody()).isEmpty();
            assertThat(response2.getBody()).isEmpty();
        }

        @Test
        @DisplayName("Khi keyword hợp lệ: trả về tối đa 5 gợi ý có đầy đủ metadata và giá định dạng")
        void testSuggestionsWithKeyword() {
            Ticket t1 = buildTicket(1L, "CONCERT", "Vé concert Anh Trai", "Anh Trai Vượt Ngàn Chông Gai");
            t1.setTicketImageStorageKey("tickets/anh-trai.jpg");
            t1.setVenue("Sân vận động Mỹ Đình");

            when(ticketRepository.findTopSuggestions("Chông Gai", 5)).thenReturn(List.of(t1));
            when(fileStorageService.buildPublicUrl("tickets/anh-trai.jpg")).thenReturn("/files/tickets/anh-trai.jpg");

            var response = controller.suggestions("  Chông Gai  ");

            assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
            List<com.yourapp.ticketpass.ticket.web.dto.TicketSuggestionResponse> body = response.getBody();
            assertThat(body).hasSize(1);

            com.yourapp.ticketpass.ticket.web.dto.TicketSuggestionResponse item = body.get(0);
            assertThat(item.getId()).isEqualTo(1L);
            assertThat(item.getEventName()).isEqualTo("Anh Trai Vượt Ngàn Chông Gai");
            assertThat(item.getTicketType()).isEqualTo(TicketType.CONCERT);
            assertThat(item.getTicketTypeLabel()).isEqualTo("Concert");
            assertThat(item.getFormattedPrice()).isEqualTo("500.000 ₫");
            assertThat(item.getThumbnailUrl()).isEqualTo("/files/tickets/anh-trai.jpg");
            assertThat(item.getDetailUrl()).isEqualTo("/tickets/1");
            assertThat(item.getVenue()).isEqualTo("Sân vận động Mỹ Đình");
        }
    }
}
