package com.yourapp.ticketpass.ticket.domain;

public record TicketTypeStatusCount(TicketType ticketType, TicketStatus status, long count) {
}
