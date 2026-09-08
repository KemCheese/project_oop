package com.yourapp.ticketpass.ticket.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DTO request: nguoi mua bam mua mot ve dang duoc rao ban.
 *
 * <p>Thuoc tang {@code web} mong, KHONG chua entity. Id nguoi mua khong nam trong DTO ma duoc lay
 * tu phien dang nhap o controller de tranh gia mao.</p>
 *
 * <p>Module: ticket</p>
 *
 * Chu so huu: Nguoi A
 */
@Getter
@Setter
@NoArgsConstructor
public class PurchaseTicketRequest {

    @NotNull
    private Long ticketId;

    /** Phuong thuc thanh toan, do module payment dinh nghia gia tri hop le. */
    @NotBlank
    private String paymentMethod;

    /** Ghi chu tuy chon nguoi mua gui kem cho nguoi ban. */
    private String note;
}
