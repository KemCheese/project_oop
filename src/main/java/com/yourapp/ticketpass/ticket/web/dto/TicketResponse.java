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

/**
 * DTO response: du du lieu de render man hinh chi tiet mot ve.
 *
 * <p>Thuoc tang {@code web} mong, KHONG chua entity {@code Ticket}. Cac khoa luu tru file da duoc
 * doi thanh URL cong khai ({@code ticketImageUrl}, {@code qrImageUrl}) truoc khi tra ve view,
 * de tang web khong phai biet chi tiet ve FileStorageService.</p>
 *
 * <p>Module: ticket</p>
 *
 * Chu so huu: Nguoi A
 */
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
            TicketStatus.TECHNICAL_ISSUE,
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

    /** {@code true} nếu {@code eventEndAt} hiện đang ước tính theo thời lượng mặc định của loại vé. */
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

    /** {@code true} nếu thời gian sử dụng sự kiện của vé đã trôi qua so với hiện tại. */
    private boolean eventExpired;

    /** Kiểm tra vé đã rơi vào trạng thái kết thúc hoặc đã hủy giao dịch. */
    public boolean isTerminalState() {
        return status != null && TERMINAL_STATUSES.contains(status);
    }

    /** Chỉ hiển thị khung xanh mở khóa QR khi vé còn hiệu lực sử dụng, chưa kết thúc và QR chưa hết 30 phút. */
    public boolean isEligibleForUnlockedQrBox() {
        return !eventExpired && !qrExpired && !isTerminalState()
                && status != null && UNLOCKED_QR_ACTIVE_STATUSES.contains(status);
    }

    /** Chỉ hiển thị khung khóa bảo mật khi vé còn hạn sự kiện và đang ở giai đoạn trước khi chuyển vé. */
    public boolean isEligibleForLockedQrBox() {
        return !eventExpired && status != null && PRE_TRANSFER_LOCKED_STATUSES.contains(status);
    }

    /** Chỉ hiển thị khung xác nhận vé ở trạng thái TRANSFERRED khi sự kiện chưa hết hạn. */
    public boolean isEligibleForTransferVerifyBox() {
        return !eventExpired && status == TicketStatus.TRANSFERRED;
    }
}
