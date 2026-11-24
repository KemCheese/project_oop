package com.yourapp.ticketpass.user.infrastructure;

import com.yourapp.ticketpass.user.domain.User;
import com.yourapp.ticketpass.user.domain.UserRole;
import com.yourapp.ticketpass.user.domain.UserStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserJpaRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    List<User> findByStatus(UserStatus status);

    List<User> findByRole(UserRole role);

    @Query("SELECT u FROM User u " +
           "WHERE (:keyword IS NULL OR :keyword = '' " +
           "     OR LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "     OR LOWER(COALESCE(u.fullName, '')) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "     OR LOWER(COALESCE(u.phoneNumber, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "ORDER BY u.createdAt DESC, u.id DESC")
    List<User> searchAdminByKeyword(@Param("keyword") String keyword);

    @Query("SELECT u FROM User u " +
           "WHERE u.status = :status " +
           "AND (:keyword IS NULL OR :keyword = '' " +
           "     OR LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "     OR LOWER(COALESCE(u.fullName, '')) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "     OR LOWER(COALESCE(u.phoneNumber, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "ORDER BY u.createdAt DESC, u.id DESC")
    List<User> searchAdminByStatusAndKeyword(@Param("status") UserStatus status,
                                             @Param("keyword") String keyword);
}
