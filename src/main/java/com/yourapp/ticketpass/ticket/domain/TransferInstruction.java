package com.yourapp.ticketpass.ticket.domain;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TransferInstruction {

    private TicketType ticketType;

    private String channel;

    private List<String> steps;

    private String note;
}
