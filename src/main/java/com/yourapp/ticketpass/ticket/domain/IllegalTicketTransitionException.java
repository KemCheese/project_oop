package com.yourapp.ticketpass.ticket.domain;

/**
 * Nem ra khi co yeu cau chuyen trang thai ve khong hop le theo state machine
 * mo ta trong {@link TicketStatus}.
 *
 * <p>Duoc {@code com.yourapp.ticketpass.ticket.application.TicketStateMachineService} su dung
 * de chan cac buoc nhay sai, vi du chuyen thang tu {@link TicketStatus#LISTED} sang
 * {@link TicketStatus#TRANSFERRED} ma bo qua buoc giu tien escrow.</p>
 *
 * <p>Module: ticket</p>
 *
 * Chu so huu: Nguoi A
 */
public class IllegalTicketTransitionException extends RuntimeException {

    /**
     * @param from trang thai hien tai cua ve
     * @param to trang thai dich bi tu choi
     */
    public IllegalTicketTransitionException(TicketStatus from, TicketStatus to) {
        super("Chuyen trang thai khong hop le: " + from + " -> " + to);
    }
}
