package com.yourapp.ticketpass.notification.application;

import com.yourapp.ticketpass.notification.domain.NotificationType;
import com.yourapp.ticketpass.shared.event.DepositTimeoutEvent;
import com.yourapp.ticketpass.shared.event.DisputeOpenedEvent;
import com.yourapp.ticketpass.shared.event.DisputeOutcome;
import com.yourapp.ticketpass.shared.event.DisputeResolvedEvent;
import com.yourapp.ticketpass.shared.event.DisputeRespondedEvent;
import com.yourapp.ticketpass.shared.event.DisputeWithdrawnEvent;
import com.yourapp.ticketpass.shared.event.TicketAutoCancelledDueToBanEvent;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
public class NotificationEventListener {

    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter REMINDER_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("HH:mm 'ngày' dd/MM/yyyy");

    private final NotificationService notificationService;
    private final StarredTicketRepository starredTicketRepository;

    @Autowired
    public NotificationEventListener(NotificationService notificationService,
                                     StarredTicketRepository starredTicketRepository) {
        this.notificationService = notificationService;
        this.starredTicketRepository = starredTicketRepository;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onTicketEscrowPaid(TicketEscrowPaidEvent event) {
        if (event == null) {
            return;
        }
        log.info("Xử lý thông báo ESCROW_PAID cho vé {}", event.getTicketId());
        String formattedPrice = formatCurrency(event.getSalePrice());
        String content = String.format("Vé \"%s\" đã có người mua và thanh toán ký quỹ (%s). Vui lòng vào đóng cọc 50%% trong vòng 15-30 phút để bàn giao mã vé!",
                event.getTicketTitle(), formattedPrice);
        safeSendNotification(
                event.getSellerId(),
                "Có người mua vé của bạn!",
                content,
                NotificationType.ESCROW_PAID,
                "/tickets/" + event.getTicketId()
        );

        try {
            List<Long> watchers = starredTicketRepository.findUserIdsByTicketId(event.getTicketId());
            if (watchers != null) {
                for (Long watcherId : watchers) {
                    if (watcherId != null && !watcherId.equals(event.getBuyerId()) && !watcherId.equals(event.getSellerId())) {
                        String watcherContent = String.format("Vé \"%s\" mà bạn đang theo dõi vừa có người mua!",
                                event.getTicketTitle());
                        safeSendNotification(
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
            log.warn("Không thể gửi thông báo theo dõi vé #{} cho watchers: {}", event.getTicketId(), e.getMessage(), e);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onTicketDepositLocked(TicketDepositLockedEvent event) {
        if (event == null) {
            return;
        }
        log.info("Xử lý thông báo DEPOSIT_LOCKED cho vé {}", event.getTicketId());
        String content = String.format("Người bán đã đóng cọc 50%% bảo đảm cho vé \"%s\". Mã QR / vé bí mật đã được mở khóa, mời bạn vào nhận và kiểm tra vé!",
                event.getTicketTitle());
        safeSendNotification(
                event.getBuyerId(),
                "Người bán đã đóng cọc vé!",
                content,
                NotificationType.DEPOSIT_LOCKED,
                "/tickets/" + event.getTicketId()
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onDisputeOpened(DisputeOpenedEvent event) {
        if (event == null) {
            return;
        }
        log.info("Xử lý thông báo DISPUTE_FILED cho tranh chấp {}", event.getDisputeId());
        String content = String.format("Vé \"%s\" vừa nhận khiếu nại từ người mua với lý do: \"%s\". Vui lòng vào gửi văn bản giải trình trong vòng 24 giờ!",
                event.getTicketTitle(), event.getReason());
        safeSendNotification(
                event.getSellerId(),
                "Vé của bạn bị khiếu nại!",
                content,
                NotificationType.DISPUTE_FILED,
                "/disputes/" + event.getDisputeId()
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onDisputeResponded(DisputeRespondedEvent event) {
        if (event == null) {
            return;
        }
        log.info("Xử lý thông báo DISPUTE_RESPONDED cho tranh chấp {}", event.getDisputeId());
        String content = String.format("Người bán đã gửi văn bản giải trình cho khiếu nại vé \"%s\". Hồ sơ đang chờ Quản trị viên đối soát.",
                event.getTicketTitle());
        safeSendNotification(
                event.getBuyerId(),
                "Người bán đã gửi giải trình",
                content,
                NotificationType.DISPUTE_RESPONDED,
                "/disputes/" + event.getDisputeId()
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onDisputeResolved(DisputeResolvedEvent event) {
        if (event == null) {
            return;
        }
        log.info("Xử lý thông báo DISPUTE_RESOLVED cho tranh chấp {}", event.getDisputeId());
        String targetUrl = "/disputes/" + event.getDisputeId();

        if (event.getOutcome() == DisputeOutcome.BUYER_WINS) {
            safeSendNotification(
                    event.getBuyerId(),
                    "Phán quyết khiếu nại: BẠN THẮNG",
                    String.format("Khiếu nại vé #%d đã có phán quyết Người mua thắng. Tiền ký quỹ 100%% và tiền bồi thường từ cọc người bán đã hoàn lại (giữ nguyên điểm uy tín).", event.getTicketId()),
                    NotificationType.DISPUTE_RESOLVED,
                    targetUrl
            );
            safeSendNotification(
                    event.getSellerId(),
                    "Phán quyết khiếu nại: NGƯỜI MUA THẮNG",
                    String.format("Khiếu nại vé #%d đã có phán quyết Người mua thắng. Cọc của bạn bị xử lý bồi thường và điểm uy tín bị trừ 2 điểm.", event.getTicketId()),
                    NotificationType.DISPUTE_RESOLVED,
                    targetUrl
            );
        } else if (event.getOutcome() == DisputeOutcome.SELLER_WINS) {
            safeSendNotification(
                    event.getSellerId(),
                    "Phán quyết khiếu nại: BẠN THẮNG",
                    String.format("Khiếu nại vé #%d đã có phán quyết Người bán thắng. Tiền bán vé và tiền hoàn cọc đã được giải ngân về tài khoản (giữ nguyên điểm uy tín).", event.getTicketId()),
                    NotificationType.DISPUTE_RESOLVED,
                    targetUrl
            );
            safeSendNotification(
                    event.getBuyerId(),
                    "Phán quyết khiếu nại: NGƯỜI BÁN THẮNG",
                    String.format("Khiếu nại vé #%d đã có phán quyết Người bán thắng. Tiền ký quỹ đã giải ngân cho người bán và bạn bị trừ 2 điểm uy tín.", event.getTicketId()),
                    NotificationType.DISPUTE_RESOLVED,
                    targetUrl
            );
        } else if (event.getOutcome() == DisputeOutcome.WITHDRAWN) {
            safeSendNotification(
                    event.getSellerId(),
                    "Khiếu nại đã được rút",
                    String.format("Khiếu nại vé #%d đã được khép lại do người mua rút đơn. Giao dịch tiếp tục luồng bình thường.", event.getTicketId()),
                    NotificationType.DISPUTE_WITHDRAWN,
                    targetUrl
            );
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onDepositTimeout(DepositTimeoutEvent event) {
        if (event == null) {
            return;
        }
        log.info("Xử lý thông báo DEPOSIT_TIMEOUT cho vé {}", event.getTicketId());
        safeSendNotification(
                event.getBuyerId(),
                "Hủy giao dịch: Người bán quá hạn cọc",
                String.format("Giao dịch vé \"%s\" đã bị hủy do người bán quá hạn nộp cọc. Toàn bộ tiền giữ đã được hoàn lại an toàn.", event.getTicketTitle()),
                NotificationType.DEPOSIT_TIMEOUT,
                "/profile"
        );
        safeSendNotification(
                event.getSellerId(),
                "Bạn đã quá hạn nộp cọc 50%",
                String.format("Bạn đã quá hạn nộp cọc cho vé \"%s\". Giao dịch đã bị hủy và điểm uy tín bị trừ 1 điểm.", event.getTicketTitle()),
                NotificationType.DEPOSIT_TIMEOUT,
                "/profile"
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onTicketReceived(TicketReceivedEvent event) {
        if (event == null) {
            return;
        }
        log.info("Xử lý thông báo TICKET_RECEIVED cho vé {}", event.getTicketId());
        String targetUrl = "/tickets/" + event.getTicketId();

        if (event.isAutoConfirmed()) {
            safeSendNotification(
                    event.getBuyerId(),
                    "Hệ thống tự động xác nhận nhận vé",
                    String.format("Vé \"%s\" đã được hệ thống tự động xác nhận sau 30 phút. Cửa sổ khiếu nại chính thức kích hoạt.", event.getTicketTitle()),
                    NotificationType.TICKET_RECEIVED,
                    targetUrl
            );
            safeSendNotification(
                    event.getSellerId(),
                    "Vé đã được tự động xác nhận",
                    String.format("Vé \"%s\" đã được tự động xác nhận sau 30 phút giao vé. Tiền sẽ được giải ngân khi hết cửa sổ khiếu nại.", event.getTicketTitle()),
                    NotificationType.TICKET_RECEIVED,
                    targetUrl
            );
        } else {
            safeSendNotification(
                    event.getBuyerId(),
                    "Xác nhận nhận vé thành công",
                    String.format("Bạn đã xác nhận nhận vé \"%s\" và mở mã QR check-in (mã QR có hiệu lực trong 30 phút). Cửa sổ khiếu nại chính thức kích hoạt.", event.getTicketTitle()),
                    NotificationType.TICKET_RECEIVED,
                    targetUrl
            );
            safeSendNotification(
                    event.getSellerId(),
                    "Người mua đã xác nhận & mở mã QR",
                    String.format("Người mua đã xác nhận vé hợp lệ và nhấn hiện mã QR check-in cho vé \"%s\" (mã QR có hiệu lực trong 30 phút). Tiền sẽ được giải ngân khi hết cửa sổ khiếu nại.", event.getTicketTitle()),
                    NotificationType.TICKET_RECEIVED,
                    targetUrl
            );
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onDisputeWithdrawn(DisputeWithdrawnEvent event) {
        if (event == null) {
            return;
        }
        log.info("Xử lý thông báo DISPUTE_WITHDRAWN cho tranh chấp {}", event.getDisputeId());
        safeSendNotification(
                event.getSellerId(),
                "Người mua đã rút khiếu nại",
                String.format("Người mua đã chủ động rút khiếu nại đối với vé \"%s\". Giao dịch trở lại luồng bình thường.", event.getTicketTitle()),
                NotificationType.DISPUTE_WITHDRAWN,
                "/disputes/" + event.getDisputeId()
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onTicketTransactionCompleted(TicketTransactionCompletedEvent event) {
        if (event == null) {
            return;
        }
        log.info("Xử lý thông báo TRANSACTION_COMPLETED cho vé {}", event.getTicketId());
        String formattedPrice = formatCurrency(event.getSalePrice());
        safeSendNotification(
                event.getSellerId(),
                "Giao dịch hoàn tất thành công!",
                String.format("Giao dịch vé #%d đã hoàn tất trọn vẹn! Tiền bán vé (%s) đã được giải ngân về tài khoản (+1 điểm uy tín).", event.getTicketId(), formattedPrice),
                NotificationType.TRANSACTION_COMPLETED,
                "/profile"
        );
        safeSendNotification(
                event.getBuyerId(),
                "Giao dịch an toàn thành công!",
                String.format("Giao dịch vé #%d đã khép lại an toàn. Cảm ơn bạn đã tin tưởng sàn TicketPass (+1 điểm uy tín).", event.getTicketId()),
                NotificationType.TRANSACTION_COMPLETED,
                "/profile"
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onTicketExpired(TicketExpiredEvent event) {
        if (event == null) {
            return;
        }
        log.info("Xử lý thông báo TICKET_EXPIRED cho vé {}", event.getTicketId());

        String sellerContent = String.format("Vé \"%s\" mà bạn đăng bán đã hết hạn thời gian sự kiện và đã được hệ thống tự động gỡ khỏi sàn.",
                event.getTicketTitle());
        safeSendNotification(
                event.getSellerId(),
                "Vé đăng bán đã hết hạn!",
                sellerContent,
                NotificationType.TICKET_EXPIRED,
                "/tickets/" + event.getTicketId()
        );

        try {
            List<Long> watchers = starredTicketRepository.findUserIdsByTicketId(event.getTicketId());
            if (watchers != null) {
                for (Long watcherId : watchers) {
                    if (watcherId != null && !watcherId.equals(event.getSellerId())) {
                        String watcherContent = String.format("Vé \"%s\" mà bạn đang theo dõi đã hết hạn thời gian và không còn khả dụng trên sàn.",
                                event.getTicketTitle());
                        safeSendNotification(
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
            log.warn("Không thể gửi thông báo hết hạn vé #{} cho watchers: {}", event.getTicketId(), e.getMessage(), e);
        }
    }

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

        safeSendNotification(
                event.getBuyerId(),
                title,
                content,
                NotificationType.UPCOMING_EVENT_REMINDER,
                "/tickets/" + event.getTicketId()
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onTicketAutoCancelledDueToBan(TicketAutoCancelledDueToBanEvent event) {
        if (event == null || event.getCounterpartyUserId() == null) {
            return;
        }
        log.info("Xử lý thông báo hủy tự động vé #{} cho counterpartyUserId={}",
                event.getTicketId(), event.getCounterpartyUserId());

        String refundSummary;
        if ("BUYER".equals(event.getCounterpartyRole())) {
            refundSummary = event.isEscrowRefunded()
                    ? "100% tiền ký quỹ Escrow đã được hoàn tự động về ví của bạn."
                    : "Giao dịch đã được hủy an toàn.";
        } else {
            refundSummary = event.isDepositRefunded()
                    ? "100% tiền cọc bảo đảm 50% của bạn đã được hoàn tự động về ví."
                    : "Giao dịch đã được hủy an toàn.";
        }

        safeSendNotification(
                event.getCounterpartyUserId(),
                "Giao dịch bị hủy tự động & hoàn tiền",
                String.format("Vé \"%s\" đã được hệ thống tự động hủy do tài khoản đối tác vi phạm giảm điểm uy tín về 0. %s",
                        event.getTicketTitle(), refundSummary),
                NotificationType.ACCOUNT_LIQUIDATED,
                "/wallet"
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onUserAccountModerated(UserAccountModeratedEvent event) {
        if (event == null || event.getUserId() == null) {
            return;
        }
        if ("ACTIVE".equals(event.getTargetStatusName())) {
            log.info("Xử lý thông báo kích hoạt lại tài khoản cho user {}", event.getUserId());
            String reasonPart = (event.getReason() != null && !event.getReason().isBlank())
                    ? " Ghi chú quản trị viên: " + event.getReason()
                    : "";
            safeSendNotification(
                    event.getUserId(),
                    "Tài khoản của bạn đã được mở khóa",
                    "Quản trị viên đã kích hoạt lại tài khoản TicketPass của bạn. Bạn có thể đăng nhập và giao dịch bình thường trở lại." + reasonPart,
                    NotificationType.SYSTEM,
                    "/profile"
            );
        } else if ("BANNED".equals(event.getTargetStatusName()) && event.getAdminId() == null) {
            safeSendNotification(
                    event.getUserId(),
                    "Tài khoản bị khóa vĩnh viễn (Điểm uy tín = 0)",
                    event.getReason() != null ? event.getReason()
                            : "Điểm uy tín của bạn đã về 0. Tài khoản bị khóa vĩnh viễn và toàn bộ số dư ví đã được thanh lý tự động.",
                    NotificationType.ACCOUNT_LIQUIDATED,
                    "/wallet"
            );
        }
    }

    private void safeSendNotification(Long userId, String title, String content,
                                      NotificationType type, String targetUrl) {
        if (userId == null) {
            return;
        }
        try {
            notificationService.sendNotification(userId, title, content, type, targetUrl);
        } catch (Exception ex) {
            log.error("Không thể gửi thông báo {} tới userId={}: {}", type, userId, ex.getMessage(), ex);
        }
    }

    private String formatCurrency(BigDecimal amount) {
        if (amount == null) return "0 đ";
        return String.format("%,d đ", amount.longValue());
    }
}
