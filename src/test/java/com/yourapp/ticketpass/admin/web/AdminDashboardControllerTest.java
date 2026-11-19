package com.yourapp.ticketpass.admin.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yourapp.ticketpass.admin.application.AdminDashboardService;
import com.yourapp.ticketpass.admin.application.AdminDashboardService.ResolvedDateRange;
import com.yourapp.ticketpass.admin.web.dto.AdminDashboardSummaryResponse;
import com.yourapp.ticketpass.shared.exception.BusinessRuleViolationException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;

@ExtendWith(MockitoExtension.class)
@DisplayName("Unit Test - AdminDashboardController")
class AdminDashboardControllerTest {

    @Mock
    private AdminDashboardService adminDashboardService;

    @InjectMocks
    private AdminDashboardController controller;

    private AdminDashboardSummaryResponse sampleSummary() {
        return AdminDashboardSummaryResponse.builder()
                .fromDate(LocalDate.of(2026, 10, 19))
                .toDate(LocalDate.of(2026, 11, 17))
                .releasedCount(2)
                .forfeitedCount(1)
                .totalGmv(new BigDecimal("1000000"))
                .totalCommission(new BigDecimal("50000"))
                .totalForfeitCut(new BigDecimal("35000"))
                .totalPlatformRevenue(new BigDecimal("85000"))
                .totalTaxWithheld(new BigDecimal("15000"))
                .liveHeldEscrowAmount(new BigDecimal("250000"))
                .categoryBreakdowns(List.of())
                .ledgerRows(List.of())
                .build();
    }

    @Test
    @DisplayName("GET /admin/dashboard hợp lệ: nạp summary vào model và trả về admin/dashboard")
    void dashboard_WhenValidDates_ShouldPopulateModel() {
        AdminDashboardSummaryResponse summary = sampleSummary();
        when(adminDashboardService.getDashboardSummary(
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 17))).thenReturn(summary);

        Model model = new ConcurrentModel();
        String view = controller.dashboard("2026-11-01", "2026-11-17", model);

        assertThat(view).isEqualTo("admin/dashboard");
        assertThat(model.getAttribute("summary")).isSameAs(summary);
        assertThat(model.getAttribute("fromDateValue")).isEqualTo("2026-11-01");
        assertThat(model.getAttribute("toDateValue")).isEqualTo("2026-11-17");
        assertThat(model.containsAttribute("dateError")).isFalse();
    }

    @Test
    @DisplayName("GET /admin/dashboard khi sai định dạng hoặc fromDate > toDate: trả view kèm dateError inline")
    void dashboard_WhenInvalidDateOrInvertedRange_ShouldShowInlineError() {
        AdminDashboardSummaryResponse fallback = sampleSummary();
        when(adminDashboardService.getDashboardSummary(null, null)).thenReturn(fallback);

        Model badFormatModel = new ConcurrentModel();
        String badView = controller.dashboard("not-a-date", "2026-11-17", badFormatModel);
        assertThat(badView).isEqualTo("admin/dashboard");
        assertThat(badFormatModel.getAttribute("dateError")).isNotNull();
        assertThat(badFormatModel.getAttribute("summary")).isSameAs(fallback);

        LocalDate from = LocalDate.of(2026, 12, 1);
        LocalDate to = LocalDate.of(2026, 11, 1);
        when(adminDashboardService.getDashboardSummary(from, to))
                .thenThrow(new BusinessRuleViolationException("Ngày bắt đầu không được lớn hơn ngày kết thúc."));

        Model invertedModel = new ConcurrentModel();
        String invertedView = controller.dashboard("2026-12-01", "2026-11-01", invertedModel);
        assertThat(invertedView).isEqualTo("admin/dashboard");
        assertThat(invertedModel.getAttribute("dateError")).isEqualTo("Ngày bắt đầu không được lớn hơn ngày kết thúc.");
    }

    @Test
    @DisplayName("GET /admin/dashboard/export.csv trả header text/csv;charset=UTF-8 và attachment")
    void exportCsv_ShouldReturnUtf8CsvResponse() {
        LocalDate from = LocalDate.of(2026, 11, 1);
        LocalDate to = LocalDate.of(2026, 11, 17);
        when(adminDashboardService.resolveDateRange(from, to))
                .thenReturn(new ResolvedDateRange(from, to, Instant.now(), Instant.now()));
        byte[] bomCsv = "\uFEFFHeader\r\n".getBytes(StandardCharsets.UTF_8);
        when(adminDashboardService.exportDashboardCsv(from, to)).thenReturn(bomCsv);

        ResponseEntity<byte[]> response = controller.exportCsv("2026-11-01", "2026-11-17");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE)).contains("text/csv").contains("UTF-8");
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .contains("ticketpass-bao-cao-2026-11-01-den-2026-11-17.csv");
        assertThat(response.getBody()).isEqualTo(bomCsv);
        verify(adminDashboardService).exportDashboardCsv(from, to);
    }
}

