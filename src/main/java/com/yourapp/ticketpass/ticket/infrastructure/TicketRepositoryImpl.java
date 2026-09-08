package com.yourapp.ticketpass.ticket.infrastructure;

import com.yourapp.ticketpass.ticket.domain.Ticket;
import com.yourapp.ticketpass.ticket.domain.TicketRepository;
import com.yourapp.ticketpass.ticket.domain.TicketStatus;
import com.yourapp.ticketpass.ticket.domain.TicketType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * Hien thuc port {@link TicketRepository} bang Spring Data JPA.
 *
 * <p>Day la glue code thuan: moi method chi delegate sang {@link TicketJpaRepository}. Nho lop nay,
 * tang domain va application khong biet gi ve Spring Data, va co the thay bang hien thuc in-memory
 * khi viet unit test.</p>
 *
 * <p>Module: ticket</p>
 *
 * Chu so huu: Nguoi A
 */
@Repository
@RequiredArgsConstructor
public class TicketRepositoryImpl implements TicketRepository {

    private final TicketJpaRepository ticketJpaRepository;

    @Override
    public Ticket save(Ticket ticket) {
        return ticketJpaRepository.save(ticket);
    }

    @Override
    public Optional<Ticket> findById(Long id) {
        return ticketJpaRepository.findById(id);
    }

    @Override
    public List<Ticket> findBySellerId(Long sellerId) {
        return ticketJpaRepository.findBySellerId(sellerId);
    }

    @Override
    public List<Ticket> findByBuyerId(Long buyerId) {
        return ticketJpaRepository.findByBuyerId(buyerId);
    }

    @Override
    public List<Ticket> findByStatus(TicketStatus status) {
        return ticketJpaRepository.findByStatus(status);
    }

    @Override
    public List<Ticket> findListedByType(TicketType ticketType) {
        return ticketJpaRepository.findByTicketTypeAndStatus(ticketType, TicketStatus.LISTED);
    }

    @Override
    public Optional<Ticket> findByQrPayloadHash(String qrPayloadHash) {
        return ticketJpaRepository.findByQrPayloadHash(qrPayloadHash);
    }

    @Override
    public boolean existsByQrPayloadHash(String qrPayloadHash) {
        return ticketJpaRepository.existsByQrPayloadHash(qrPayloadHash);
    }

    @Override
    public List<Ticket> findDueForAutoComplete(TicketStatus status, Instant before) {
        return ticketJpaRepository.findByStatusAndDisputeWindowClosesAtBefore(status, before);
    }

    @Override
    public void deleteById(Long id) {
        ticketJpaRepository.deleteById(id);
    }
}
