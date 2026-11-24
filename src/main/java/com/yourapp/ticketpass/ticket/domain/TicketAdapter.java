package com.yourapp.ticketpass.ticket.domain;

import java.time.Duration;

public interface TicketAdapter {

    TicketType supportedType();

    Duration disputeWindowAfterTransfer();

    Duration autoCompleteDelay();

    boolean requiresSeatInfo();

    boolean requiresEmailForwardProof();

    ValidationResult validateListing(Ticket ticket);

    Duration defaultEventDuration();

    TransferInstruction buildTransferInstruction(Ticket ticket);
}
