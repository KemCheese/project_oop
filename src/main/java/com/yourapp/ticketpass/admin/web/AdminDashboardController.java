package com.yourapp.ticketpass.admin.web;

import com.yourapp.ticketpass.admin.application.AdminDashboardService;
import com.yourapp.ticketpass.admin.application.AdminDashboardService.ResolvedDateRange;
import com.yourapp.ticketpass.admin.web.dto.AdminDashboardSummaryResponse;
import com.yourapp.ticketpass.shared.exception.BusinessRuleViolationException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Controller cung cấp Dashboard Tài chính (Doanh thu, Hoa hồng, Thuế, Snapshot Escrow)
 * và xuất báo cáo CSV dành riêng cho vai trò {@code ROLE_ADMIN}.
 *
 * <p>Module: admin</p>
 * <p>Chủ sở hữu: Người C (Dev C)</p>
 */
@Controller
@RequestMapping("/admin/dashboard")
@RequiredArgsConstructor
public class AdminDashboardController {

    private static final MediaType TEXT_CSV_UTF8 = new MediaType("text", "csv", StandardCharsets.UTF_8);

    private final AdminDashboardService adminDashboardService;

    /**
     * Hiển thị màn hình thống kê doanh thu, hoa hồng, thuế và tỷ lệ giao dịch theo loại vé.
     *
     * <p>Nếu người dùng nhập sai định dạng ngày hoặc {@code fromDate > toDate}, lỗi được hiển thị
     * inline ngay trên view {@code admin/dashboard} kèm dữ liệu mặc định 30 ngày gần nhất thay vì
     * trả trang lỗi chung.</p>
     *
     * @param fromDateRaw ngày bắt đầu ({@code YYYY-MM-DD}) tùy chọn
     * @param toDateRaw ngày kết thúc ({@code YYYY-MM-DD}) tùy chọn
     * @param model model Thymeleaf
     * @return tên view {@code admin/dashboard}
     */
    @GetMapping
    public String dashboard(@RequestParam(value = "fromDate", required = false) String fromDateRaw,
                            @RequestParam(value = "toDate", required = false) String toDateRaw,
                            Model model) {
        LocalDate fromDate = null;
        LocalDate toDate = null;
        String dateError = null;

        try {
            fromDate = parseOptionalDate(fromDateRaw, "Từ ngày");
            toDate = parseOptionalDate(toDateRaw, "Đến ngày");
        } catch (BusinessRuleViolationException ex) {
            dateError = ex.getMessage();
            fromDate = null;
            toDate = null;
        }

        AdminDashboardSummaryResponse summary;
        if (dateError == null) {
            try {
                summary = adminDashboardService.getDashboardSummary(fromDate, toDate);
            } catch (BusinessRuleViolationException ex) {
                dateError = ex.getMessage();
                summary = adminDashboardService.getDashboardSummary(null, null);
            }
        } else {
            summary = adminDashboardService.getDashboardSummary(null, null);
        }

        model.addAttribute("summary", summary);
        boolean keepSubmittedDates = dateError == null || (fromDate != null && toDate != null);
        model.addAttribute("fromDateValue",
                keepSubmittedDates && hasText(fromDateRaw) ? fromDateRaw.trim() : summary.getFromDate().toString());
        model.addAttribute("toDateValue",
                keepSubmittedDates && hasText(toDateRaw) ? toDateRaw.trim() : summary.getToDate().toString());
        if (dateError != null) {
            model.addAttribute("dateError", dateError);
        }

        return "admin/dashboard";
    }

    /**
     * Xuất sổ cái doanh thu sàn ra file CSV mã hóa UTF-8 BOM an toàn cho Excel.
     *
     * @param fromDateRaw ngày bắt đầu ({@code YYYY-MM-DD}) tùy chọn
     * @param toDateRaw ngày kết thúc ({@code YYYY-MM-DD}) tùy chọn
     * @return phản hồi tải file CSV ({@code text/csv; charset=UTF-8})
     */
    @GetMapping("/export.csv")
    public ResponseEntity<byte[]> exportCsv(
            @RequestParam(value = "fromDate", required = false) String fromDateRaw,
            @RequestParam(value = "toDate", required = false) String toDateRaw) {
        LocalDate fromDate = parseOptionalDate(fromDateRaw, "Từ ngày");
        LocalDate toDate = parseOptionalDate(toDateRaw, "Đến ngày");

        ResolvedDateRange range = adminDashboardService.resolveDateRange(fromDate, toDate);
        byte[] csvBytes = adminDashboardService.exportDashboardCsv(range.fromDate(), range.toDate());

        String filename = String.format("ticketpass-bao-cao-%s-den-%s.csv",
                range.fromDate(), range.toDate());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(TEXT_CSV_UTF8);
        headers.setContentDisposition(ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build());
        headers.setCacheControl("no-store, no-cache, must-revalidate");

        return ResponseEntity.ok()
                .headers(headers)
                .body(csvBytes);
    }

    private LocalDate parseOptionalDate(String raw, String fieldLabel) {
        if (!hasText(raw)) {
            return null;
        }
        try {
            return LocalDate.parse(raw.trim());
        } catch (DateTimeParseException ex) {
            throw new BusinessRuleViolationException(
                    fieldLabel + " không đúng định dạng ngày hợp lệ (YYYY-MM-DD).");
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
