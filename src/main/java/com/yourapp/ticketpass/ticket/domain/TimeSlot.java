package com.yourapp.ticketpass.ticket.domain;

import java.time.Instant;

/**
 * Value Object bất biến đại diện cho khoảng thời gian nửa mở {@code [start, end)} của một sự kiện.
 *
 * <p><b>Quy ước toán học:</b> Hai khoảng nửa mở {@code [s1, e1)} và {@code [s2, e2)} trùng nhau
 * khi và chỉ khi {@code s1 < e2 && s2 < e1}. Nhờ đó, hai sự kiện tiếp xúc biên chính xác
 * (ví dụ {@code 13:00–15:00} và {@code 15:00–17:00}) được xác định là <b>không trùng</b>.</p>
 *
 * <p>Module: ticket</p>
 * <p>Chủ sở hữu: Người A (phối hợp Dev C)</p>
 */
public record TimeSlot(Instant start, Instant end) {

    public TimeSlot {
        if (start == null || end == null) {
            throw new IllegalArgumentException("Thời điểm bắt đầu và kết thúc sự kiện không được để trống.");
        }
        if (!end.isAfter(start)) {
            throw new IllegalArgumentException("Thời điểm kết thúc sự kiện phải nằm sau thời điểm bắt đầu.");
        }
    }

    /**
     * Kiểm tra khoảng thời gian này có chồng lấn với một {@link TimeSlot} khác hay không.
     *
     * @param other khung thời gian cần đối chiếu
     * @return {@code true} nếu giao nhau tại ít nhất 1 khoảnh khắc trong khoảng nửa mở {@code [start, end)}
     */
    public boolean overlaps(TimeSlot other) {
        if (other == null) {
            return false;
        }
        return this.start.isBefore(other.end) && other.start.isBefore(this.end);
    }
}
