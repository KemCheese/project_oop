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
        return disputeWindowAfterTransfer().plusHours(1);
    }

    @Override
    public boolean requiresSeatInfo() {
        return true;
    }

    @Override
    public boolean requiresEmailForwardProof() {
        return false;
    }

    @Override
    public ValidationResult validateListing(Ticket ticket) {
        List<String> errors = new ArrayList<>();

        if (ticket.getSeatInfo() == null || ticket.getSeatInfo().isBlank()) {
            errors.add("Ve xe khach bat buoc phai co thong tin ghe (so ghe hoac so giuong).");
        }

        if (ticket.getEventName() == null || ticket.getEventName().isBlank()) {
            errors.add("Ve xe khach bat buoc phai co tuyen di (vi du: Sai Gon - Da Lat).");
        }

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
