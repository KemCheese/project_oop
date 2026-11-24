package com.yourapp.ticketpass.ticket.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yourapp.ticketpass.shared.dto.ConflictingTicketInfo;
import com.yourapp.ticketpass.shared.storage.FileStorageService;
import com.yourapp.ticketpass.ticket.application.TicketScheduleService;
import com.yourapp.ticketpass.ticket.application.TransferTicketService;
import com.yourapp.ticketpass.ticket.domain.Ticket;
import com.yourapp.ticketpass.ticket.domain.TicketRepository;
import com.yourapp.ticketpass.ticket.domain.TicketStatus;
import com.yourapp.ticketpass.ticket.domain.TicketType;
import com.yourapp.ticketpass.ticket.web.dto.TicketResponse;
import com.yourapp.ticketpass.ticket.web.dto.TicketSummaryResponse;
import com.yourapp.ticketpass.user.application.StarredTicketUseCase;
import com.yourapp.ticketpass.user.domain.UserRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
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

    @Mock
    private TicketScheduleService ticketScheduleService;

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

    @Nested
    @DisplayName("GET /tickets/{id} & POST /tickets - Guard trùng giờ và validate eventEndAt")
    class ScheduleConflictAndValidationTests {

        @Test
        @DisplayName("detail(): Chỉ check trùng giờ khi người xem là Buyer tiềm năng và vé đang LISTED")
        void testDetailChecksScheduleForPotentialBuyerOnListedTicket() {
            Ticket t = buildTicket(10L, "MOVIE", "Vé mới", "Dune");
            com.yourapp.ticketpass.user.domain.User seller = new com.yourapp.ticketpass.user.domain.User();
            seller.setId(10L);
            seller.setEmail("seller@test.com");

            com.yourapp.ticketpass.user.domain.User buyer = new com.yourapp.ticketpass.user.domain.User();
            buyer.setId(20L);
            buyer.setEmail("buyer@test.com");

            when(ticketRepository.findById(10L)).thenReturn(Optional.of(t));
            when(userRepository.findById(10L)).thenReturn(Optional.of(seller));
            when(userRepository.findByEmail("buyer@test.com")).thenReturn(Optional.of(buyer));
            when(starredTicketUseCase.isStarred(20L, 10L)).thenReturn(false);

            ConflictingTicketInfo conflict = new ConflictingTicketInfo(
                    5L, "Vé cũ", "Concert", "Mỹ Đình", "AWAITING_USE", "Đang giữ vé",
                    Instant.now(), Instant.now().plusSeconds(3600), false, "13:00 - 15:00"
            );
            when(ticketScheduleService.findConflictingTickets(20L, t)).thenReturn(List.of(conflict));

            java.security.Principal buyerPrincipal = () -> "buyer@test.com";
            Model model = new ConcurrentModel();
            String view = controller.detail(10L, model, buyerPrincipal);

            assertThat(view).isEqualTo("ticket/detail");
            assertThat(model.getAttribute("hasScheduleConflict")).isEqualTo(true);
            verify(ticketScheduleService).findConflictingTickets(20L, t);
        }

        @Test
        @DisplayName("detail(): Seller xem vé của chính mình không bị cảnh báo trùng giờ")
        void testDetailSkipsConflictCheckForSeller() {
            Ticket t = buildTicket(11L, "MOVIE", "Vé của mình", "Dune");
            com.yourapp.ticketpass.user.domain.User seller = new com.yourapp.ticketpass.user.domain.User();
            seller.setId(10L);
            seller.setEmail("seller@test.com");

            when(ticketRepository.findById(11L)).thenReturn(Optional.of(t));
            when(userRepository.findById(10L)).thenReturn(Optional.of(seller));
            when(userRepository.findByEmail("seller@test.com")).thenReturn(Optional.of(seller));
            when(starredTicketUseCase.isStarred(10L, 11L)).thenReturn(false);

            java.security.Principal sellerPrincipal = () -> "seller@test.com";
            Model model = new ConcurrentModel();
            controller.detail(11L, model, sellerPrincipal);

            assertThat(model.getAttribute("hasScheduleConflict")).isEqualTo(false);
        }

        @Test
        @DisplayName("create(): Từ chối inline khi eventEndAt <= eventStartAt")
        void testCreateRejectsInvalidEventEndRange() {
            com.yourapp.ticketpass.ticket.web.dto.CreateTicketRequest req =
                    new com.yourapp.ticketpass.ticket.web.dto.CreateTicketRequest();
            java.time.LocalDateTime start = java.time.LocalDateTime.now().plusDays(2);
            req.setEventStartAt(start);
            req.setEventEndAt(start);

            org.springframework.validation.BeanPropertyBindingResult binding =
                    new org.springframework.validation.BeanPropertyBindingResult(req, "createTicketRequest");
            org.springframework.mock.web.MockMultipartFile dummyImg =
                    new org.springframework.mock.web.MockMultipartFile("f", "a.png", "image/png", new byte[] {1});

            Model model = new ConcurrentModel();
            String view = controller.create(req, binding, dummyImg, dummyImg, () -> "s@test.com", model);

            assertThat(view).isEqualTo("ticket/create");
            assertThat(binding.hasFieldErrors("eventEndAt")).isTrue();
        }
    }

    @Nested
    @DisplayName("GET /tickets/{id} - Ẩn khung QR màu xanh với vé đã hết hạn")
    class QrBoxExpirationVisibilityTests {

        private TicketResponse loadDetailResponse(Ticket ticket) {
            com.yourapp.ticketpass.user.domain.User seller = new com.yourapp.ticketpass.user.domain.User();
            seller.setId(10L);
            seller.setEmail("seller@test.com");
            when(ticketRepository.findById(ticket.getId())).thenReturn(Optional.of(ticket));
            when(userRepository.findById(10L)).thenReturn(Optional.of(seller));

            Model model = new ConcurrentModel();
            controller.detail(ticket.getId(), model, null);
            return (TicketResponse) model.getAttribute("ticket");
        }

        @Test
        @DisplayName("Vé AWAITING_USE đã mở quá 30p nhưng còn trong cửa sổ khiếu nại -> qrExpired = false, eligibleForUnlockedQrBox = true")
        void testDetailActiveAwaitingUseShowsUnlockedQrBoxThroughoutDisputeWindow() {
            Ticket t = buildTicket(20L, "BUS", "Vé còn hạn", "Sài Gòn - Đà Lạt");
            t.setStatus(TicketStatus.AWAITING_USE);
            t.setBuyerId(20L);
            t.setEventStartAt(Instant.now().plus(Duration.ofHours(5)));
            t.setEventEndAt(Instant.now().plus(Duration.ofHours(9)));
            t.setReceivedAt(Instant.now().minus(Duration.ofDays(2)));
            t.setDisputeWindowClosesAt(t.getEventStartAt().plus(Duration.ofMinutes(30)));

            TicketResponse response = loadDetailResponse(t);
            assertThat(response.isEventExpired()).isFalse();
            assertThat(response.isQrExpired()).isFalse();
            assertThat(response.isEligibleForUnlockedQrBox()).isTrue();
            assertThat(response.isEligibleForLockedQrBox()).isFalse();
        }

        @Test
        @DisplayName("Vé AWAITING_USE đã quá cửa sổ khiếu nại (dù sự kiện chưa kết thúc) -> qrExpired = true, ẩn khung QR")
        void testDetailPastDisputeWindowHidesUnlockedQrBox() {
            Ticket t = buildTicket(23L, "BUS", "Vé hết cửa sổ khiếu nại", "Sài Gòn - Đà Lạt");
            t.setStatus(TicketStatus.AWAITING_USE);
            t.setBuyerId(20L);
            t.setEventStartAt(Instant.now().minus(Duration.ofMinutes(40)));
            t.setEventEndAt(Instant.now().plus(Duration.ofHours(2)));
            t.setReceivedAt(Instant.now().minus(Duration.ofDays(1)));
            t.setDisputeWindowClosesAt(Instant.now().minus(Duration.ofMinutes(10)));

            TicketResponse response = loadDetailResponse(t);
            assertThat(response.isEventExpired()).isFalse();
            assertThat(response.isQrExpired()).isTrue();
            assertThat(response.isEligibleForUnlockedQrBox()).isFalse();
            assertThat(response.isEligibleForLockedQrBox()).isFalse();
        }

        @Test
        @DisplayName("Vé đã qua thời gian sự kiện -> eventExpired = true, ẩn cả khung xanh lẫn khung khóa")
        void testDetailExpiredEventHidesAllQrBoxes() {
            Ticket t = buildTicket(21L, "BUS", "Vé hết hạn sự kiện", "Cần Thơ - Rạch Giá");
            t.setStatus(TicketStatus.AWAITING_USE);
            t.setBuyerId(20L);
            t.setEventStartAt(Instant.now().minus(Duration.ofDays(5)));
            t.setEventEndAt(Instant.now().minus(Duration.ofDays(5)).plus(Duration.ofHours(4)));
            t.setReceivedAt(Instant.now().minus(Duration.ofDays(6)));

            TicketResponse response = loadDetailResponse(t);
            assertThat(response.isEventExpired()).isTrue();
            assertThat(response.isQrExpired()).isTrue();
            assertThat(response.isEligibleForUnlockedQrBox()).isFalse();
            assertThat(response.isEligibleForLockedQrBox()).isFalse();
        }

        @Test
        @DisplayName("Vé AUTO_COMPLETE -> trạng thái kết thúc, ẩn toàn bộ khung xanh và khung khóa")
        void testDetailTerminalOrQrExpiredHidesAllQrBoxes() {
            Ticket autoComplete = buildTicket(22L, "BUS", "Vé hoàn tất cũ", "Cần Thơ - Rạch Giá");
            autoComplete.setStatus(TicketStatus.AUTO_COMPLETE);
            autoComplete.setBuyerId(20L);
            autoComplete.setEventStartAt(Instant.now().plus(Duration.ofHours(3)));
            autoComplete.setEventEndAt(Instant.now().plus(Duration.ofHours(7)));
            autoComplete.setReceivedAt(Instant.now().minus(Duration.ofHours(1)));
            autoComplete.setDisputeWindowClosesAt(Instant.now().minus(Duration.ofMinutes(5)));

            TicketResponse response = loadDetailResponse(autoComplete);
            assertThat(response.isTerminalState()).isTrue();
            assertThat(response.isQrExpired()).isTrue();
            assertThat(response.isEligibleForUnlockedQrBox()).isFalse();
            assertThat(response.isEligibleForLockedQrBox()).isFalse();
        }
    }
}
