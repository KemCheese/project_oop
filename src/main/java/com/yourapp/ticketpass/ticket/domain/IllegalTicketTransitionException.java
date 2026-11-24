package com.yourapp.ticketpass.ticket.domain;

public class IllegalTicketTransitionException extends RuntimeException {

    public IllegalTicketTransitionException(TicketStatus from, TicketStatus to) {
        super("Chuyen trang thai khong hop le: " + from + " -> " + to);
    }
}
