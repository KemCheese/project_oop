package com.yourapp.ticketpass.ticket.domain;

import java.time.Duration;

/**
 * Hop dong Adapter/Strategy cho tung {@link TicketType}.
 *
 * <p>Moi loai ve co mot hien thuc rieng trong package
 * {@code com.yourapp.ticketpass.ticket.adapter}. Nho vay, khi san ho tro them mot loai ve moi
 * (vi du ve may bay), ta chi can them mot {@code @Component} moi ma khong sua bat ky service nao
 * dang chay (Open/Closed Principle).</p>
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
     * Do dai cua so khieu nai tinh tu thoi diem ve duoc chuyen cho nguoi mua.
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
     * Loai ve nay co bat buoc nop bang chung email forward tu don vi phat hanh hay khong.
     *
     * @return true neu bat buoc co email forward de xac thuc quyen so huu ve
     */
    boolean requiresEmailForwardProof();

    /**
     * Kiem tra ve co du dieu kien dang ban theo luat rieng cua loai ve nay khong.
     *
     * @param ticket ve can kiem tra
     * @return ket qua kiem tra kem danh sach loi neu khong hop le
     */
    ValidationResult validateListing(Ticket ticket);

    /**
     * Sinh huong dan chuyen ve phu hop voi loai ve va du lieu cu the cua ve.
     *
     * @param ticket ve can sinh huong dan
     * @return cac buoc chuyen ve hien thi cho nguoi ban
     */
    TransferInstruction buildTransferInstruction(Ticket ticket);
}
