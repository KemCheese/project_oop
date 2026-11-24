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
public class ConcertTicketAdapter implements TicketAdapter {

    @Value("${app.dispute.window.concert-hours:24}")
    private int disputeWindowHours;

    @Value("${app.ticket.default-duration.concert-minutes:180}")
    private long defaultDurationMinutes = 180L;

    @Override
    public TicketType supportedType() {
        return TicketType.CONCERT;
    }

    @Override
    public Duration defaultEventDuration() {
        long minutes = defaultDurationMinutes > 0 ? defaultDurationMinutes : 180L;
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
        return false;
    }

    @Override
    public boolean requiresEmailForwardProof() {
        return false;
    }

    @Override
    public ValidationResult validateListing(Ticket ticket) {
        List<String> errors = new ArrayList<>();

        if (ticket.getEventName() == null || ticket.getEventName().isBlank()) {
            errors.add("Ve concert bat buoc phai co ten su kien.");
        }

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
