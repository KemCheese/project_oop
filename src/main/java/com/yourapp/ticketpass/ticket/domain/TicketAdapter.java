package com.yourapp.ticketpass.ticket.domain;

import java.time.Duration;

/**
 * Hop dong Adapter/Strategy cho tung {@link TicketType} tren san marketplace.
 *
 * <p>San marketplace ho tro 3 loai ve: MOVIE, BUS, CONCERT. Moi loai co mot hien thuc
 * rieng trong package {@code com.yourapp.ticketpass.ticket.adapter}. Nho vay, khi san
 * ho tro them mot loai ve moi (vi du ve may bay), ta chi can them mot {@code @Component}
 * moi ma khong sua bat ky service nao dang chay (Open/Closed Principle).</p>
 *
 * <p>{@code com.yourapp.ticketpass.ticket.application.TicketStateMachineService} inject
 * {@code List<TicketAdapter>} do Spring cung cap, roi chon adapter phu hop bang cach so khop
 * {@link #supportedType()} voi {@code Ticket.getTicketType()}.</p>
 *
 * <p>Module: ticket</p>
 *
 * Chu so huu: Nguoi A
 */
public interface TicketAdapter {

    /**
     * Loai ve ma adapter nay phu trach.
     *
     * @return gia tri {@link TicketType} tuong ung, dung de chon adapter luc runtime
     */
    TicketType supportedType();

    /**
     * Do dai cua so khieu nai tinh tu thoi diem ve duoc chuyen cho nguoi mua
     * (tuc la tu khi anh QR an duoc mo khoa).
     *
     * @return khoang thoi gian nguoi mua con quyen mo tranh chap
     */
    Duration disputeWindowAfterTransfer();

    /**
     * Do tre truoc khi giao dich tu dong chuyen sang {@link TicketStatus#AUTO_COMPLETE}
     * neu khong co khieu nai nao duoc mo.
     *
     * @return khoang thoi gian cho truoc khi tu dong hoan tat
     */
    Duration autoCompleteDelay();

    /**
     * Loai ve nay co bat buoc khai thong tin cho ngoi hay khong.
     *
     * @return true neu thieu {@code seatInfo} thi khong cho dang ban
     */
    boolean requiresSeatInfo();

    /**
     * [DEPRECATED] Loai ve nay co bat buoc nop email forward hay khong.
     * Du an da chuyen huong sang mo hinh marketplace, khong con yeu cau
     * email forward de xac thuc. Method nay giu lai de tuong thich voi code cu.
     *
     * @return luon tra ve false trong phien ban marketplace
     */
    boolean requiresEmailForwardProof();

    /**
     * Kiem tra ve co du dieu kien dang ban theo luat rieng cua loai ve nay khong.
     * Kiem tra format nhap lieu, so luong ghe khop so ve, va cac rang buoc rieng.
     *
     * @param ticket ve can kiem tra
     * @return ket qua kiem tra kem danh sach loi neu khong hop le
     */
    ValidationResult validateListing(Ticket ticket);

    /**
     * Thoi luong su kien mac dinh cua loai ve nay.
     * Dung lam fallback khi tinh khung gio cho cac ve cu/mau chua luu {@code eventEndAt}.
     *
     * @return thoi luong mac dinh (duong) cua loai ve
     */
    Duration defaultEventDuration();

    /**
     * Sinh huong dan chuyen ve phu hop voi loai ve.
     * Tren san marketplace, buoc chuyen ve chinh la san mo khoa anh QR an cho buyer.
     *
     * @param ticket ve can sinh huong dan
     * @return cac buoc huong dan hien thi cho buyer sau khi anh QR duoc mo khoa
     */
    TransferInstruction buildTransferInstruction(Ticket ticket);
}
