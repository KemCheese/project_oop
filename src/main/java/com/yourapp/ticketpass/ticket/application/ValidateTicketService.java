package com.yourapp.ticketpass.ticket.application;

import com.yourapp.ticketpass.shared.exception.ResourceNotFoundException;
import com.yourapp.ticketpass.ticket.domain.Ticket;
import com.yourapp.ticketpass.ticket.domain.TicketAdapter;
import com.yourapp.ticketpass.ticket.domain.TicketRepository;
import com.yourapp.ticketpass.ticket.domain.TicketStatus;
import com.yourapp.ticketpass.ticket.domain.ValidationResult;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Kiem tra dieu kien hop le cua ve khi dang ban tren san marketplace.
 *
 * <p>
 * Service nay khong tu viet luat rieng cho tung loai ve ma uy quyen cho
 * {@link TicketAdapter} tuong ung; phan con lai la cac luat chung cua san nhu
 * gia ban hop le, gio su kien o tuong lai, so ghe khop so ve, va quyen so huu.
 * </p>
 *
 * <p>
 * Module: ticket
 * </p>
 *
 * Chu so huu: Nguoi A
 */
@Service
@RequiredArgsConstructor
public class ValidateTicketService {

    private final TicketRepository ticketRepository;

    private final List<TicketAdapter> ticketAdapters;

    private final Clock clock;

    @Value("${app.ticket.allow-markup:false}")
    private boolean allowMarkup;

    /**
     * Kiem tra ve co du dieu kien dang ban khong.
     *
     * <p>
     * Luong xu ly theo BR-01:
     * 1. Kiem tra cac luat chung cua san (gia ban, thoi gian su kien, anh ve)
     * 2. Tim adapter phu hop theo TicketType
     * 3. Goi adapter.validateListing() de kiem tra luat rieng cua loai ve
     * 4. Gop toan bo loi thanh mot ValidationResult duy nhat
     * </p>
     *
     * @param ticket ve chuan bi dang
     * @return ket qua kiem tra kem danh sach loi
     */
    public ValidationResult validateForListing(Ticket ticket) {
        List<String> errors = new ArrayList<>();

        // --- Luat chung cua san (BR-01) ---

        // Gia ban phai > 0
        if (ticket.getSalePrice() == null || ticket.getSalePrice().compareTo(BigDecimal.ZERO) <= 0) {
            errors.add("Gia ban phai lon hon 0.");
        }

        // BR-01.1: Gia ban khong duoc vuot qua gia goc (chong dau co)
        if (!allowMarkup
                && ticket.getSalePrice() != null
                && ticket.getOriginalPrice() != null
                && ticket.getSalePrice().compareTo(ticket.getOriginalPrice()) > 0) {
            errors.add("Gia ban khong duoc vuot qua gia goc (quy tac chong dau co).");
        }

        // eventStartAt phai o tuong lai
        Instant now = resolveNow();
        if (ticket.getEventStartAt() == null) {
            errors.add("Thoi gian su kien khong duoc de trong.");
        } else if (!ticket.getEventStartAt().isAfter(now)) {
            errors.add("Thoi gian su kien phai nam o tuong lai.");
        }

        // 1.PA B: Ve dang ban moi bat buoc phai co eventEndAt va phai sau eventStartAt
        if (ticket.getEventEndAt() == null) {
            errors.add("Thoi gian ket thuc su kien khong duoc de trong.");
        } else if (ticket.getEventStartAt() != null && !ticket.getEventEndAt().isAfter(ticket.getEventStartAt())) {
            errors.add("Thoi gian ket thuc su kien phai sau thoi gian bat dau.");
        }

        // Phai co anh cong khai
        if (ticket.getTicketImageStorageKey() == null || ticket.getTicketImageStorageKey().isBlank()) {
            errors.add("Phai upload anh ve cong khai.");
        }

        // Phai co anh QR an
        if (ticket.getQrImageStorageKey() == null || ticket.getQrImageStorageKey().isBlank()) {
            errors.add("Phai upload anh QR an (anh check-in bi mat).");
        }

        // --- Luat rieng theo loai ve (uy quyen cho adapter) ---

        TicketAdapter adapter = findAdapter(ticket);
        if (adapter != null) {
            ValidationResult adapterResult = adapter.validateListing(ticket);
            if (!adapterResult.isValid()) {
                errors.addAll(adapterResult.getErrors());
            }
        } else {
            errors.add("Loai ve khong duoc ho tro: " + ticket.getTicketType());
        }

        if (errors.isEmpty()) {
            return ValidationResult.ok();
        }
        return ValidationResult.fail(errors);
    }

    /**
     * Kiem tra mot nguoi mua co duoc phep mua ve nay khong.
     *
     * @param ticketId id cua ve
     * @param buyerId  id nguoi mua
     * @return ket qua kiem tra kem danh sach loi
     */
    public ValidationResult validateForPurchase(Long ticketId, Long buyerId) {
        List<String> errors = new ArrayList<>();

        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket", ticketId));

        // Ve phai dang o trang thai LISTED
        if (ticket.getStatus() != TicketStatus.LISTED) {
            errors.add("Ve khong o trang thai co the mua (trang thai hien tai: " + ticket.getStatus() + ").");
        }

        // Nguoi mua khong duoc trung voi nguoi ban (khong tu mua ve cua chinh minh)
        if (ticket.getSellerId() != null && ticket.getSellerId().equals(buyerId)) {
            errors.add("Ban khong the mua ve cua chinh minh.");
        }

        // eventStartAt phai con o tuong lai
        if (ticket.getEventStartAt() != null && !ticket.getEventStartAt().isAfter(resolveNow())) {
            errors.add("Ve da qua thoi gian su kien, khong the mua.");
        }

        if (errors.isEmpty()) {
            return ValidationResult.ok();
        }
        return ValidationResult.fail(errors);
    }

    /**
     * Kiem tra nguoi ban co duoc phep danh dau da chuyen ve khong.
     *
     * @param ticketId id cua ve
     * @param sellerId id nguoi ban thuc hien thao tac
     * @return ket qua kiem tra kem danh sach loi
     */
    public ValidationResult validateForTransfer(Long ticketId, Long sellerId) {
        List<String> errors = new ArrayList<>();

        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket", ticketId));

        // Nguoi thuc hien phai dung la chu ve
        if (!ticket.getSellerId().equals(sellerId)) {
            errors.add("Ban khong phai la chu so huu cua ve nay.");
        }

        // Ve phai o trang thai DEPOSIT_LOCKED (tien escrow da giu va coc da khoa)
        if (ticket.getStatus() != TicketStatus.DEPOSIT_LOCKED) {
            errors.add("Ve chua san sang de chuyen (trang thai hien tai: " + ticket.getStatus()
                    + ", can: DEPOSIT_LOCKED).");
        }

        // Ve phai co anh QR an de mo khoa cho buyer
        if (ticket.getQrImageStorageKey() == null || ticket.getQrImageStorageKey().isBlank()) {
            errors.add("Ve chua co anh QR an. Vui long upload anh QR truoc khi chuyen ve.");
        }

        if (errors.isEmpty()) {
            return ValidationResult.ok();
        }
        return ValidationResult.fail(errors);
    }

    /**
     * Tim adapter phu hop voi loai ve cua ticket.
     *
     * @param ticket ve can tim adapter
     * @return adapter tuong ung, hoac null neu khong tim thay
     */
    private TicketAdapter findAdapter(Ticket ticket) {
        if (ticket.getTicketType() == null) {
            return null;
        }
        return ticketAdapters.stream()
                .filter(adapter -> adapter.supportedType() == ticket.getTicketType())
                .findFirst()
                .orElse(null);
    }

    private Instant resolveNow() {
        Instant instant = clock != null ? clock.instant() : null;
        return instant != null ? instant : Instant.now();
    }
}
