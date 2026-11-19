package com.yourapp.ticketpass.notification.application;

import com.yourapp.ticketpass.notification.domain.NotificationType;
import com.yourapp.ticketpass.shared.event.DepositTimeoutEvent;
import com.yourapp.ticketpass.shared.event.DisputeOpenedEvent;
import com.yourapp.ticketpass.shared.event.DisputeOutcome;
import com.yourapp.ticketpass.shared.event.DisputeResolvedEvent;
import com.yourapp.ticketpass.shared.event.DisputeRespondedEvent;
import com.yourapp.ticketpass.shared.event.DisputeWithdrawnEvent;
import com.yourapp.ticketpass.shared.event.TicketDepositLockedEvent;
import com.yourapp.ticketpass.shared.event.TicketEscrowPaidEvent;
import com.yourapp.ticketpass.shared.event.TicketExpiredEvent;
import com.yourapp.ticketpass.shared.event.TicketReceivedEvent;
import com.yourapp.ticketpass.shared.event.TicketTransactionCompletedEvent;
import com.yourapp.ticketpass.shared.event.TicketUpcomingReminderEvent;
import com.yourapp.ticketpass.shared.event.UserAccountModeratedEvent;
import com.yourapp.ticketpass.user.domain.StarredTicketRepository;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Lắng nghe các Domain Event trong hệ thống và tự động tạo thông báo tương ứng.
 *
 * <p>Áp dụng Observer Pattern: Lắng nghe ở phase {@code AFTER_COMMIT} để đảm bảo
 * giao dịch tiền tệ gốc đã commit thành công mới bắn thông báo. Nếu có lỗi trong
 * quá trình tạo thông báo, tuyệt đối không làm ảnh hưởng hay rollback transaction gốc.</p>
 *
 * <p>Module: notification</p>
 * <p>Chủ sở hữu: Người C (Dev C)</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter REMINDER_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("HH:mm 'ngày' dd/MM/yyyy");

    private final NotificationService notificationService;
    private final StarredTicketRepository starredTicketRepository;

    /**
     * 1. Người mua nạp tiền ký quỹ thành công -> Báo Người bán vào đóng cọc 50%,
     * đồng thời báo cho các user đang theo dõi vé này (watchers).
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onTicketEscrowPaid(TicketEscrowPaidEvent event) {
        log.info("Xử lý thông báo ESCROW_PAID cho vé {}", event.getTicketId());
        String formattedPrice = formatCurrency(event.getSalePrice());
        String content = String.format("Vé \"%s\" đã có người mua và thanh toán ký quỹ (%s). Vui lòng vào đóng cọc 50%% trong vòng 15-30 phút để bàn giao mã vé!",
                event.getTicketTitle(), formattedPrice);
        notificationService.sendNotification(
                event.getSellerId(),
                "Có người mua vé của bạn!",
                content,
                NotificationType.ESCROW_PAID,
                "/tickets/" + event.getTicketId()
        );

        // Báo cho các user đã đánh sao theo dõi vé này (loại trừ buyer và seller)
        try {
            List<Long> watchers = starredTicketRepository.findUserIdsByTicketId(event.getTicketId());
            if (watchers != null) {
                for (Long watcherId : watchers) {
                    if (watcherId != null && !watcherId.equals(event.getBuyerId()) && !watcherId.equals(event.getSellerId())) {
                        String watcherContent = String.format("Vé \"%s\" mà bạn đang theo dõi vừa có người mua!",
                                event.getTicketTitle());
                        notificationService.sendNotification(
                                watcherId,
                                "Vé theo dõi đã có người mua!",
                                watcherContent,
                                NotificationType.STARRED_TICKET_BOUGHT,
                                "/tickets/" + event.getTicketId()
                        );
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Không thể gửi thông báo theo dõi vé #{} cho watchers: {}", event.getTicketId(), e.getMessage());
        }
    }

    /**
     * 2. Người bán khóa cọc 50% thành công -> Báo Người mua vào nhận và kiểm tra mã vé.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onTicketDepositLocked(TicketDepositLockedEvent event) {
        log.info("Xử lý thông báo DEPOSIT_LOCKED cho vé {}", event.getTicketId());
        String content = String.format("Người bán đã đóng cọc 50%% bảo đảm cho vé \"%s\". Mã QR / vé bí mật đã được mở khóa, mời bạn vào nhận và kiểm tra vé!",
                event.getTicketTitle());
        notificationService.sendNotification(
                event.getBuyerId(),
                "Người bán đã đóng cọc vé!",
                content,
                NotificationType.DEPOSIT_LOCKED,
                "/tickets/" + event.getTicketId()
        );
    }

    /**
     * 3. Người mua mở khiếu nại vé -> Báo Người bán vào giải trình trong 24h.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onDisputeOpened(DisputeOpenedEvent event) {
        log.info("Xử lý thông báo DISPUTE_FILED cho tranh chấp {}", event.getDisputeId());
        String content = String.format("Vé \"%s\" vừa nhận khiếu nại từ người mua với lý do: \"%s\". Vui lòng vào gửi văn bản giải trình trong vòng 24 giờ!",
                event.getTicketTitle(), event.getReason());
        notificationService.sendNotification(
                event.getSellerId(),
                "Vé của bạn bị khiếu nại!",
                content,
                NotificationType.DISPUTE_FILED,
                "/disputes/" + event.getDisputeId()
        );
    }

    /**
     * 4. Người bán gửi giải trình tranh chấp -> Báo Người mua theo dõi.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onDisputeResponded(DisputeRespondedEvent event) {
        log.info("Xử lý thông báo DISPUTE_RESPONDED cho tranh chấp {}", event.getDisputeId());
        String content = String.format("Người bán đã gửi văn bản giải trình cho khiếu nại vé \"%s\". Hồ sơ đang chờ Quản trị viên đối soát.",
                event.getTicketTitle());
        notificationService.sendNotification(
                event.getBuyerId(),
                "Người bán đã gửi giải trình",
                content,
                NotificationType.DISPUTE_RESPONDED,
                "/disputes/" + event.getDisputeId()
        );
    }

    /**
     * 5. Quản trị viên phán quyết khiếu nại -> Báo kết quả cho cả Buyer và Seller.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onDisputeResolved(DisputeResolvedEvent event) {
        log.info("Xử lý thông báo DISPUTE_RESOLVED cho tranh chấp {}", event.getDisputeId());
        String targetUrl = "/disputes/" + event.getDisputeId();

        if (event.getOutcome() == DisputeOutcome.BUYER_WINS) {
            notificationService.sendNotification(
                    event.getBuyerId(),
                    "Phán quyết khiếu nại: BẠN THẮNG",
                    String.format("Khiếu nại vé #%d đã có phán quyết Người mua thắng. Tiền ký quỹ 100%% và tiền bồi thường từ cọc người bán đã hoàn lại (+1 điểm uy tín).", event.getTicketId()),
                    NotificationType.DISPUTE_RESOLVED,
                    targetUrl
            );
            notificationService.sendNotification(
                    event.getSellerId(),
                    "Phán quyết khiếu nại: NGƯỜI MUA THẮNG",
                    String.format("Khiếu nại vé #%d đã có phán quyết Người mua thắng. Cọc của bạn bị xử lý bồi thường và điểm uy tín bị trừ 15 điểm.", event.getTicketId()),
                    NotificationType.DISPUTE_RESOLVED,
                    targetUrl
            );
        } else if (event.getOutcome() == DisputeOutcome.SELLER_WINS) {
            notificationService.sendNotification(
                    event.getSellerId(),
                    "Phán quyết khiếu nại: BẠN THẮNG",
                    String.format("Khiếu nại vé #%d đã có phán quyết Người bán thắng. Tiền bán vé và tiền hoàn cọc đã được giải ngân về tài khoản (+1 điểm uy tín).", event.getTicketId()),
                    NotificationType.DISPUTE_RESOLVED,
                    targetUrl
            );
            notificationService.sendNotification(
                    event.getBuyerId(),
                    "Phán quyết khiếu nại: NGƯỜI BÁN THẮNG",
                    String.format("Khiếu nại vé #%d đã có phán quyết Người bán thắng. Tiền ký quỹ đã giải ngân cho người bán và bạn bị trừ 15 điểm uy tín.", event.getTicketId()),
                    NotificationType.DISPUTE_RESOLVED,
                    targetUrl
            );
        } else if (event.getOutcome() == DisputeOutcome.WITHDRAWN) {
            notificationService.sendNotification(
                    event.getSellerId(),
                    "Khiếu nại đã được rút",
                    String.format("Khiếu nại vé #%d đã được khép lại do người mua rút đơn. Giao dịch tiếp tục luồng bình thường.", event.getTicketId()),
                    NotificationType.DISPUTE_WITHDRAWN,
                    targetUrl
            );
        }
    }

    /**
     * 6. Quá hạn nộp cọc 50% -> Báo cả Buyer (được hoàn tiền) và Seller (bị phạt điểm).
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onDepositTimeout(DepositTimeoutEvent event) {
        log.info("Xử lý thông báo DEPOSIT_TIMEOUT cho vé {}", event.getTicketId());
        notificationService.sendNotification(
                event.getBuyerId(),
                "Hủy giao dịch: Người bán quá hạn cọc",
                String.format("Giao dịch vé \"%s\" đã bị hủy do người bán quá hạn nộp cọc. Toàn bộ tiền giữ đã được hoàn lại an toàn.", event.getTicketTitle()),
                NotificationType.DEPOSIT_TIMEOUT,
                "/profile"
        );
        notificationService.sendNotification(
                event.getSellerId(),
                "Bạn đã quá hạn nộp cọc 50%",
                String.format("Bạn đã quá hạn nộp cọc cho vé \"%s\". Giao dịch đã bị hủy và điểm uy tín bị trừ 10 điểm.", event.getTicketTitle()),
                NotificationType.DEPOSIT_TIMEOUT,
                "/profile"
        );
    }

    /**
     * 7. Người mua đã nhận vé (thủ công hoặc tự động sau 30 phút) -> Bắt đầu cửa sổ khiếu nại.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onTicketReceived(TicketReceivedEvent event) {
        log.info("Xử lý thông báo TICKET_RECEIVED cho vé {}", event.getTicketId());
        String targetUrl = "/tickets/" + event.getTicketId();

        if (event.isAutoConfirmed()) {
            notificationService.sendNotification(
                    event.getBuyerId(),
                    "Hệ thống tự động xác nhận nhận vé",
                    String.format("Vé \"%s\" đã được hệ thống tự động xác nhận sau 30 phút. Cửa sổ khiếu nại chính thức kích hoạt.", event.getTicketTitle()),
                    NotificationType.TICKET_RECEIVED,
                    targetUrl
            );
            notificationService.sendNotification(
                    event.getSellerId(),
                    "Vé đã được tự động xác nhận",
                    String.format("Vé \"%s\" đã được tự động xác nhận sau 30 phút giao vé. Tiền sẽ được giải ngân khi hết cửa sổ khiếu nại.", event.getTicketTitle()),
                    NotificationType.TICKET_RECEIVED,
                    targetUrl
            );
        } else {
            notificationService.sendNotification(
                    event.getBuyerId(),
                    "Xác nhận nhận vé thành công",
                    String.format("Bạn đã xác nhận nhận vé \"%s\" và mở mã QR check-in (mã QR có hiệu lực trong 30 phút). Cửa sổ khiếu nại chính thức kích hoạt.", event.getTicketTitle()),
                    NotificationType.TICKET_RECEIVED,
                    targetUrl
            );
            notificationService.sendNotification(
                    event.getSellerId(),
                    "Người mua đã xác nhận & mở mã QR",
                    String.format("Người mua đã xác nhận vé hợp lệ và nhấn hiện mã QR check-in cho vé \"%s\" (mã QR có hiệu lực trong 30 phút). Tiền sẽ được giải ngân khi hết cửa sổ khiếu nại.", event.getTicketTitle()),
                    NotificationType.TICKET_RECEIVED,
                    targetUrl
            );
        }
    }

    /**
     * 8. Người mua chủ động rút khiếu nại.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onDisputeWithdrawn(DisputeWithdrawnEvent event) {
        log.info("Xử lý thông báo DISPUTE_WITHDRAWN cho tranh chấp {}", event.getDisputeId());
        notificationService.sendNotification(
                event.getSellerId(),
                "Người mua đã rút khiếu nại",
                String.format("Người mua đã chủ động rút khiếu nại đối với vé \"%s\". Giao dịch trở lại luồng bình thường.", event.getTicketTitle()),
                NotificationType.DISPUTE_WITHDRAWN,
                "/disputes/" + event.getDisputeId()
        );
    }

    /**
     * 9. Giao dịch hoàn tất thành công và giải ngân -> Báo cho cả Seller và Buyer.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onTicketTransactionCompleted(TicketTransactionCompletedEvent event) {
        log.info("Xử lý thông báo TRANSACTION_COMPLETED cho vé {}", event.getTicketId());
        String formattedPrice = formatCurrency(event.getSalePrice());
        notificationService.sendNotification(
                event.getSellerId(),
                "Giao dịch hoàn tất thành công!",
                String.format("Giao dịch vé #%d đã hoàn tất trọn vẹn! Tiền bán vé (%s) đã được giải ngân về tài khoản (+2 điểm uy tín).", event.getTicketId(), formattedPrice),
                NotificationType.TRANSACTION_COMPLETED,
                "/profile"
        );
        notificationService.sendNotification(
                event.getBuyerId(),
                "Giao dịch an toàn thành công!",
                String.format("Giao dịch vé #%d đã khép lại an toàn. Cảm ơn bạn đã tin tưởng sàn TicketPass (+2 điểm uy tín).", event.getTicketId()),
                NotificationType.TRANSACTION_COMPLETED,
                "/profile"
        );
    }

    /**
     * 10. Vé đăng bán quá hạn sự kiện và bị hệ thống tự động gỡ.
     * Báo cho người bán và tất cả người theo dõi (đánh sao) vé này.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onTicketExpired(TicketExpiredEvent event) {
        log.info("Xử lý thông báo TICKET_EXPIRED cho vé {}", event.getTicketId());

        // 1. Thông báo cho người bán
        String sellerContent = String.format("Vé \"%s\" mà bạn đăng bán đã hết hạn thời gian sự kiện và đã được hệ thống tự động gỡ khỏi sàn.",
                event.getTicketTitle());
        notificationService.sendNotification(
                event.getSellerId(),
                "Vé đăng bán đã hết hạn!",
                sellerContent,
                NotificationType.TICKET_EXPIRED,
                "/tickets/" + event.getTicketId()
        );

        // 2. Thông báo cho các user đã đánh sao theo dõi vé này (loại trừ người bán)
        try {
            List<Long> watchers = starredTicketRepository.findUserIdsByTicketId(event.getTicketId());
            if (watchers != null) {
                for (Long watcherId : watchers) {
                    if (watcherId != null && !watcherId.equals(event.getSellerId())) {
                        String watcherContent = String.format("Vé \"%s\" mà bạn đang theo dõi đã hết hạn thời gian và không còn khả dụng trên sàn.",
                                event.getTicketTitle());
                        notificationService.sendNotification(
                                watcherId,
                                "Vé theo dõi đã hết hạn!",
                                watcherContent,
                                NotificationType.STARRED_TICKET_EXPIRED,
                                "/tickets/" + event.getTicketId()
                        );
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Không thể gửi thông báo hết hạn vé #{} cho watchers: {}", event.getTicketId(), e.getMessage());
        }
    }

    /**
     * 11. Nhắc người mua sắp đến giờ sử dụng vé (trước 24 giờ hoặc nhắc sớm khi mua sát giờ &lt;= 24h).
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onTicketUpcomingReminder(TicketUpcomingReminderEvent event) {
        if (event == null || event.getBuyerId() == null) {
            return;
        }
        log.info("Xử lý thông báo UPCOMING_EVENT_REMINDER cho vé {} (lastMinute={})",
                event.getTicketId(), event.isLastMinutePurchase());

        String startText = event.getEventStartAt() != null
                ? REMINDER_TIME_FORMATTER.format(event.getEventStartAt().atZone(VIETNAM_ZONE))
                : "sắp tới";
        String venuePart = (event.getVenue() != null && !event.getVenue().isBlank())
                ? " tại " + event.getVenue()
                : "";

        String title = event.isLastMinutePurchase()
                ? "Sự kiện sắp diễn ra — Chuẩn bị sử dụng vé!"
                : "Nhắc lịch sử dụng vé trong 24 giờ tới!";
        String content = event.isLastMinutePurchase()
                ? String.format("Vé \"%s\"%s bạn vừa mua sẽ bắt đầu lúc %s (trong vòng 24 giờ tới). Hãy mở mã QR để sẵn sàng check-in!",
                        event.getTicketTitle(), venuePart, startText)
                : String.format("Vé \"%s\"%s sẽ bắt đầu lúc %s. Đừng quên kiểm tra mã vé QR và sắp xếp thời gian tham dự nhé!",
                        event.getTicketTitle(), venuePart, startText);

        notificationService.sendNotification(
                event.getBuyerId(),
                title,
                content,
                NotificationType.UPCOMING_EVENT_REMINDER,
                "/tickets/" + event.getTicketId()
        );
    }

    /**
     * 12. Admin mở khóa / kích hoạt lại tài khoản người dùng -&gt; Gửi thông báo hệ thống.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onUserAccountModerated(UserAccountModeratedEvent event) {
        if (event == null || event.getUserId() == null) {
            return;
        }
        if (!"ACTIVE".equals(event.getTargetStatusName())) {
            return;
        }
        log.info("Xử lý thông báo kích hoạt lại tài khoản cho user {}", event.getUserId());
        String reasonPart = (event.getReason() != null && !event.getReason().isBlank())
                ? " Ghi chú quản trị viên: " + event.getReason()
                : "";
        try {
            notificationService.sendNotification(
                    event.getUserId(),
                    "Tài khoản của bạn đã được mở khóa",
                    "Quản trị viên đã kích hoạt lại tài khoản TicketPass của bạn. Bạn có thể đăng nhập và giao dịch bình thường trở lại." + reasonPart,
                    NotificationType.SYSTEM,
                    "/profile"
            );
        } catch (Exception ex) {
            log.warn("Không thể gửi thông báo mở khóa tài khoản cho user #{}: {}", event.getUserId(), ex.getMessage());
        }
    }

    private String formatCurrency(BigDecimal amount) {
        if (amount == null) return "0 đ";
        return String.format("%,d đ", amount.longValue());
    }
}
