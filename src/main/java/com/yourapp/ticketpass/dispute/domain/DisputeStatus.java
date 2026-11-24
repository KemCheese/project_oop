package com.yourapp.ticketpass.dispute.domain;

public enum DisputeStatus {

    OPEN,

    AWAITING_SELLER_RESPONSE,

    SELLER_RESPONDED,

    UNDER_REVIEW,

    RESOLVED_BUYER_WINS,

    RESOLVED_SELLER_WINS,

    WITHDRAWN,

    EXPIRED
}
