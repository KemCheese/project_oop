package com.yourapp.ticketpass.ticket.domain;

/**
 * Cac loai ve duoc san marketplace ho tro.
 *
 * <p>San TicketPass hoat dong theo mo hinh marketplace (kieu Shopee cho ve),
 * ho tro 3 loai ve chinh: ve xem phim, ve xe khach va ve concert.
 * Moi loai ve co mot adapter rieng (Strategy/Adapter pattern) vi dieu kien dang ban
 * va cua so khieu nai cua tung loai la khac nhau: ve phim thuong duoc su dung ngay
 * nen cua so khieu nai ngan, con ve concert co the mua truoc hang tuan nen cua so dai hon.
 * Xem {@link TicketAdapter} va cac hien thuc trong package
 * {@code com.yourapp.ticketpass.ticket.adapter}.</p>
 *
 * <p>Module: ticket</p>
 *
 * Chu so huu: Nguoi A
 */
public enum TicketType {

    /**
     * Ve xem phim.
     * Truong bat buoc: ten phim, ten rap (chon tu danh sach), gio chieu,
     * so luong ve, ghe (khop so luong ve), gia ban, anh cong khai, anh QR an.
     */
    MOVIE,

    /**
     * Ve su kien am nhac / concert.
     * Truong bat buoc: ten concert, dia diem, loai ve (ngoi/dung),
     * neu dung -> line/zone, neu ngoi -> ghe/zone,
     * gio bat dau, so luong ve, gia ban, anh cong khai, anh QR an.
     */
    CONCERT,

    /**
     * [DEPRECATED] Ve tau hoa - loai ve nay da bi loai bo khoi pham vi du an.
     * Du an chuyen huong sang mo hinh marketplace, chi ho tro 3 loai ve:
     * MOVIE, CONCERT, BUS. Xem 00-tong-quan-du-an.md.
     */
    TRAIN,

    /**
     * Ve xe khach cua cac nha xe (KHONG phai ve xe buyt noi do).
     * Truong bat buoc: hang xe (chon tu danh sach), diem xuat phat, diem den,
     * gio chay, so luong cho, ten ghe (khop so luong cho), gia ban,
     * anh cong khai, anh QR an.
     */
    BUS
}
