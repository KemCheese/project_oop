package com.yourapp.ticketpass.ticket.domain;

import java.time.Instant;

public record TimeSlot(Instant start, Instant end) {

    public TimeSlot {
        if (start == null || end == null) {
            throw new IllegalArgumentException("Thời điểm bắt đầu và kết thúc sự kiện không được để trống.");
        }
        if (!end.isAfter(start)) {
            throw new IllegalArgumentException("Thời điểm kết thúc sự kiện phải nằm sau thời điểm bắt đầu.");
        }
    }

    public boolean overlaps(TimeSlot other) {
        if (other == null) {
            return false;
        }
        return this.start.isBefore(other.end) && other.start.isBefore(this.end);
    }
}
