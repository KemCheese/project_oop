package com.yourapp.ticketpass.ticket.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

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
     * Tim ve theo hash payload QR, phuc vu kiem tra ban trung ve.
     *
     * @param qrPayloadHash SHA-256 hex cua payload QR
     * @return ve co cung hash neu ton tai
     */
    Optional<Ticket> findByQrPayloadHash(String qrPayloadHash);

    /**
     * Kiem tra nhanh xem hash payload QR da ton tai trong he thong chua.
     *
     * @param qrPayloadHash SHA-256 hex cua payload QR
     * @return true neu da co ve khac dung cung ma QR
     */
    boolean existsByQrPayloadHash(String qrPayloadHash);

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
