package com.yourapp.ticketpass.system.crossmodule;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.yourapp.ticketpass.reputation.application.UpdateReputationUseCase;
import com.yourapp.ticketpass.reputation.domain.UserReputationScore;
import com.yourapp.ticketpass.shared.event.CompletionReason;
import com.yourapp.ticketpass.shared.event.DisputeOutcome;
import com.yourapp.ticketpass.shared.event.DisputeResolvedEvent;
import com.yourapp.ticketpass.shared.event.TicketTransactionCompletedEvent;
import com.yourapp.ticketpass.system.base.AbstractSystemIntegrationTest;
import com.yourapp.ticketpass.user.domain.User;
import com.yourapp.ticketpass.user.domain.UserRole;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Hệ Thống - Hợp Đồng Chéo 1: Observer Pattern Qua Domain Events Giữa Payment/Dispute và Reputation")
class DomainEventObserverReputationContractTest extends AbstractSystemIntegrationTest {

    @Autowired
    private UpdateReputationUseCase updateReputationUseCase;

    @Test
    @DisplayName("Sự kiện TicketTransactionCompletedEvent: Cộng +2.00 điểm cho cả Buyer và Seller, tích lũy GMV người bán")
    void shouldHandleTicketTransactionCompletedEventCorrectly() {
        // Arrange
        User seller = createAndSaveUser("obs_seller@test.com", "Password123@", UserRole.USER);
        User buyer = createAndSaveUser("obs_buyer@test.com", "Password123@", UserRole.USER);

        UserReputationScore sRep = reputationRepository.findByUserId(seller.getId()).orElseThrow();
        sRep.setScore(new BigDecimal("80.00"));
        reputationRepository.save(sRep);

        UserReputationScore bRep = reputationRepository.findByUserId(buyer.getId()).orElseThrow();
        bRep.setScore(new BigDecimal("80.00"));
        reputationRepository.save(bRep);

        BigDecimal salePrice = new BigDecimal("1000000.00");
        TicketTransactionCompletedEvent event = new TicketTransactionCompletedEvent(
                101L, 201L, 301L,
                seller.getId(), buyer.getId(),
                salePrice,
                new BigDecimal("25000.00"),
                new BigDecimal("25000.00"),
                BigDecimal.ZERO,
                new BigDecimal("975000.00"),
                new BigDecimal("500000.00"),
                CompletionReason.AUTO_COMPLETE,
                Instant.now()
        );

        // Act: Kích hoạt xử lý qua Observer Use Case
        updateReputationUseCase.onTicketTransactionCompleted(event);

        // Assert: Cả 2 bên được cộng +2.00 điểm (80 -> 82), GMV người bán tăng đúng 1.000.000đ
        UserReputationScore updatedSeller = reputationRepository.findByUserId(seller.getId()).orElseThrow();
        assertThat(updatedSeller.getScore()).isEqualByComparingTo(new BigDecimal("82.00"));
        assertThat(updatedSeller.getTotalTransactions()).isEqualTo(1);
        assertThat(updatedSeller.getSuccessfulTransactions()).isEqualTo(1);
        assertThat(updatedSeller.getTotalGmv()).isEqualByComparingTo(salePrice);

        UserReputationScore updatedBuyer = reputationRepository.findByUserId(buyer.getId()).orElseThrow();
        assertThat(updatedBuyer.getScore()).isEqualByComparingTo(new BigDecimal("82.00"));
        assertThat(updatedBuyer.getTotalTransactions()).isEqualTo(1);
        assertThat(updatedBuyer.getSuccessfulTransactions()).isEqualTo(1);
    }

    @Test
    @DisplayName("Sự kiện DisputeResolvedEvent(BUYER_WINS): Phạt trừ Seller -15đ, Thưởng Buyer +1đ, bảo đảm không âm điểm")
    void shouldHandleDisputeBuyerWinsEvent() {
        // Arrange: Seller có 10.00 điểm (để test chặn sàn không âm dưới 0.00)
        User seller = createAndSaveUser("low_score_seller@test.com", "Password123@", UserRole.USER);
        User buyer = createAndSaveUser("happy_buyer2@test.com", "Password123@", UserRole.USER);

        UserReputationScore sRep = reputationRepository.findByUserId(seller.getId()).orElseThrow();
        sRep.setScore(new BigDecimal("10.00"));
        reputationRepository.save(sRep);

        UserReputationScore bRep = reputationRepository.findByUserId(buyer.getId()).orElseThrow();
        bRep.setScore(new BigDecimal("99.50"));
        reputationRepository.save(bRep);

        DisputeResolvedEvent event = new DisputeResolvedEvent(
                501L, 102L, 202L, 302L,
                buyer.getId(), seller.getId(),
                DisputeOutcome.BUYER_WINS,
                new BigDecimal("200000.00"),
                new BigDecimal("14000.00"),
                new BigDecimal("186000.00"),
                999L,
                Instant.now()
        );

        // Act
        updateReputationUseCase.onDisputeResolved(event);

        // Assert: Seller bị trừ -15đ nhưng bị chặn sàn tối thiểu 0.00; Buyer được cộng +1đ nhưng chặn trần 100.00
        UserReputationScore updatedSeller = reputationRepository.findByUserId(seller.getId()).orElseThrow();
        assertThat(updatedSeller.getScore()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(updatedSeller.getDisputesLost()).isEqualTo(1);

        UserReputationScore updatedBuyer = reputationRepository.findByUserId(buyer.getId()).orElseThrow();
        assertThat(updatedBuyer.getScore()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(updatedBuyer.getDisputesWon()).isEqualTo(1);
    }
}
