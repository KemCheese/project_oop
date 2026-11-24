package com.yourapp.ticketpass.user.domain;

import java.util.List;
import java.util.Set;

public interface StarredTicketRepository {

    StarredTicket save(StarredTicket starredTicket);

    void deleteByUserIdAndTicketId(Long userId, Long ticketId);

    boolean existsByUserIdAndTicketId(Long userId, Long ticketId);

    Set<Long> findStarredTicketIdsByUserId(Long userId);

    List<Long> findTicketIdsByUserId(Long userId);

    long countByUserId(Long userId);

    List<Long> findUserIdsByTicketId(Long ticketId);
}
