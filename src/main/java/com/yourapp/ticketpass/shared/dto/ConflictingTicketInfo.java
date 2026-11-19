package com.yourapp.ticketpass.shared.dto;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DTO dùng chung xuyên module mô tả một vé mà người mua đang sở hữu hiệu lực
 * bị trùng khung giờ sử dụng với vé dự định mua.
 *
 * <p>Đặt tại {@code shared.dto} (tương tự {@link CommissionBreakdown}, {@link ForfeitBreakdown})
 * để tầng {@code ticket.application} không phụ thuộc ngược vào {@code ticket.web.dto},
 * đồng thời giữ ranh giới sạch cho {@code payment.web} và {@code wallet.web}.</p>
 *
 * <p>Module: shared</p>
 * <p>Chủ sở hữu: Người C (Dev C)</p>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ConflictingTicketInfo {

    private Long ticketId;

    private String title;

    private String eventName;

    private String venue;

    private String statusCode;

    private String statusLabel;

    private Instant eventStartAt;

    private Instant eventEndAt;

    /** {@code true} nếu {@code eventEndAt} được ước tính bằng thời lượng mặc định của loại vé. */
    private boolean estimatedEndTime;

    /** Chuỗi khoảng thời gian định dạng sẵn theo giờ Việt Nam (VD: {@code 13:00 - 15:00 18/11/2026}). */
    private String formattedTimeRange;
}
