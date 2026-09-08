package com.yourapp.ticketpass.ticket.web.dto;

import com.yourapp.ticketpass.ticket.domain.TicketStatus;
import com.yourapp.ticketpass.ticket.domain.TicketType;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DTO response ban gon: dung cho danh sach ve va ket qua tim kiem.
 *
 * <p>Chi mang cac truong can de ve mot the ve trong danh sach, tranh keo theo du lieu nang nhu
 * huong dan chuyen ve. Thuoc tang {@code web} mong, KHONG chua entity.</p>
 *
 * <p>Module: ticket</p>
 *
 * Chu so huu: Nguoi A
 */
@Getter
@Setter
@NoArgsConstructor
public class TicketSummaryResponse {

    private Long id;

    private TicketType ticketType;

    private TicketStatus status;

    private String title;

    private String eventName;

    private Instant eventStartAt;

    private BigDecimal salePrice;

    /** URL anh thu nho cua ve, sinh tu ticketImageStorageKey. */
    private String thumbnailUrl;
}
