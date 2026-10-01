package com.yourapp.ticketpass.dispute.adapter;

import java.time.Duration;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.yourapp.ticketpass.dispute.domain.DisputeWindowPolicy;
import com.yourapp.ticketpass.ticket.domain.TicketType;

/**
 * Cửa sổ khiếu nại cho vé tàu hỏa: <b>6 giờ</b> sau {@code transferredAt}
 * (đọc từ {@code app.dispute.window.train-hours}).
 *
 * <p>Thời điểm đóng cửa sổ là thời điểm <em>sớm hơn</em> giữa
 * {@code transferredAt + 6h} và {@code eventStartAt}.
 * Nếu tàu khởi hành trước khi hết 6 giờ thì cửa sổ đóng ngay tại giờ khởi hành.</p>
 *
 * <p><b>Lưu ý:</b> {@link TicketType#TRAIN} đã deprecated trong enum nhưng vẫn giữ
 * để xử lý các vé cũ còn lưu trong hệ thống.</p>
 *
 * <p>Module: dispute</p>
 *
 * <p>Chủ sở hữu: Người B</p>
 */
@Component
public class TrainDisputeWindowPolicy implements DisputeWindowPolicy {

    private final Duration window;
    private static final Duration SELLER_RESPONSE_WINDOW = Duration.ofHours(12);

    public TrainDisputeWindowPolicy(@Value("${app.dispute.window.train-hours:6}") long trainHours) {
        this.window = Duration.ofHours(trainHours);
    }

    public TrainDisputeWindowPolicy() {
        this(6);
    }

    @Override
    public TicketType supportedType() {
        return TicketType.TRAIN;
    }

    @Override
    public Duration windowAfterTransfer() {
        return window;
    }

    /**
     * Trả về thời điểm sớm hơn giữa {@code transferredAt + 6h} và {@code eventStartAt}.
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
