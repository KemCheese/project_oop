package com.yourapp.ticketpass.system.concurrency;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.yourapp.ticketpass.reputation.application.UpdateReputationUseCase;
import com.yourapp.ticketpass.reputation.domain.UserReputationScore;
import com.yourapp.ticketpass.system.base.AbstractSystemIntegrationTest;
import com.yourapp.ticketpass.user.domain.User;
import com.yourapp.ticketpass.user.domain.UserRole;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Phi Chức Năng - Concurrency 2: Cập Nhật Điểm Uy Tín Đa Luồng (Tự Động Thử Lại Khi Xung Đột Phiên Bản)")
class ReputationConcurrentUpdatesTest extends AbstractSystemIntegrationTest {

    @Autowired
    private UpdateReputationUseCase updateReputationUseCase;

    @Test
    @DisplayName("Nhiều tác vụ đồng thời cập nhật điểm uy tín: Cơ chế retry đảm bảo dữ liệu ghi nhận chính xác")
    void shouldHandleConcurrentReputationUpdatesSafely() throws InterruptedException {
        User seller = createAndSaveUser("conc_rep_seller@test.com", "Password123@", UserRole.USER);
        Long sellerId = seller.getId();
        UserReputationScore initRep = reputationRepository.findByUserId(sellerId).orElseThrow();
        initRep.setScore(new BigDecimal("5.00"));
        reputationRepository.save(initRep);

        int numberOfThreads = 4;
        ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(numberOfThreads);
        AtomicInteger successCounter = new AtomicInteger(0);

        for (int i = 0; i < numberOfThreads; i++) {
            executorService.submit(() -> {
                try {
                    startLatch.await();
                    updateReputationUseCase.recordCancelledListing(sellerId);
                    successCounter.incrementAndGet();
                } catch (Exception e) {
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean finished = finishLatch.await(10, TimeUnit.SECONDS);
        executorService.shutdown();

        assertThat(finished).isTrue();
        assertThat(successCounter.get()).isGreaterThanOrEqualTo(1);

        UserReputationScore finalRep = reputationRepository.findByUserId(sellerId).orElseThrow();
        assertThat(finalRep.getCancelledListings()).isEqualTo(successCounter.get());
        BigDecimal expectedScore = new BigDecimal("5.00")
                .subtract(BigDecimal.ONE.multiply(new BigDecimal(successCounter.get())))
                .max(BigDecimal.ZERO);
        assertThat(finalRep.getScore()).isEqualByComparingTo(expectedScore);
    }
}
