package com.yourapp.ticketpass.ticket.web.dto;

import com.yourapp.ticketpass.ticket.domain.TicketType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

@Getter
@Setter
@NoArgsConstructor
public class CreateTicketRequest {

    @NotNull
    private TicketType ticketType;

    @NotBlank
    @Size(max = 200)
    private String title;

    @NotBlank
    @Size(max = 200)
    private String eventName;

    @Size(max = 255)
    private String venue;

    @Size(max = 120)
    private String seatInfo;

    @NotNull
    @Future
    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime eventStartAt;

    @NotNull(message = "Thời gian kết thúc sự kiện không được để trống.")
    @Future(message = "Thời gian kết thúc sự kiện phải nằm ở tương lai.")
    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime eventEndAt;

    @NotNull
    @DecimalMin("0")
    private BigDecimal originalPrice;

    @NotNull
    @DecimalMin("10000")
    private BigDecimal salePrice;

    @Size(max = 100)
    private String providerName;

    @Size(max = 120)
    private String providerOrderCode;

    private String ticketImageStorageKey;

    private String qrImageStorageKey;
}
