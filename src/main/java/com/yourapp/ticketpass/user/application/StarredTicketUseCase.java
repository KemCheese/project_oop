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

/**
 * Use case điều phối nghiệp vụ Đánh sao vé (Watchlist cá nhân) của người dùng.
 *
 * <p>Theo kiến trúc Modular Monolith và Nguyên tắc Scalability số 1:
 * - Module user quản lý quan hệ sở hữu đánh sao.
 * - Khi cần thông tin vé để hiển thị, gọi qua port {@link TicketRepository} thuộc domain ticket
 *   chứ không dùng JPA relation xuyên module.</p>
 *
 * <p>Module: user</p>
 * <p>Chủ sở hữu: Người C</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StarredTicketUseCase {

    private final StarredTicketRepository starredTicketRepository;
    private final TicketRepository ticketRepository;
    private final FileStorageService fileStorageService;

    /**
     * Bật/tắt trạng thái đánh dấu sao một vé của người dùng.
     *
     * @param userId ID của người dùng
     * @param ticketId ID của vé cần đánh sao
     * @return {@code true} nếu vé vừa được thêm vào danh sách sao; {@code false} nếu vừa được hủy sao
     * @throws ResourceNotFoundException nếu vé không tồn tại trên hệ thống
     */
    @Transactional
    public boolean toggleStar(Long userId, Long ticketId) {
        if (userId == null || ticketId == null) {
            throw new IllegalArgumentException("userId và ticketId không được null");
        }

        // Kiểm tra vé có tồn tại hay không
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

    /**
     * Kiểm tra xem người dùng đã đánh dấu sao vé này chưa.
     */
    @Transactional(readOnly = true)
    public boolean isStarred(Long userId, Long ticketId) {
        if (userId == null || ticketId == null) {
            return false;
        }
        return starredTicketRepository.existsByUserIdAndTicketId(userId, ticketId);
    }

    /**
     * Lấy toàn bộ Set ticketId mà người dùng đã đánh sao (phục vụ map active trên UI sàn vé).
     */
    @Transactional(readOnly = true)
    public Set<Long> getStarredTicketIds(Long userId) {
        if (userId == null) {
            return Collections.emptySet();
        }
        return starredTicketRepository.findStarredTicketIdsByUserId(userId);
    }

    /**
     * Lấy danh sách chi tiết các vé mà người dùng đã đánh dấu sao để hiển thị trong hồ sơ cá nhân.
     * Thứ tự được bảo toàn theo thời điểm đánh sao mới nhất.
     */
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

    /**
     * Đếm tổng số vé mà người dùng đã đánh sao.
     */
    @Transactional(readOnly = true)
    public long countStarredTickets(Long userId) {
        if (userId == null) {
            return 0;
        }
        return starredTicketRepository.countByUserId(userId);
    }
}
