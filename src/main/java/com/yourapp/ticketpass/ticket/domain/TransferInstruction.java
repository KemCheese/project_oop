package com.yourapp.ticketpass.ticket.domain;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Huong dan chuyen ve, duoc sinh rieng theo tung {@link TicketType}.
 *
 * <p>Vi du: ve phim chuyen qua ma dat cho gui cho nguoi mua, ve tau phai lam thu tuc
 * doi ten hanh khach tai nha ga hoac tren cong dat ve, ve xe khach thuong chi can chuyen
 * ma dat cho kem so dien thoai. Doi tuong nay duoc {@link TicketAdapter#buildTransferInstruction(Ticket)}
 * tao ra roi hien thi cho nguoi ban trong man hinh chuyen ve.</p>
 *
 * <p>Module: ticket</p>
 *
 * Chu so huu: Nguoi A
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TransferInstruction {

    /** Loai ve ma huong dan nay ap dung. */
    private TicketType ticketType;

    /** Kenh chuyen ve, vi du "APP_TRANSFER", "EMAIL_FORWARD", "COUNTER". */
    private String channel;

    /** Cac buoc thuc hien theo dung thu tu. */
    private List<String> steps;

    /** Ghi chu them cho nguoi ban, vi du han chot phai chuyen truoc gio dien ra su kien. */
    private String note;
}
