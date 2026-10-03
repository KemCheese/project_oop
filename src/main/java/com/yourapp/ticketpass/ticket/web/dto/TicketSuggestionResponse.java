package com.yourapp.ticketpass.ticket.web.dto;

import com.yourapp.ticketpass.ticket.domain.TicketType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DTO phuc vu menu goi y tim kiem nhanh (live autocomplete).
 *
 * <p>Cung cap du lieu gon nhe cho client render danh sach 5 goi y hang dau.</p>
 *
 * <p>Module: ticket</p>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TicketSuggestionResponse {

    private Long id;

    private String title;

    private String eventName;

    private TicketType ticketType;

    private String ticketTypeLabel;

    private String formattedPrice;

    private String thumbnailUrl;

    private String detailUrl;

    private String venue;
}
