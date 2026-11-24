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

@Service
@RequiredArgsConstructor
public class ValidateTicketService {

    private final TicketRepository ticketRepository;

    private final List<TicketAdapter> ticketAdapters;

    private final Clock clock;

    @Value("${app.ticket.allow-markup:false}")
    private boolean allowMarkup;

    public ValidationResult validateForListing(Ticket ticket) {
        List<String> errors = new ArrayList<>();

        if (ticket.getSalePrice() == null || ticket.getSalePrice().compareTo(BigDecimal.ZERO) <= 0) {
            errors.add("Gia ban phai lon hon 0.");
        }

        if (!allowMarkup
                && ticket.getSalePrice() != null
                && ticket.getOriginalPrice() != null
                && ticket.getSalePrice().compareTo(ticket.getOriginalPrice()) > 0) {
            errors.add("Gia ban khong duoc vuot qua gia goc (quy tac chong dau co).");
        }

        Instant now = resolveNow();
        if (ticket.getEventStartAt() == null) {
            errors.add("Thoi gian su kien khong duoc de trong.");
        } else if (!ticket.getEventStartAt().isAfter(now)) {
            errors.add("Thoi gian su kien phai nam o tuong lai.");
        }

        if (ticket.getEventEndAt() == null) {
            errors.add("Thoi gian ket thuc su kien khong duoc de trong.");
        } else if (ticket.getEventStartAt() != null && !ticket.getEventEndAt().isAfter(ticket.getEventStartAt())) {
            errors.add("Thoi gian ket thuc su kien phai sau thoi gian bat dau.");
        }

        if (ticket.getTicketImageStorageKey() == null || ticket.getTicketImageStorageKey().isBlank()) {
            errors.add("Phai upload anh ve cong khai.");
        }

        if (ticket.getQrImageStorageKey() == null || ticket.getQrImageStorageKey().isBlank()) {
            errors.add("Phai upload anh QR an (anh check-in bi mat).");
        }

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

    public ValidationResult validateForPurchase(Long ticketId, Long buyerId) {
        List<String> errors = new ArrayList<>();

        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket", ticketId));

        if (ticket.getStatus() != TicketStatus.LISTED) {
            errors.add("Ve khong o trang thai co the mua (trang thai hien tai: " + ticket.getStatus() + ").");
        }

        if (ticket.getSellerId() != null && ticket.getSellerId().equals(buyerId)) {
            errors.add("Ban khong the mua ve cua chinh minh.");
        }

        if (ticket.getEventStartAt() != null && !ticket.getEventStartAt().isAfter(resolveNow())) {
            errors.add("Ve da qua thoi gian su kien, khong the mua.");
        }

        if (errors.isEmpty()) {
            return ValidationResult.ok();
        }
        return ValidationResult.fail(errors);
    }

    public ValidationResult validateForTransfer(Long ticketId, Long sellerId) {
        List<String> errors = new ArrayList<>();

        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket", ticketId));

        if (!ticket.getSellerId().equals(sellerId)) {
            errors.add("Ban khong phai la chu so huu cua ve nay.");
        }

        if (ticket.getStatus() != TicketStatus.DEPOSIT_LOCKED) {
            errors.add("Ve chua san sang de chuyen (trang thai hien tai: " + ticket.getStatus()
                    + ", can: DEPOSIT_LOCKED).");
        }

        if (ticket.getQrImageStorageKey() == null || ticket.getQrImageStorageKey().isBlank()) {
            errors.add("Ve chua co anh QR an. Vui long upload anh QR truoc khi chuyen ve.");
        }

        if (errors.isEmpty()) {
            return ValidationResult.ok();
        }
        return ValidationResult.fail(errors);
    }

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
