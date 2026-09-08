package com.yourapp.ticketpass.ticket.web.dto;

import com.yourapp.ticketpass.ticket.domain.TicketType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DTO request: du lieu nguoi ban nhap o form dang ve.
 *
 * <p>Thuoc tang {@code web} mong, chi mang du lieu tho tu form sang service; KHONG chua entity
 * va khong duoc dung truc tiep lam tham so JPA. Cac rang buoc Bean Validation o day chi kiem tra
 * dinh dang co ban, con luat rieng theo tung loai ve do
 * {@code com.yourapp.ticketpass.ticket.domain.TicketAdapter#validateListing} dam nhiem.</p>
 *
 * <p>Module: ticket</p>
 *
 * Chu so huu: Nguoi A
 */
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
    private Instant eventStartAt;

    @NotNull
    @DecimalMin("0")
    private BigDecimal originalPrice;

    @NotNull
    @DecimalMin("1000")
    private BigDecimal salePrice;

    @Size(max = 100)
    private String providerName;

    @Size(max = 120)
    private String providerOrderCode;

    /** Khoa file anh ve da duoc upload truoc do qua FileStorageService. */
    private String ticketImageStorageKey;

    /** Khoa file anh QR da duoc upload truoc do qua FileStorageService. */
    private String qrImageStorageKey;

    /** Noi dung tho cua email forward tu don vi phat hanh, de module verification bo tach. */
    private String rawEmailForwardSource;
}
