package com.yourapp.ticketpass.shared.event;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Sự kiện: Người mua đã nhận vé (hoặc hệ thống tự động xác nhận sau 30 phút).
 * Bắt đầu tính Cửa sổ khiếu nại, cả 2 bên nhận thông báo.
 *
 * <p>Module: shared</p>
 * <p>Chủ sở hữu: Người C (Dev C)</p>
 */
@Getter
@AllArgsConstructor
public class TicketReceivedEvent {
    private final Long ticketId;
    private final Long sellerId;
    private final Long buyerId;
    private final String ticketTitle;
    private final boolean autoConfirmed;
    private final Instant occurredAt;
}
