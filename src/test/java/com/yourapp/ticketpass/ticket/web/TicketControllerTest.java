package com.yourapp.ticketpass.ticket.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yourapp.ticketpass.shared.storage.FileStorageService;
import com.yourapp.ticketpass.ticket.application.TransferTicketService;
import com.yourapp.ticketpass.ticket.domain.Ticket;
import com.yourapp.ticketpass.ticket.domain.TicketRepository;
import com.yourapp.ticketpass.ticket.domain.TicketStatus;
import com.yourapp.ticketpass.ticket.domain.TicketType;
import com.yourapp.ticketpass.ticket.web.dto.TicketSummaryResponse;
import com.yourapp.ticketpass.user.application.StarredTicketUseCase;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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

    @Mock
    private StarredTicketUseCase starredTicketUseCase;

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
        @DisplayName("Khi không truyền param: gọi searchListed(null, null, pageable) và trả về view ticket/list")
        void testListDefault() {
            Ticket t = buildTicket(1L, "MOVIE", "Vé xem phim Dune", "Dune: Part Three");
            Pageable pageable = PageRequest.of(0, 16, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
            Page<Ticket> page = new PageImpl<>(List.of(t), pageable, 1);
            when(ticketRepository.searchListed(eq(null), eq(null), any(Pageable.class))).thenReturn(page);

            Model model = new ConcurrentModel();
            String view = controller.list(null, null, 0, null, null, null, model);

            assertThat(view).isEqualTo("ticket/list");
            assertThat(model.getAttribute("selectedType")).isNull();
            assertThat(model.getAttribute("keyword")).isEqualTo("");
            assertThat(model.getAttribute("currentPage")).isEqualTo(0);
            assertThat(model.getAttribute("totalPages")).isEqualTo(1);
            assertThat(model.getAttribute("totalElements")).isEqualTo(1L);
            assertThat(model.getAttribute("pageSize")).isEqualTo(16);
            assertThat(model.getAttribute("hasPrevious")).isEqualTo(false);
            assertThat(model.getAttribute("hasNext")).isEqualTo(false);
            assertThat(model.getAttribute("pageNumbers")).isEqualTo(Collections.emptyList());
            @SuppressWarnings("unchecked")
            List<TicketSummaryResponse> tickets = (List<TicketSummaryResponse>) model.getAttribute("tickets");
            assertThat(tickets).hasSize(1);
            assertThat(tickets.get(0).getEventName()).isEqualTo("Dune: Part Three");
            verify(ticketRepository).searchListed(eq(null), eq(null), any(Pageable.class));
        }

        @Test
        @DisplayName("Khi truyền từ khóa keyword: gọi searchListed với keyword và trim khoảng trắng")
        void testListWithKeyword() {
            Ticket t = buildTicket(2L, "CONCERT", "Vé concert Anh Trai", "Anh Trai Vượt Ngàn Chông Gai");
            Pageable pageable = PageRequest.of(0, 16, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
            Page<Ticket> page = new PageImpl<>(List.of(t), pageable, 1);
            when(ticketRepository.searchListed(eq("Chông Gai"), eq(null), any(Pageable.class))).thenReturn(page);

            Model model = new ConcurrentModel();
            String view = controller.list(null, "  Chông Gai  ", 0, null, null, null, model);

            assertThat(view).isEqualTo("ticket/list");
            assertThat(model.getAttribute("keyword")).isEqualTo("Chông Gai");
            @SuppressWarnings("unchecked")
            List<TicketSummaryResponse> tickets = (List<TicketSummaryResponse>) model.getAttribute("tickets");
            assertThat(tickets).hasSize(1);
            assertThat(tickets.get(0).getEventName()).isEqualTo("Anh Trai Vượt Ngàn Chông Gai");
            verify(ticketRepository).searchListed(eq("Chông Gai"), eq(null), any(Pageable.class));
        }

        @Test
        @DisplayName("Khi truyền cả keyword và ticketType hợp lệ: gọi searchListed với cả 2 điều kiện")
        void testListWithKeywordAndValidType() {
            Ticket t = buildTicket(3L, "BUS", "Vé xe Đà Lạt", "Tuyến Sài Gòn - Đà Lạt");
            Pageable pageable = PageRequest.of(0, 16, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
            Page<Ticket> page = new PageImpl<>(List.of(t), pageable, 1);
            when(ticketRepository.searchListed(eq("Đà Lạt"), eq(TicketType.BUS), any(Pageable.class))).thenReturn(page);

            Model model = new ConcurrentModel();
            String view = controller.list("BUS", "Đà Lạt", 0, null, null, null, model);

            assertThat(view).isEqualTo("ticket/list");
            assertThat(model.getAttribute("selectedType")).isEqualTo("BUS");
            assertThat(model.getAttribute("keyword")).isEqualTo("Đà Lạt");
            @SuppressWarnings("unchecked")
            List<TicketSummaryResponse> tickets = (List<TicketSummaryResponse>) model.getAttribute("tickets");
            assertThat(tickets).hasSize(1);
            assertThat(tickets.get(0).getEventName()).isEqualTo("Tuyến Sài Gòn - Đà Lạt");
            verify(ticketRepository).searchListed(eq("Đà Lạt"), eq(TicketType.BUS), any(Pageable.class));
        }

        @Test
        @DisplayName("Khi truyền ticketType không hợp lệ: bỏ qua lọc loại vé, type = null")
        void testListWithInvalidType() {
            Pageable pageable = PageRequest.of(0, 16, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
            Page<Ticket> page = new PageImpl<>(Collections.emptyList(), pageable, 0);
            when(ticketRepository.searchListed(eq("Hà Nội"), eq(null), any(Pageable.class))).thenReturn(page);

            Model model = new ConcurrentModel();
            String view = controller.list("UNKNOWN_TYPE", "Hà Nội", 0, null, null, null, model);

            assertThat(view).isEqualTo("ticket/list");
            assertThat(model.getAttribute("selectedType")).isNull();
            assertThat(model.getAttribute("keyword")).isEqualTo("Hà Nội");
            @SuppressWarnings("unchecked")
            List<TicketSummaryResponse> tickets = (List<TicketSummaryResponse>) model.getAttribute("tickets");
            assertThat(tickets).isEmpty();
            verify(ticketRepository).searchListed(eq("Hà Nội"), eq(null), any(Pageable.class));
        }

        @Test
        @DisplayName("Khi không tìm thấy vé: trả về danh sách rỗng để view hiển thị Empty State")
        void testListEmptyResult() {
            Pageable pageable = PageRequest.of(0, 16, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
            Page<Ticket> page = new PageImpl<>(Collections.emptyList(), pageable, 0);
            when(ticketRepository.searchListed(eq("Vé không tồn tại"), eq(null), any(Pageable.class))).thenReturn(page);

            Model model = new ConcurrentModel();
            String view = controller.list(null, "Vé không tồn tại", 0, null, null, null, model);

            assertThat(view).isEqualTo("ticket/list");
            assertThat(model.getAttribute("keyword")).isEqualTo("Vé không tồn tại");
            @SuppressWarnings("unchecked")
            List<TicketSummaryResponse> tickets = (List<TicketSummaryResponse>) model.getAttribute("tickets");
            assertThat(tickets).isEmpty();
        }

        @Test
        @DisplayName("Khi truyền tham số phân trang page và cookie: tính toán pageSize và sliding window chính xác")
        void testListWithPaginationAndCookie() {
            Ticket t1 = buildTicket(1L, "MOVIE", "Vé 1", "Sự kiện 1");
            Pageable pageable = PageRequest.of(1, 8, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
            Page<Ticket> page = new PageImpl<>(List.of(t1), pageable, 25);
            when(ticketRepository.searchListed(eq(null), eq(null), any(Pageable.class))).thenReturn(page);

            Model model = new ConcurrentModel();
            String view = controller.list(null, null, 1, null, 8, null, model);

            assertThat(view).isEqualTo("ticket/list");
            assertThat(model.getAttribute("currentPage")).isEqualTo(1);
            assertThat(model.getAttribute("pageSize")).isEqualTo(8);
            assertThat(model.getAttribute("totalPages")).isEqualTo(4);
            assertThat(model.getAttribute("totalElements")).isEqualTo(25L);
            assertThat(model.getAttribute("hasPrevious")).isEqualTo(true);
            assertThat(model.getAttribute("hasNext")).isEqualTo(true);
            assertThat(model.getAttribute("pageNumbers")).isEqualTo(List.of(0, 1, 2, 3));
            assertThat(model.getAttribute("startItem")).isEqualTo(9L);
            assertThat(model.getAttribute("endItem")).isEqualTo(16L);
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
