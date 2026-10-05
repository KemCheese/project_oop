package com.yourapp.ticketpass.ticket.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Kiểm thử tích hợp Web End-to-End cho tính năng Tìm kiếm vé sự kiện qua HTTP GET /tickets.
 *
 * <p>Kiểm tra tích hợp từ giao diện Thymeleaf (base.html, list.html) qua Controller,
 * Repository đến cơ sở dữ liệu H2.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("TicketSearchWebIntegrationTest - Kiểm thử tích hợp tìm kiếm vé sự kiện trên Web")
class TicketSearchWebIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET /tickets hiển thị thanh tìm kiếm trên header và các mục phân loại vé")
    void testGetTickets_rendersSearchBarAndTabs() throws Exception {
        mockMvc.perform(get("/tickets"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("class=\"nav-search-form\"")))
                .andExpect(content().string(containsString("placeholder=\"Tìm sự kiện, phim, tuyến xe...\"")))
                .andExpect(content().string(containsString("Tất cả vé")));
    }

    @Test
    @DisplayName("GET /tickets?keyword=Chông+Gai tìm thấy vé concert và hiển thị banner kết quả")
    void testSearchByKeyword_found() throws Exception {
        mockMvc.perform(get("/tickets").param("keyword", "Chông Gai"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Anh Trai Vượt Ngàn Chông Gai")))
                .andExpect(content().string(containsString("search-meta-bar")))
                .andExpect(content().string(containsString("Kết quả tìm kiếm cho:")))
                .andExpect(content().string(not(containsString("Dune: Part Three"))));
    }

    @Test
    @DisplayName("GET /tickets?keyword=Dune&ticketType=MOVIE tìm thấy vé phim phù hợp cả 2 điều kiện")
    void testSearchByKeywordAndType_found() throws Exception {
        mockMvc.perform(get("/tickets")
                        .param("keyword", "Dune")
                        .param("ticketType", "MOVIE"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Dune: Part Three")))
                .andExpect(content().string(containsString("search-meta-bar")))
                .andExpect(content().string(not(containsString("Chông Gai"))));
    }

    @Test
    @DisplayName("GET /tickets?keyword=KhongTonTaiXYZ hiển thị giao diện Empty State thân thiện")
    void testSearchByKeyword_emptyState() throws Exception {
        mockMvc.perform(get("/tickets").param("keyword", "KhongTonTaiXYZ"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("empty-state")))
                .andExpect(content().string(containsString("Không tìm thấy vé phù hợp")))
                .andExpect(content().string(containsString("Không tìm thấy vé sự kiện nào khớp với từ khóa")))
                .andExpect(content().string(containsString("KhongTonTaiXYZ")))
                .andExpect(content().string(containsString("Xóa từ khóa tìm kiếm")));
    }

    @Test
    @DisplayName("GET /tickets/suggestions?keyword=Chông trả về JSON gợi ý vé concert")
    void testSuggestionsApi_returnsJson() throws Exception {
        mockMvc.perform(get("/tickets/suggestions").param("keyword", "Chông"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].ticketType").value("CONCERT"))
                .andExpect(jsonPath("$[0].ticketTypeLabel").value("Concert"))
                .andExpect(jsonPath("$[0].detailUrl").value("/tickets/1"));
    }

    @Test
    @DisplayName("GET /tickets/suggestions không truyền keyword trả về mảng rỗng []")
    void testSuggestionsApi_emptyKeywordReturnsEmptyArray() throws Exception {
        mockMvc.perform(get("/tickets/suggestions"))
                .andExpect(status().isOk())
                .andExpect(content().string("[]"));
    }

    @Test
    @DisplayName("GET /tickets đọc cookie tp_page_size và áp dụng đúng pageSize")
    void testPagination_readsCookiePageSize() throws Exception {
        mockMvc.perform(get("/tickets").cookie(new Cookie("tp_page_size", "4")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("pageSize", 4))
                .andExpect(model().attribute("currentPage", 0));
    }

    @Test
    @DisplayName("GET /tickets?page=0&size=2 phân trang thành công và hiển thị thanh pagination khi totalPages > 1")
    void testPagination_rendersPaginationBarWhenMultiplePages() throws Exception {
        mockMvc.perform(get("/tickets")
                        .param("page", "0")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("totalPages", 27))
                .andExpect(model().attribute("hasPrevious", false))
                .andExpect(model().attribute("hasNext", true))
                .andExpect(content().string(containsString("class=\"pagination-container\"")))
                .andExpect(content().string(containsString("class=\"pagination-nav\"")))
                .andExpect(content().string(containsString("Hiển thị")));
    }

    @Test
    @DisplayName("GET /tickets?keyword=KhongTonTaiXYZ ẩn hoàn toàn thanh phân trang khi totalPages <= 1")
    void testPagination_hidesPaginationBarWhenSinglePageOrEmpty() throws Exception {
        mockMvc.perform(get("/tickets").param("keyword", "KhongTonTaiXYZ"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("totalPages", 0))
                .andExpect(content().string(not(containsString("class=\"pagination-container\""))));
    }

    @Test
    @DisplayName("GET /tickets?page=999 vượt quá tổng số trang được xử lý an toàn không quăng lỗi 500")
    void testPagination_pageOutOfBoundsClampedGracefully() throws Exception {
        mockMvc.perform(get("/tickets").param("page", "999"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("currentPage", 3));
    }
}
