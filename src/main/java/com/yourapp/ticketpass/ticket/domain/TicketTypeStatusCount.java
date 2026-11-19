package com.yourapp.ticketpass.ticket.domain;

/**
 * Bản ghi chiếu (projection) thống kê số lượng vé nhóm theo loại vé và trạng thái.
 *
 * <p>Đặt trong {@code ticket.domain} để giữ {@code shared} hoàn toàn độc lập với domain enum
 * của module {@code ticket}.</p>
 *
 * <p>Module: ticket</p>
 * <p>Chủ sở hữu phối hợp: Dev C (truy vấn báo cáo từ Admin Facade).</p>
 */
public record TicketTypeStatusCount(TicketType ticketType, TicketStatus status, long count) {
}

