package com.yourapp.ticketpass.ticket.domain;

/**
 * Trang thai cua mot ve trong vong doi giao dich tren san.
 *
 * <p>State machine cua ve:</p>
 * <pre>
 * LISTED -&gt; PAYMENT_ESCROWED -&gt; DEPOSIT_LOCKED -&gt; TRANSFERRED -&gt; AWAITING_USE
 *   -&gt; AUTO_COMPLETE                        (het cua so khieu nai, khong tranh chap)
 *   -&gt; DISPUTE_UNDER_REVIEW -&gt; RESOLVED_BUYER_WINS | RESOLVED_SELLER_WINS
 * </pre>
 *
 * <p>Thu tu khai bao cua enum phan anh dung thu tu tien trien cua state machine.
 * Noi duy nhat duoc phep doi trang thai la
 * {@code com.yourapp.ticketpass.ticket.application.TicketStateMachineService}.</p>
 *
 * <p>Module: ticket</p>
 *
 * Chu so huu: Nguoi A
 */
public enum TicketStatus {

    /** Ve vua duoc dang ban, chua co nguoi mua. */
    LISTED,

    /** Nguoi mua da thanh toan, tien dang duoc giu o escrow. */
    PAYMENT_ESCROWED,

    /** Tien coc cua nguoi ban da bi khoa lam bao dam. */
    DEPOSIT_LOCKED,

    /** Nguoi ban da danh dau chuyen ve cho nguoi mua. */
    TRANSFERRED,

    /** Nguoi mua da xac nhan nhan ve, dang trong cua so khieu nai cho toi luc su dung. */
    AWAITING_USE,

    /** Het cua so khieu nai ma khong co tranh chap: giao dich tu dong hoan tat. */
    AUTO_COMPLETE,

    /** Co khieu nai, admin dang xu ly tranh chap. */
    DISPUTE_UNDER_REVIEW,

    /** Tranh chap ket thuc: nguoi mua thang, tien escrow hoan lai cho nguoi mua. */
    RESOLVED_BUYER_WINS,

    /** Tranh chap ket thuc: nguoi ban thang, tien escrow duoc giai ngan cho nguoi ban. */
    RESOLVED_SELLER_WINS,

    /** Ve da bi huy do nguoi ban tu huy khi con o trang thai LISTED hoac do timeout nap coc. */
    CANCELLED,

    /** Nguoi mua bao loi anh QR hong hoac khong xem duoc; tam dung timeout nhan ve de ky thuat/admin can thiep. */
    TECHNICAL_ISSUE
}
