package com.yourapp.ticketpass.ticket.domain;

public enum TicketStatus {

    LISTED,

    PAYMENT_ESCROWED,

    DEPOSIT_LOCKED,

    TRANSFERRED,

    AWAITING_USE,

    AUTO_COMPLETE,

    DISPUTE_UNDER_REVIEW,

    RESOLVED_BUYER_WINS,

    RESOLVED_SELLER_WINS,

    CANCELLED
}
