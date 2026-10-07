package com.yourapp.ticketpass.dispute.adapter;

import java.time.Duration;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.yourapp.ticketpass.dispute.domain.DisputeWindowPolicy;
import com.yourapp.ticketpass.ticket.domain.TicketType;

/**
 * Cửa sổ khiếu nại cho vé xe khách: <b>3 giờ</b> sau {@code transferredAt}
 * (đọc từ {@code app.dispute.window.bus-hours}).
 *
 * <p>Thời điểm đóng cửa sổ là thời điểm <em>sớm hơn</em> giữa
 * {@code transferredAt + 3h} và {@code eventStartAt}.
 * Nếu xe khởi hành trước khi hết 3 giờ thì cửa sổ đóng ngay tại giờ khởi hành.</p>
 *
 * <p>Module: dispute</p>
 *
 * <p>Chủ sở hữu: Người B</p>
 */
@Component
public class BusDisputeWindowPolicy implements DisputeWindowPolicy {

    private final Duration window;
    private static final Duration SELLER_RESPONSE_WINDOW = Duration.ofHours(8);

    public BusDisputeWindowPolicy(@Value("${app.dispute.window.bus-hours:3}") long busHours) {
        this.window = Duration.ofHours(busHours);
    }

    public BusDisputeWindowPolicy() {
        this(3);
    }

    @Override
    public TicketType supportedType() {
        return TicketType.BUS;
    }

    @Override
    public Duration windowAfterTransfer() {
        return window;
    }

    /** Vùng đệm khiếu nại tại cổng (Gate Dispute - BR-09 Revised): 30 phút sau giờ bắt đầu sự kiện. */
    public static final Duration GATE_DISPUTE_BUFFER = Duration.ofMinutes(30);

    /**
     * Cửa sổ khiếu nại đóng sau thời gian bắt đầu sự kiện 1 ngày (eventStartAt + 1 day).
     * Nếu không có eventStartAt, fallback theo transferredAt + window.
     */
    @Override
    public Instant computeWindowClose(Instant transferredAt, Instant eventStartAt) {
        if (eventStartAt != null) {
            return eventStartAt.plus(Duration.ofDays(1));
        }
        return transferredAt != null ? transferredAt.plus(window) : Instant.now().plus(window);
    }

    /**
     * {@code true} khi {@code now} chưa đạt tới thời điểm đóng cửa sổ.
     */
    @Override
    public boolean isWithinWindow(Instant transferredAt, Instant eventStartAt, Instant now) {
        if (transferredAt == null || now == null) {
            return false;
        }
        return now.isBefore(computeWindowClose(transferredAt, eventStartAt));
    }

    @Override
    public Duration sellerResponseWindow() {
        return SELLER_RESPONSE_WINDOW;
    }
}
