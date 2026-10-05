package com.yourapp.ticketpass.user.infrastructure;

import com.yourapp.ticketpass.user.domain.StarredTicket;
import com.yourapp.ticketpass.user.domain.StarredTicketRepository;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * Hiện thực port {@link StarredTicketRepository} bằng Spring Data JPA.
 *
 * <p>Adapter delegating trực tiếp sang {@link StarredTicketJpaRepository}.</p>
 *
 * <p>Module: user</p>
 * <p>Chủ sở hữu: Người C</p>
 */
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
}
