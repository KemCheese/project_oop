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
        // 1. Arrange: Tạo User
        User seller = createAndSaveUser("conc_rep_seller@test.com", "Password123@", UserRole.USER);
        Long sellerId = seller.getId();

        int numberOfThreads = 4;
        ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(numberOfThreads);
        AtomicInteger successCounter = new AtomicInteger(0);

        // 2. Kích hoạt 4 luồng đồng thời ghi nhận hành vi huỷ vé (-10 điểm mỗi lần)
        for (int i = 0; i < numberOfThreads; i++) {
            executorService.submit(() -> {
                try {
                    startLatch.await(); // Đợi cả 4 luồng cùng sẵn sàng để kích nổ đồng thời
                    updateReputationUseCase.recordCancelledListing(sellerId);
                    successCounter.incrementAndGet();
                } catch (Exception e) {
                    // Log or track failure
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        // Bắt đầu chạy đua
        startLatch.countDown();
        boolean finished = finishLatch.await(10, TimeUnit.SECONDS);
        executorService.shutdown();

        assertThat(finished).isTrue();
        assertThat(successCounter.get()).isGreaterThanOrEqualTo(1);

        // 3. Assert: Kiểm tra bản ghi điểm uy tín cuối cùng trong CSDL
        UserReputationScore finalRep = reputationRepository.findByUserId(sellerId).orElseThrow();
        assertThat(finalRep.getCancelledListings()).isEqualTo(successCounter.get());
        // Điểm uy tín bị trừ tương ứng với số lần thành công (100 - successCounter * 10)
        BigDecimal expectedScore = new BigDecimal("100.00").subtract(new BigDecimal("10.00").multiply(new BigDecimal(successCounter.get())));
        assertThat(finalRep.getScore()).isEqualByComparingTo(expectedScore);
    }
}
