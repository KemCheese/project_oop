package com.yourapp.ticketpass.ticket.web.dto;

import com.yourapp.ticketpass.ticket.domain.TicketStatus;
import com.yourapp.ticketpass.ticket.domain.TicketType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class TicketResponse {

    private static final Set<TicketStatus> TERMINAL_STATUSES = Collections.unmodifiableSet(EnumSet.of(
            TicketStatus.AUTO_COMPLETE,
            TicketStatus.RESOLVED_BUYER_WINS,
            TicketStatus.RESOLVED_SELLER_WINS,
            TicketStatus.CANCELLED
    ));

    private static final Set<TicketStatus> UNLOCKED_QR_ACTIVE_STATUSES = Collections.unmodifiableSet(EnumSet.of(
            TicketStatus.AWAITING_USE,
            TicketStatus.DISPUTE_UNDER_REVIEW
    ));

    private static final Set<TicketStatus> PRE_TRANSFER_LOCKED_STATUSES = Collections.unmodifiableSet(EnumSet.of(
            TicketStatus.LISTED,
            TicketStatus.PAYMENT_ESCROWED,
            TicketStatus.DEPOSIT_LOCKED
    ));

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

    private Instant eventEndAt;

    private boolean estimatedEndTime;

    private BigDecimal originalPrice;

    private BigDecimal salePrice;

    private String ticketImageUrl;

    private String qrImageUrl;

    private String transferInstruction;

    private Instant disputeWindowClosesAt;

    private Instant createdAt;

    private Instant receivedAt;

    private boolean qrExpired;

    private boolean eventExpired;

    public boolean isTerminalState() {
        return status != null && TERMINAL_STATUSES.contains(status);
    }

    public boolean isEligibleForUnlockedQrBox() {
        if (qrExpired || isTerminalState() || status == null || !UNLOCKED_QR_ACTIVE_STATUSES.contains(status)) {
            return false;
        }
        return disputeWindowClosesAt != null || !eventExpired;
    }

    public boolean isEligibleForLockedQrBox() {
        return !eventExpired && status != null && PRE_TRANSFER_LOCKED_STATUSES.contains(status);
    }
}
