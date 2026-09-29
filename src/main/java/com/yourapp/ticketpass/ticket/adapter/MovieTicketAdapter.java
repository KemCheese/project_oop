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
 * Adapter cho ve xem phim tren san marketplace.
 *
 * <p>Truong bat buoc khi dang ve phim: ten phim (nhap chuan dinh dang de he thong tu phan loai),
 * ten rap (lua chon trong danh sach co san), gio chieu (nhap tung o theo dinh dang co san:
 * ngay/gio/phut), so luong ve, ghe (so luong ghe phai khop voi so luong ve), gia ban,
 * anh cong khai (de buyer xem truoc) va anh QR an (chi mo khoa sau escrow + coc).</p>
 *
 * <p>Cua so khieu nai DU KIEN: 2 gio sau khi ve duoc chuyen (anh QR an mo khoa), doc tu
 * cau hinh {@code app.dispute.window.movie-hours}, va luon phai dong truoc
 * {@code eventStartAt} vi ve phim thuong duoc su dung ngay.</p>
 *
 * <p>Module: ticket</p>
 *
 * Chu so huu: Nguoi A
 */
@Component
public class MovieTicketAdapter implements TicketAdapter {

    @Value("${app.dispute.window.movie-hours:2}")
    private int disputeWindowHours;

    @Override
    public TicketType supportedType() {
        return TicketType.MOVIE;
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
     * Ve phim bat buoc khai thong tin ghe (phong chieu va so ghe)
     * de nguoi mua doi chieu voi ve nhan duoc.
     * Xem BR-01.2 trong 04-quy-tac-nghiep-vu.md.
     */
    @Override
    public boolean requiresSeatInfo() {
        return true;
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

        // BR-01.2: Ve phim bat buoc co seatInfo (phong chieu + so ghe)
        if (ticket.getSeatInfo() == null || ticket.getSeatInfo().isBlank()) {
            errors.add("Ve phim bat buoc phai co thong tin ghe (phong chieu va so ghe).");
        }

        // Kiem tra eventName (ten phim) khong duoc rong
        if (ticket.getEventName() == null || ticket.getEventName().isBlank()) {
            errors.add("Ve phim bat buoc phai co ten phim.");
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
                "Su dung anh QR nay de check-in tai rap " + (ticket.getVenue() != null ? ticket.getVenue() : "") + ".",
                "Mang theo CCCD/CMND de doi chieu neu rap yeu cau."
        );

        String note = "Han su dung: truoc gio chieu ghi tren ve. "
                + "Neu gap van de, ban co " + disputeWindowHours + " gio sau khi nhan ve de mo khieu nai.";

        return new TransferInstruction(TicketType.MOVIE, "QR_UNLOCK", steps, note);
    }
}
