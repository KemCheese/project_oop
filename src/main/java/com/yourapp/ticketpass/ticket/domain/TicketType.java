package com.yourapp.ticketpass.ticket.domain;

/**
 * Cac loai ve duoc san ho tro.
 *
 * <p>Moi loai ve co mot adapter rieng (Strategy/Adapter pattern) vi luat xac thuc
 * va cua so khieu nai cua tung loai la khac nhau: ve phim chuyen qua ma dat cho va
 * gan nhu su dung ngay, con ve concert co the mua truoc hang thang nen cua so khieu nai
 * dai hon. Xem {@link TicketAdapter} va cac hien thuc trong package
 * {@code com.yourapp.ticketpass.ticket.adapter}.</p>
 *
 * <p>Module: ticket</p>
 *
 * Chu so huu: Nguoi A
 */
public enum TicketType {

    /** Ve xem phim (rap chieu). */
    MOVIE,

    /** Ve su kien am nhac / concert. */
    CONCERT,

    /** Ve tau hoa. */
    TRAIN,

    /** Ve xe khach. */
    BUS
}
