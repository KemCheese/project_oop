package com.yourapp.ticketpass.ticket.domain;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Port luu tru cua module {@code ticket}, khai bao o tang domain.
 *
 * <p>Theo nguyen tac Dependency Inversion, domain va application chi phu thuoc vao interface nay
 * chu khong phu thuoc vao Spring Data JPA. Hien thuc nam o
 * {@code com.yourapp.ticketpass.ticket.infrastructure.TicketRepositoryImpl}, noi delegate sang
 * {@code TicketJpaRepository}. Muon doi sang luu tru khac (in-memory cho test, JDBC thuan)
 * chi can thay hien thuc ma khong sua tang tren.</p>
 *
 * <p>Module: ticket</p>
 *
 * Chu so huu: Nguoi A
 */
public interface TicketRepository {

    /**
     * Luu moi hoac cap nhat mot ve.
     *
     * @param ticket ve can luu
     * @return ve sau khi luu, da co id va version moi nhat
     */
    Ticket save(Ticket ticket);

    /**
     * Tim ve theo khoa chinh.
     *
     * @param id id cua ve
     * @return ve neu ton tai
     */
    Optional<Ticket> findById(Long id);

    /**
     * Lay toan bo ve do mot nguoi ban dang len.
     *
     * @param sellerId id nguoi ban
     * @return danh sach ve cua nguoi ban
     */
    List<Ticket> findBySellerId(Long sellerId);

    /**
     * Lay toan bo ve ma mot nguoi da mua.
     *
     * @param buyerId id nguoi mua
     * @return danh sach ve cua nguoi mua
     */
    List<Ticket> findByBuyerId(Long buyerId);

    /**
     * Lay ve theo trang thai, phuc vu man hinh quan tri va cac job dinh ky.
     *
     * @param status trang thai can loc
     * @return danh sach ve dang o trang thai do
     */
    List<Ticket> findByStatus(TicketStatus status);

    /**
     * Lay cac ve dang rao ban ({@link TicketStatus#LISTED}) theo loai ve.
     *
     * @param ticketType loai ve can loc
     * @return danh sach ve dang ban thuoc loai do
     */
    List<Ticket> findListedByType(TicketType ticketType);

    /**
     * Tim kiem va loc cac ve dang rao ban ({@link TicketStatus#LISTED}) theo tu khoa va loai ve.
     *
     * @param keyword tu khoa tim kiem theo ten su kien hoac tieu de (co the null hoac trong)
     * @param ticketType loai ve can loc (co the null neu tim tat ca loai ve)
     * @return danh sach ve dang ban thoa man dieu kien
     */
    List<Ticket> searchListed(String keyword, TicketType ticketType);

    /**
     * Tim kiem va loc cac ve dang rao ban theo tu khoa, loai ve va ho tro phan trang.
     *
     * @param keyword tu khoa tim kiem theo ten su kien hoac tieu de
     * @param ticketType loai ve can loc
     * @param pageable thong tin phan trang va sap xep
     * @return trang ve thoa man dieu kien
     */
    Page<Ticket> searchListed(String keyword, TicketType ticketType, Pageable pageable);

    /**
     * Lay danh sach ve dang rao ban phuc vu goi y tim kiem nhanh (autocomplete).
     * Sap xep theo thoi gian dang moi nhat va gioi han so luong tra ve.
     *
     * @param keyword tu khoa tim kiem theo ten su kien hoac tieu de
     * @param limit so luong ban ghi toi da (thuong la 5)
     * @return danh sach ve thoa man dieu kien
     */
    List<Ticket> findTopSuggestions(String keyword, int limit);

    /**
     * Lay cac ve den han tu dong hoan tat: dang o trang thai chi dinh va
     * cua so khieu nai da dong truoc moc thoi gian dua vao.
     *
     * @param status trang thai can quet, thuong la {@link TicketStatus#AWAITING_USE}
     * @param before moc thoi gian so sanh voi {@code disputeWindowClosesAt}
     * @return danh sach ve can chuyen sang {@link TicketStatus#AUTO_COMPLETE}
     */
    List<Ticket> findDueForAutoComplete(TicketStatus status, Instant before);

    /**
     * Lay cac ve qua han nap coc (trang thai PAYMENT_ESCROWED va escrowedAt truoc moc chi dinh).
     *
     * @param before moc thoi gian so sanh voi escrowedAt (now - 30 phut)
     * @return danh sach ve qua han nap coc
     */
    List<Ticket> findOverdueDepositTickets(Instant before);

    /**
     * Lay cac ve qua han xac nhan nhan ve (trang thai TRANSFERRED va transferredAt truoc moc chi dinh).
     *
     * @param before moc thoi gian so sanh voi transferredAt (now - 30 phut)
     * @return danh sach ve qua han nhan ve can auto-confirm
     */
    List<Ticket> findOverdueDeliveryTickets(Instant before);

    /**
     * Lay cac ve dang ban (trang thai LISTED) ma thoi diem su kien (eventStartAt) da qua han.
     *
     * @param now moc thoi gian hien tai
     * @return danh sach ve LISTED da qua gio su kien can tu dong go
     */
    List<Ticket> findExpiredListedTickets(Instant now);

    /**
     * Lay danh sach ve ma nguoi mua dang so huu hieu luc trong khung thoi gian co kha nang giao thoa.
     * Van lay cac ve cu co {@code eventEndAt IS NULL} neu {@code eventStartAt < targetEnd}
     * de service ap dung thoi luong mac dinh theo loai ve va doi chieu chuan xac.
     *
     * @param buyerId id nguoi mua
     * @param statuses tap trang thai so huu hieu luc
     * @param targetEnd thoi diem ket thuc cua ve dang xem
     * @param targetStart thoi diem bat dau cua ve dang xem
     * @param excludeTicketId id ve can loai tru (hoac null)
     * @return danh sach ve ung vien co kha nang trung gio
     */
    List<Ticket> findPotentialScheduleConflicts(Long buyerId,
                                                Collection<TicketStatus> statuses,
                                                Instant targetEnd,
                                                Instant targetStart,
                                                Long excludeTicketId);

    /**
     * Lay theo batch cac ve den han nhac lich su dung truoc gio su kien.
     *
     * @param statuses cac trang thai hop le (da khoa coc / da giao ve)
     * @param now moc thoi gian hien tai
     * @param windowEnd moc gioi han tren ({@code now + leadHours})
     * @param pageable gioi han so ban ghi moi luot quet
     * @return danh sach ve chua nhac lich trong cua so thoi gian
     */
    List<Ticket> findTicketsDueFor24hReminder(Collection<TicketStatus> statuses,
                                              Instant now,
                                              Instant windowEnd,
                                              Pageable pageable);

    /**
     * Danh dau nguyen tu mot ve da duoc nhac lich 24h neu truoc do chua gui.
     *
     * @param ticketId id cua ve
     * @param remindedAt thoi diem danh dau
     * @return {@code 1} neu claim thanh cong, {@code 0} neu da duoc nhac truoc do
     */
    int markReminded24hIfUnset(Long ticketId, Instant remindedAt);

    /**
     * Tai hang loat ve theo tap ID phuc vu hien thi thong tin ve tren bao cao Admin (tranh N+1).
     *
     * @param ids tap ID ve can tra cuu
     * @return danh sach ve tim thay (rong neu dau vao rong)
     */
    List<Ticket> findByIdIn(Collection<Long> ids);

    /**
     * Dem so luong ve ket thuc theo loai ve va trang thai trong khoang thoi gian {@code [startInclusive, endExclusive)}
     * tinh theo {@code completedAt}.
     *
     * @param startInclusive moc bat dau (bao gom)
     * @param endExclusive moc ket thuc (khong bao gom)
     * @return danh sach thong ke gom (ticketType, status, count)
     */
    List<TicketTypeStatusCount> countCompletedGroupedByTypeAndStatus(Instant startInclusive, Instant endExclusive);

    /**
     * Dem snapshot tuc thoi (live) toan bo ve hien tai theo loai ve va trang thai.
     *
     * @return danh sach thong ke gom (ticketType, status, count)
     */
    List<TicketTypeStatusCount> countLiveGroupedByTypeAndStatus();

    /**
     * Xoa mot ve theo id.
     *
     * @param id id cua ve can xoa
     */
    void deleteById(Long id);
}
