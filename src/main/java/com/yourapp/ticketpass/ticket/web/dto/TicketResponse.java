package com.yourapp.ticketpass.ticket.web.dto;

import com.yourapp.ticketpass.ticket.domain.TicketStatus;
import com.yourapp.ticketpass.ticket.domain.TicketType;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DTO response: du du lieu de render man hinh chi tiet mot ve.
 *
 * <p>Thuoc tang {@code web} mong, KHONG chua entity {@code Ticket}. Cac khoa luu tru file da duoc
 * doi thanh URL cong khai ({@code ticketImageUrl}, {@code qrImageUrl}) truoc khi tra ve view,
 * de tang web khong phai biet chi tiet ve FileStorageService.</p>
 *
 * <p>Module: ticket</p>
 *
 * Chu so huu: Nguoi A
 */
@Getter
@Setter
@NoArgsConstructor
public class TicketResponse {

    private Long id;

    private Long sellerId;

    private Long buyerId;

    private TicketType ticketType;

    private TicketStatus status;

    private String title;

    private String eventName;

    private String venue;

    private String seatInfo;

    private Instant eventStartAt;

    private BigDecimal originalPrice;

    private BigDecimal salePrice;

    private String ticketImageUrl;

    private String qrImageUrl;

    private String transferInstruction;

    private Instant disputeWindowClosesAt;

    private Instant createdAt;
}
