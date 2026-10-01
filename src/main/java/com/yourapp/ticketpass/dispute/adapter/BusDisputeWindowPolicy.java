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

    /**
     * Trả về thời điểm sớm hơn giữa {@code transferredAt + 3h} và {@code eventStartAt}.
     *
     * <p>Trường hợp biên: nếu {@code eventStartAt} bằng hoặc trước {@code windowEnd}
     * thì trả về {@code eventStartAt}, cửa sổ đóng tại giờ khởi hành.</p>
     */
    @Override
    public Instant computeWindowClose(Instant transferredAt, Instant eventStartAt) {
        Instant windowEnd = transferredAt.plus(window);
        if (eventStartAt == null) {
            return windowEnd;
        }
        return windowEnd.isBefore(eventStartAt) ? windowEnd : eventStartAt;
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
