package com.yourapp.ticketpass.reputation.web.dto;

import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Một dòng lịch sử giao dịch, dùng để hiển thị trong trang chi tiết uy tín.
 *
 * <p>
 * DTO này được xây dựng từ dữ liệu của entity {@code Ticket} — vì trạng thái
 * và timestamp của vé chính là "lịch sử sự kiện" uy tín của người dùng.
 * </p>
 *
 * <p>Module: reputation</p>
 *
 * Chủ sở hữu: Người A
 */
@Getter
@Setter
@NoArgsConstructor
public class TransactionHistoryItem {

    /** ID của vé (dùng để link sang /tickets/{id}). */
    private Long ticketId;

    /** Tên sự kiện của vé. */
    private String eventName;

    /** Tiêu đề vé. */
    private String title;

    /** Giá bán. */
    private BigDecimal salePrice;

    /**
     * Vai trò của user trong giao dịch này.
     * "SELLER" nếu user là người bán, "BUYER" nếu user là người mua.
     */
    private String role;

    /**
     * Nhãn kết quả giao dịch dành cho hiển thị.
     * Ví dụ: "Thành công", "Đang xử lý", "Đã hủy", "Tranh chấp thắng", "Tranh chấp thua"
     */
    private String outcomeLabel;

    /**
     * Màu badge tương ứng với outcome (dùng CSS variable hoặc hex).
     * Ví dụ: "success", "info", "danger", "warning"
     */
    private String outcomeColor;

    /**
     * Thời điểm quan trọng nhất của giao dịch (dùng để hiển thị "thời gian giao dịch").
     * Ưu tiên: completedAt > transferredAt > listedAt > createdAt
     */
    private Instant eventAt;

    /**
     * Trạng thái gốc từ enum TicketStatus (dùng để lọc/debug).
     */
    private String rawStatus;
}
