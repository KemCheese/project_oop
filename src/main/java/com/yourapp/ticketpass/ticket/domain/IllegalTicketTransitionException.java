package com.yourapp.ticketpass.ticket.domain;

import com.yourapp.ticketpass.shared.exception.BusinessRuleViolationException;

public class IllegalTicketTransitionException extends BusinessRuleViolationException {

    public IllegalTicketTransitionException(TicketStatus from, TicketStatus to) {
        super("Chuyen trang thai khong hop le: " + from + " -> " + to);
    }
}
