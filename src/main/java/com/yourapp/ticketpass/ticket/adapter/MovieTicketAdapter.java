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
public class MovieTicketAdapter implements TicketAdapter {

    @Value("${app.dispute.window.movie-hours:2}")
    private int disputeWindowHours;

    @Value("${app.ticket.default-duration.movie-minutes:120}")
    private long defaultDurationMinutes = 120L;

    @Override
    public TicketType supportedType() {
        return TicketType.MOVIE;
    }

    @Override
    public Duration defaultEventDuration() {
        long minutes = defaultDurationMinutes > 0 ? defaultDurationMinutes : 120L;
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
    public ValidationResult validateListing(Ticket ticket) {
        List<String> errors = new ArrayList<>();

        if (ticket.getSeatInfo() == null || ticket.getSeatInfo().isBlank()) {
            errors.add("Ve phim bat buoc phai co thong tin ghe (phong chieu va so ghe).");
        }

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
