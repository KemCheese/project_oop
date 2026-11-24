package com.yourapp.ticketpass.system.crossmodule;

import static org.assertj.core.api.Assertions.assertThat;

import com.yourapp.ticketpass.reputation.application.UpdateReputationUseCase;
import com.yourapp.ticketpass.reputation.domain.UserReputationScore;
import com.yourapp.ticketpass.shared.event.CompletionReason;
import com.yourapp.ticketpass.shared.event.DisputeOutcome;
import com.yourapp.ticketpass.shared.event.DisputeResolvedEvent;
import com.yourapp.ticketpass.shared.event.TicketTransactionCompletedEvent;
import com.yourapp.ticketpass.system.base.AbstractSystemIntegrationTest;
import com.yourapp.ticketpass.user.domain.User;
import com.yourapp.ticketpass.user.domain.UserRole;
import com.yourapp.ticketpass.user.domain.UserStatus;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("Hệ Thống - Hợp Đồng Chéo 1: Observer Pattern Qua Domain Events Giữa Payment/Dispute và Reputation (Thang 0-5)")
class DomainEventObserverReputationContractTest extends AbstractSystemIntegrationTest {

    @Autowired
    private UpdateReputationUseCase updateReputationUseCase;

    @Test
    @DisplayName("Sự kiện TicketTransactionCompletedEvent: Cộng +1 điểm cho cả Buyer và Seller, tích lũy GMV")
    void shouldHandleTicketTransactionCompletedEventCorrectly() {
        User seller = createAndSaveUser("obs_seller@test.com", "Password123@", UserRole.USER);
        User buyer = createAndSaveUser("obs_buyer@test.com", "Password123@", UserRole.USER);

        UserReputationScore sRep = reputationRepository.findByUserId(seller.getId()).orElseThrow();
        sRep.setScore(new BigDecimal("3.00"));
        reputationRepository.save(sRep);

        UserReputationScore bRep = reputationRepository.findByUserId(buyer.getId()).orElseThrow();
        bRep.setScore(new BigDecimal("3.00"));
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

        updateReputationUseCase.onTicketTransactionCompleted(event);

        UserReputationScore updatedSeller = reputationRepository.findByUserId(seller.getId()).orElseThrow();
        assertThat(updatedSeller.getScore()).isEqualByComparingTo(new BigDecimal("4.00"));
        assertThat(updatedSeller.getTotalTransactions()).isEqualTo(1);
        assertThat(updatedSeller.getSuccessfulTransactions()).isEqualTo(1);
        assertThat(updatedSeller.getTotalGmv()).isEqualByComparingTo(salePrice);

        UserReputationScore updatedBuyer = reputationRepository.findByUserId(buyer.getId()).orElseThrow();
        assertThat(updatedBuyer.getScore()).isEqualByComparingTo(new BigDecimal("4.00"));
        assertThat(updatedBuyer.getTotalTransactions()).isEqualTo(1);
        assertThat(updatedBuyer.getSuccessfulTransactions()).isEqualTo(1);
    }

    @Test
    @DisplayName("Sự kiện DisputeResolvedEvent(BUYER_WINS): Phạt Seller -2đ (sàn 0 & auto-ban vĩnh viễn), Buyer giữ nguyên điểm (+0)")
    void shouldHandleDisputeBuyerWinsEvent() {
        User seller = createAndSaveUser("low_score_seller@test.com", "Password123@", UserRole.USER);
        User buyer = createAndSaveUser("happy_buyer2@test.com", "Password123@", UserRole.USER);

        UserReputationScore sRep = reputationRepository.findByUserId(seller.getId()).orElseThrow();
        sRep.setScore(new BigDecimal("1.00"));
        reputationRepository.save(sRep);

        UserReputationScore bRep = reputationRepository.findByUserId(buyer.getId()).orElseThrow();
        bRep.setScore(new BigDecimal("5.00"));
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

        updateReputationUseCase.onDisputeResolved(event);

        UserReputationScore updatedSeller = reputationRepository.findByUserId(seller.getId()).orElseThrow();
        assertThat(updatedSeller.getScore()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(updatedSeller.getDisputesLost()).isEqualTo(1);

        User bannedSeller = userRepository.findById(seller.getId()).orElseThrow();
        assertThat(bannedSeller.getStatus()).isEqualTo(UserStatus.BANNED);
        assertThat(bannedSeller.isPermanentlyBanned()).isTrue();

        UserReputationScore updatedBuyer = reputationRepository.findByUserId(buyer.getId()).orElseThrow();
        assertThat(updatedBuyer.getScore()).isEqualByComparingTo(new BigDecimal("5.00"));
        assertThat(updatedBuyer.getDisputesWon()).isEqualTo(1);
    }
}
