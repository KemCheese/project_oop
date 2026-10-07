package com.yourapp.ticketpass.dispute.adapter;

import java.time.Duration;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.yourapp.ticketpass.dispute.domain.DisputeWindowPolicy;
import com.yourapp.ticketpass.ticket.domain.TicketType;

/**
 * Cửa sổ khiếu nại cho vé xem phim: <b>2 giờ</b> sau {@code transferredAt}
 * (đọc từ {@code app.dispute.window.movie-hours}).
 *
 * <p>Thời điểm đóng cửa sổ là thời điểm <em>sớm hơn</em> giữa
 * {@code transferredAt + 2h} và {@code eventStartAt}.
 * Một khi buổi chiếu đã bắt đầu thì không thể xác minh vé nữa,
 * nên cửa sổ phải đóng ngay tại {@code eventStartAt} nếu đến trước.</p>
 *
 * <p>Module: dispute</p>
 *
 * <p>Chủ sở hữu: Người B</p>
 */
@Component
public class MovieDisputeWindowPolicy implements DisputeWindowPolicy {

    private final Duration window;
    private static final Duration SELLER_RESPONSE_WINDOW = Duration.ofHours(4);

    public MovieDisputeWindowPolicy(@Value("${app.dispute.window.movie-hours:2}") long movieHours) {
        this.window = Duration.ofHours(movieHours);
    }

    public MovieDisputeWindowPolicy() {
        this(2);
    }

    @Override
    public TicketType supportedType() {
        return TicketType.MOVIE;
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
