package com.yourapp.ticketpass.ticket.domain;

import java.time.Instant;
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
     * Xoa mot ve theo id.
     *
     * @param id id cua ve can xoa
     */
    void deleteById(Long id);
}
