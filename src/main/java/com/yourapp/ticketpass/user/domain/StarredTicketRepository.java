package com.yourapp.ticketpass.user.domain;

import java.util.List;
import java.util.Set;

/**
 * Port lưu trữ của tính năng Đánh sao vé, khai báo tại tầng Domain.
 *
 * <p>Theo nguyên tắc Dependency Inversion (Hexagonal Architecture), tầng domain và application
 * chỉ phụ thuộc vào interface này mà không phụ thuộc Spring Data JPA.</p>
 *
 * <p>Module: user</p>
 * <p>Chủ sở hữu: Người C</p>
 */
public interface StarredTicketRepository {

    /**
     * Lưu mới bản ghi đánh sao vé.
     */
    StarredTicket save(StarredTicket starredTicket);

    /**
     * Xóa đánh sao của một vé theo userId và ticketId.
     */
    void deleteByUserIdAndTicketId(Long userId, Long ticketId);

    /**
     * Kiểm tra xem user đã đánh sao vé này chưa.
     */
    boolean existsByUserIdAndTicketId(Long userId, Long ticketId);

    /**
     * Lấy toàn bộ Set ticketId mà user đã sao (phục vụ map icon active trên UI).
     */
    Set<Long> findStarredTicketIdsByUserId(Long userId);

    /**
     * Lấy danh sách ticketId mà user đã sao, sắp xếp theo thời điểm mới nhất.
     */
    List<Long> findTicketIdsByUserId(Long userId);

    /**
     * Đếm tổng số lượng vé mà user đã đánh sao.
     */
    long countByUserId(Long userId);
}
