package com.yourapp.ticketpass.system.crossmodule;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.yourapp.ticketpass.system.base.AbstractSystemIntegrationTest;
import com.yourapp.ticketpass.ticket.domain.Ticket;
import com.yourapp.ticketpass.ticket.domain.TicketStatus;
import com.yourapp.ticketpass.ticket.domain.TicketType;
import com.yourapp.ticketpass.user.application.StarredTicketUseCase;
import com.yourapp.ticketpass.user.domain.User;
import com.yourapp.ticketpass.user.domain.UserRole;
import com.yourapp.ticketpass.user.web.dto.StarredTicketItemResponse;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Hệ Thống - Hợp Đồng Chéo 2: Data Stitching Hồ Sơ Người Dùng & Watchlist Cá Nhân (Zero JPA Cross-Module)")
class UserReputationWatchlistStitchingTest extends AbstractSystemIntegrationTest {

    @Autowired
    private StarredTicketUseCase starredTicketUseCase;

    @Test
    @DisplayName("Data Stitching: Ghép nối User + Điểm uy tín + Watchlist theo dõi vé qua ID thuần mà không dùng JPA join")
    void shouldStitchUserProfileAndWatchlistCorrectlyWithoutCrossModuleJpaRelations() {
        User seller = createAndSaveUser("stitch_seller@test.com", "Password123@", UserRole.USER);
        User buyer = createAndSaveUser("stitch_buyer@test.com", "Password123@", UserRole.USER);

        Ticket ticket1 = createAndSaveTicket(seller.getId(), "Vé Concert BlackPink In Hanoi", TicketType.CONCERT, new BigDecimal("1200000.00"), TicketStatus.LISTED);
        Ticket ticket2 = createAndSaveTicket(seller.getId(), "Vé Xem Phim Oppenheimer", TicketType.MOVIE, new BigDecimal("150000.00"), TicketStatus.LISTED);

        boolean star1 = starredTicketUseCase.toggleStar(buyer.getId(), ticket1.getId());
        boolean star2 = starredTicketUseCase.toggleStar(buyer.getId(), ticket2.getId());
        assertThat(star1).isTrue();
        assertThat(star2).isTrue();

        List<Long> starredIds = starredTicketRepository.findTicketIdsByUserId(buyer.getId());
        assertThat(starredIds).hasSize(2);
        assertThat(starredIds).contains(ticket1.getId(), ticket2.getId());

        List<StarredTicketItemResponse> items = starredTicketUseCase.getStarredTicketsForUser(buyer.getId());
        assertThat(items).hasSize(2);
        assertThat(items).extracting(StarredTicketItemResponse::title)
                .containsExactlyInAnyOrder("Vé Concert BlackPink In Hanoi", "Vé Xem Phim Oppenheimer");

        boolean unstar1 = starredTicketUseCase.toggleStar(buyer.getId(), ticket1.getId());
        assertThat(unstar1).isFalse();

        List<StarredTicketItemResponse> itemsAfterUnstar = starredTicketUseCase.getStarredTicketsForUser(buyer.getId());
        assertThat(itemsAfterUnstar).hasSize(1);
        assertThat(itemsAfterUnstar.get(0).title()).isEqualTo("Vé Xem Phim Oppenheimer");
    }
}
