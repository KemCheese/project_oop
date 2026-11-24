package com.yourapp.ticketpass.user.infrastructure;

import com.yourapp.ticketpass.user.domain.StarredTicket;
import com.yourapp.ticketpass.user.domain.StarredTicketRepository;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class StarredTicketRepositoryImpl implements StarredTicketRepository {

    private final StarredTicketJpaRepository jpaRepository;

    @Override
    public StarredTicket save(StarredTicket starredTicket) {
        return jpaRepository.save(starredTicket);
    }

    @Override
    public void deleteByUserIdAndTicketId(Long userId, Long ticketId) {
        jpaRepository.deleteByUserIdAndTicketId(userId, ticketId);
    }

    @Override
    public boolean existsByUserIdAndTicketId(Long userId, Long ticketId) {
        return jpaRepository.existsByUserIdAndTicketId(userId, ticketId);
    }

    @Override
    public Set<Long> findStarredTicketIdsByUserId(Long userId) {
        return jpaRepository.findStarredTicketIdsByUserId(userId);
    }

    @Override
    public List<Long> findTicketIdsByUserId(Long userId) {
        return jpaRepository.findTicketIdsByUserId(userId);
    }

    @Override
    public long countByUserId(Long userId) {
        return jpaRepository.countByUserId(userId);
    }

    @Override
    public List<Long> findUserIdsByTicketId(Long ticketId) {
        if (ticketId == null) {
            return java.util.Collections.emptyList();
        }
        return jpaRepository.findUserIdsByTicketId(ticketId);
    }
}
