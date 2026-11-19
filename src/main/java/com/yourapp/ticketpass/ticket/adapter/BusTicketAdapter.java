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
 * Adapter cho ve xe khach cua cac nha xe tren san marketplace.
 *
 * <p>LUU Y: Day la ve xe khach duong dai cua cac nha xe (vi du Phuong Trang, Thanh Buoi),
 * KHONG phai ve xe buyt noi do.</p>
 *
 * <p>Truong bat buoc khi dang ve xe khach: hang xe (lua chon trong danh sach co san),
 * diem xuat phat, diem den, gio chay, so luong cho, ten ghe (so luong cho phai khop
 * voi so ghe nhap vao), gia ban, anh cong khai (de buyer xem truoc) va anh QR an
 * (chi mo khoa sau escrow + coc).</p>
 *
 * <p>Cua so khieu nai DU KIEN: 3 gio sau khi ve duoc chuyen (anh QR an mo khoa), doc tu
 * cau hinh {@code app.dispute.window.bus-hours}, va luon phai dong truoc
 * {@code eventStartAt} (gio xe xuat ben).</p>
 *
 * <p>Module: ticket</p>
 *
 * Chu so huu: Nguoi A
 */
@Component
public class BusTicketAdapter implements TicketAdapter {

    @Value("${app.dispute.window.bus-hours:3}")
    private int disputeWindowHours;

    @Value("${app.ticket.default-duration.bus-minutes:240}")
    private long defaultDurationMinutes = 240L;

    @Override
    public TicketType supportedType() {
        return TicketType.BUS;
    }

    @Override
    public Duration defaultEventDuration() {
        long minutes = defaultDurationMinutes > 0 ? defaultDurationMinutes : 240L;
        return Duration.ofMinutes(minutes);
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
     * Ve xe khach bat buoc khai thong tin ghe (so ghe / so giuong)
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

        // BR-01.2: Ve xe khach bat buoc co seatInfo (so ghe / so giuong)
        if (ticket.getSeatInfo() == null || ticket.getSeatInfo().isBlank()) {
            errors.add("Ve xe khach bat buoc phai co thong tin ghe (so ghe hoac so giuong).");
        }

        // Kiem tra eventName (tuyen di, vi du "Sai Gon - Da Lat") khong duoc rong
        if (ticket.getEventName() == null || ticket.getEventName().isBlank()) {
            errors.add("Ve xe khach bat buoc phai co tuyen di (vi du: Sai Gon - Da Lat).");
        }

        // Kiem tra venue (diem don khach) khong duoc rong
        if (ticket.getVenue() == null || ticket.getVenue().isBlank()) {
            errors.add("Ve xe khach bat buoc phai co diem don khach.");
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
                "Su dung anh QR nay de len xe tai " + (ticket.getVenue() != null ? ticket.getVenue() : "diem don") + ".",
                "Mang theo CCCD/CMND de doi chieu voi thong tin hanh khach tren ve."
        );

        String note = "Han su dung: truoc gio xe xuat ben ghi tren ve. "
                + "Nen co mat truoc gio xuat ben it nhat 15 phut. "
                + "Neu gap van de, ban co " + disputeWindowHours + " gio sau khi nhan ve de mo khieu nai.";

        return new TransferInstruction(TicketType.BUS, "QR_UNLOCK", steps, note);
    }
}
