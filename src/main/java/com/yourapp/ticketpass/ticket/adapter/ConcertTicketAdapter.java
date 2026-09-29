package com.yourapp.ticketpass.ticket.adapter;

import com.yourapp.ticketpass.ticket.domain.Ticket;
import com.yourapp.ticketpass.ticket.domain.TicketAdapter;
import com.yourapp.ticketpass.ticket.domain.TicketType;
import com.yourapp.ticketpass.ticket.domain.TransferInstruction;
import com.yourapp.ticketpass.ticket.domain.ValidationResult;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Adapter cho ve concert / su kien am nhac tren san marketplace.
 *
 * <p>Truong bat buoc khi dang ve concert: ten concert (nhap chuan dinh dang), dia diem,
 * loai ve (chon: Ngoi hoac Dung — neu dung thi hien o nhap Line/Zone, neu ngoi thi hien
 * o nhap Ghe/Zone), gio bat dau (nhap tung o theo dinh dang co san), so luong ve,
 * ghe/zone (phai khop voi so luong ve), gia ban, anh cong khai va anh QR an.</p>
 *
 * <p>Cua so khieu nai DU KIEN: 24 gio sau khi ve duoc chuyen (anh QR an mo khoa), doc tu
 * cau hinh {@code app.dispute.window.concert-hours}, va luon phai dong truoc
 * {@code eventStartAt}. Ve concert thuong duoc sang tay truoc su kien hang tuan nen
 * cua so dai nhat trong ba loai ve.</p>
 *
 * <p>Module: ticket</p>
 *
 * Chu so huu: Nguoi A
 */
@Component
public class ConcertTicketAdapter implements TicketAdapter {

    @Value("${app.dispute.window.concert-hours:24}")
    private int disputeWindowHours;

    @Override
    public TicketType supportedType() {
        return TicketType.CONCERT;
    }

    @Override
    public Duration disputeWindowAfterTransfer() {
        return Duration.ofHours(disputeWindowHours);
    }

    @Override
    public Duration autoCompleteDelay() {
        // Cua so khieu nai cong them 1 gio bien an toan
        return disputeWindowAfterTransfer().plusHours(1);
    }

    /**
     * Ve concert KHONG bat buoc seatInfo vi nhieu truong hop khong co ghe:
     * ve dung khu fanzone, ve general admission, ve ban theo khu vuc.
     * Bat buoc seatInfo voi concert se khien nguoi ban phai nhap du lieu gia
     * de vuot validation — lam du lieu ban hon chu khong sach hon.
     * Xem BR-01.2 trong 04-quy-tac-nghiep-vu.md.
     */
    @Override
    public boolean requiresSeatInfo() {
        return false;
    }

    /**
     * [DEPRECATED] Du an da chuyen sang mo hinh marketplace,
     * khong con yeu cau email forward de xac thuc.
     */
    @Override
    public boolean requiresEmailForwardProof() {
        return false;
    }

    @Override
    public ValidationResult validateListing(Ticket ticket) {
        List<String> errors = new ArrayList<>();

        // Kiem tra eventName (ten concert) khong duoc rong
        if (ticket.getEventName() == null || ticket.getEventName().isBlank()) {
            errors.add("Ve concert bat buoc phai co ten su kien.");
        }

        // Kiem tra venue (dia diem to chuc) khong duoc rong
        if (ticket.getVenue() == null || ticket.getVenue().isBlank()) {
            errors.add("Ve concert bat buoc phai co dia diem to chuc.");
        }

        if (errors.isEmpty()) {
            return ValidationResult.ok();
        }
        return ValidationResult.fail(errors);
    }

    @Override
    public TransferInstruction buildTransferInstruction(Ticket ticket) {
        List<String> steps = List.of(
                "San da mo khoa anh QR an cua ve cho ban.",
                "Vao muc 'Ve da mua' de xem anh QR.",
                "Su dung anh QR nay de check-in tai " + (ticket.getVenue() != null ? ticket.getVenue() : "dia diem to chuc") + ".",
                "Mang theo CCCD/CMND de doi chieu neu ban to chuc yeu cau."
        );

        String note = "Han su dung: truoc gio bat dau su kien ghi tren ve. "
                + "Neu gap van de, ban co " + disputeWindowHours + " gio sau khi nhan ve de mo khieu nai.";

        return new TransferInstruction(TicketType.CONCERT, "QR_UNLOCK", steps, note);
    }
}
