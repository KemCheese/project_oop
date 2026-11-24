package com.yourapp.ticketpass.user.application;

import com.yourapp.ticketpass.shared.exception.ResourceNotFoundException;
import com.yourapp.ticketpass.shared.storage.FileStorageService;
import com.yourapp.ticketpass.ticket.domain.TicketRepository;
import com.yourapp.ticketpass.user.domain.StarredTicket;
import com.yourapp.ticketpass.user.domain.StarredTicketRepository;
import com.yourapp.ticketpass.user.web.dto.StarredTicketItemResponse;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class StarredTicketUseCase {

    private final StarredTicketRepository starredTicketRepository;
    private final TicketRepository ticketRepository;
    private final FileStorageService fileStorageService;

    @Transactional
    public boolean toggleStar(Long userId, Long ticketId) {
        if (userId == null || ticketId == null) {
            throw new IllegalArgumentException("userId và ticketId không được null");
        }

        if (ticketRepository.findById(ticketId).isEmpty()) {
            throw new ResourceNotFoundException("Không tìm thấy vé với ID: " + ticketId);
        }

        if (starredTicketRepository.existsByUserIdAndTicketId(userId, ticketId)) {
            starredTicketRepository.deleteByUserIdAndTicketId(userId, ticketId);
            log.info("User {} da bo danh sao ve {}", userId, ticketId);
            return false;
        } else {
            StarredTicket starredTicket = new StarredTicket(userId, ticketId);
            starredTicketRepository.save(starredTicket);
            log.info("User {} da danh sao ve {}", userId, ticketId);
            return true;
        }
    }

    @Transactional(readOnly = true)
    public boolean isStarred(Long userId, Long ticketId) {
        if (userId == null || ticketId == null) {
            return false;
        }
        return starredTicketRepository.existsByUserIdAndTicketId(userId, ticketId);
    }

    @Transactional(readOnly = true)
    public Set<Long> getStarredTicketIds(Long userId) {
        if (userId == null) {
            return Collections.emptySet();
        }
        return starredTicketRepository.findStarredTicketIdsByUserId(userId);
    }

    @Transactional(readOnly = true)
    public List<StarredTicketItemResponse> getStarredTicketsForUser(Long userId) {
        if (userId == null) {
            return Collections.emptyList();
        }

        List<Long> ticketIds = starredTicketRepository.findTicketIdsByUserId(userId);
        if (ticketIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<StarredTicketItemResponse> result = new ArrayList<>();
        for (Long ticketId : ticketIds) {
            ticketRepository.findById(ticketId).ifPresent(ticket -> {
                String thumbnailUrl = null;
                if (ticket.getTicketImageStorageKey() != null && !ticket.getTicketImageStorageKey().isBlank()) {
                    thumbnailUrl = fileStorageService.buildPublicUrl(ticket.getTicketImageStorageKey());
                }

                result.add(new StarredTicketItemResponse(
                    ticket.getId(),
                    ticket.getTitle(),
                    ticket.getEventName(),
                    ticket.getVenue(),
                    ticket.getSeatInfo(),
                    ticket.getTicketType(),
                    ticket.getStatus(),
                    ticket.getSalePrice(),
                    ticket.getOriginalPrice(),
                    ticket.getEventStartAt(),
                    thumbnailUrl
                ));
            });
        }

        return result;
    }

    @Transactional(readOnly = true)
    public long countStarredTickets(Long userId) {
        if (userId == null) {
            return 0;
        }
        return starredTicketRepository.countByUserId(userId);
    }
}
