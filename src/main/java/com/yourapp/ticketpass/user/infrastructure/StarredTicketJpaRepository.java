package com.yourapp.ticketpass.user.infrastructure;

import com.yourapp.ticketpass.user.domain.StarredTicket;
import java.util.List;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repository Spring Data JPA cho bảng {@code user_starred_tickets}.
 *
 * <p>Chi tiết hạ tầng kỹ thuật, chỉ được {@link StarredTicketRepositoryImpl} sử dụng.</p>
 *
 * <p>Module: user</p>
 * <p>Chủ sở hữu: Người C</p>
 */
public interface StarredTicketJpaRepository extends JpaRepository<StarredTicket, Long> {

    boolean existsByUserIdAndTicketId(Long userId, Long ticketId);

    @Modifying
    @Query("DELETE FROM StarredTicket s WHERE s.userId = :userId AND s.ticketId = :ticketId")
    void deleteByUserIdAndTicketId(@Param("userId") Long userId, @Param("ticketId") Long ticketId);

    @Query("SELECT s.ticketId FROM StarredTicket s WHERE s.userId = :userId")
    Set<Long> findStarredTicketIdsByUserId(@Param("userId") Long userId);

    @Query("SELECT s.ticketId FROM StarredTicket s WHERE s.userId = :userId ORDER BY s.createdAt DESC")
    List<Long> findTicketIdsByUserId(@Param("userId") Long userId);

    long countByUserId(Long userId);
}
