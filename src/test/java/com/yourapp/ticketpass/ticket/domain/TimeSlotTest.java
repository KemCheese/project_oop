package com.yourapp.ticketpass.ticket.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("TimeSlot Value Object Test - Khoảng nửa mở [start, end)")
class TimeSlotTest {

    @Test
    @DisplayName("Từ chối khi start hoặc end bị null hoặc end <= start")
    void testInvalidBoundsThrowException() {
        Instant t1 = Instant.parse("2026-11-18T06:00:00Z"); // 13:00 VN
        Instant t2 = Instant.parse("2026-11-18T08:00:00Z"); // 15:00 VN

        assertThatThrownBy(() -> new TimeSlot(null, t2)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TimeSlot(t1, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TimeSlot(t1, t1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TimeSlot(t2, t1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Biên tiếp xúc chính xác (13:00-15:00 vs 15:00-17:00) KHÔNG bị coi là trùng")
    void testAdjacentBoundaryDoesNotOverlap() {
        TimeSlot first = new TimeSlot(
                Instant.parse("2026-11-18T06:00:00Z"),
                Instant.parse("2026-11-18T08:00:00Z")
        );
        TimeSlot second = new TimeSlot(
                Instant.parse("2026-11-18T08:00:00Z"),
                Instant.parse("2026-11-18T10:00:00Z")
        );

        assertThat(first.overlaps(second)).isFalse();
        assertThat(second.overlaps(first)).isFalse();
        assertThat(first.overlaps(null)).isFalse();
    }

    @Test
    @DisplayName("Giao nhau dù chỉ 1 giây hoặc bao trùm toàn phần -> PHẢI báo trùng")
    void testPartialAndNestedIntervalsOverlap() {
        TimeSlot slotA = new TimeSlot(
                Instant.parse("2026-11-18T06:00:00Z"),
                Instant.parse("2026-11-18T08:01:00Z")
        );
        TimeSlot slotB = new TimeSlot(
                Instant.parse("2026-11-18T08:00:00Z"),
                Instant.parse("2026-11-18T10:00:00Z")
        );
        TimeSlot outer = new TimeSlot(
                Instant.parse("2026-11-18T05:00:00Z"),
                Instant.parse("2026-11-18T12:00:00Z")
        );

        assertThat(slotA.overlaps(slotB)).isTrue();
        assertThat(slotB.overlaps(slotA)).isTrue();
        assertThat(outer.overlaps(slotB)).isTrue();
        assertThat(slotB.overlaps(outer)).isTrue();
    }
}
