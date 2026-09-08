package com.yourapp.ticketpass.ticket.application;

import com.yourapp.ticketpass.ticket.domain.Ticket;
import com.yourapp.ticketpass.ticket.domain.TicketAdapter;
import com.yourapp.ticketpass.ticket.domain.TicketRepository;
import com.yourapp.ticketpass.ticket.domain.ValidationResult;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Kiem tra dieu kien hop le cua ve o cac moc quan trong: dang ban, mua, va chuyen ve.
 *
 * <p>Service nay khong tu viet luat rieng cho tung loai ve ma uy quyen cho
 * {@link TicketAdapter} tuong ung; phan con lai la cac luat chung cua san nhu quyen so huu,
 * trang thai hien tai, va chong ban trung ma QR.</p>
 *
 * <p>Module: ticket</p>
 *
 * Chu so huu: Nguoi A
 */
@Service
@RequiredArgsConstructor
public class ValidateTicketService {

    private final TicketRepository ticketRepository;

    private final List<TicketAdapter> ticketAdapters;

    /**
     * Kiem tra ve co du dieu kien dang ban khong.
     *
     * @param ticket ve chuan bi dang
     * @return ket qua kiem tra kem danh sach loi
     */
    public ValidationResult validateForListing(Ticket ticket) {
        throw new UnsupportedOperationException(
                "TODO [Nguoi A] Prompt 2: Chay cac luat chung truoc (salePrice > 0, eventStartAt o tuong lai, "
                        + "qrPayloadHash chua ton tai theo ticketRepository.existsByQrPayloadHash de chan ban trung ve), "
                        + "sau do goi adapter.validateListing(ticket) cua loai ve tuong ung va gop toan bo loi lai "
                        + "thanh mot ValidationResult duy nhat.");
    }

    /**
     * Kiem tra mot nguoi mua co duoc phep mua ve nay khong.
     *
     * @param ticketId id cua ve
     * @param buyerId id nguoi mua
     * @return ket qua kiem tra kem danh sach loi
     */
    public ValidationResult validateForPurchase(Long ticketId, Long buyerId) {
        throw new UnsupportedOperationException(
                "TODO [Nguoi A] Prompt 2: Nap ve, kiem tra status phai la LISTED, buyerId khong duoc trung sellerId "
                        + "(khong tu mua ve cua chinh minh), eventStartAt phai con o tuong lai va con du thoi gian "
                        + "hoan tat chuyen ve; tra ve ValidationResult.ok() hoac fail(danh sach loi).");
    }

    /**
     * Kiem tra nguoi ban co duoc phep danh dau da chuyen ve khong.
     *
     * @param ticketId id cua ve
     * @param sellerId id nguoi ban thuc hien thao tac
     * @return ket qua kiem tra kem danh sach loi
     */
    public ValidationResult validateForTransfer(Long ticketId, Long sellerId) {
        throw new UnsupportedOperationException(
                "TODO [Nguoi A] Prompt 2: Nap ve, kiem tra sellerId dung la chu ve, status phai la DEPOSIT_LOCKED "
                        + "(tien escrow da giu va coc da khoa), va ve da co du du lieu chuyen (transferInstruction, "
                        + "qrImageStorageKey); tra ve ValidationResult.ok() hoac fail(danh sach loi).");
    }
}
