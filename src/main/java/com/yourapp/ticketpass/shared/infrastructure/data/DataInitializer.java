package com.yourapp.ticketpass.shared.infrastructure.data;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.yourapp.ticketpass.reputation.domain.ReputationRepository;
import com.yourapp.ticketpass.reputation.domain.UserReputationScore;
import com.yourapp.ticketpass.ticket.domain.Ticket;
import com.yourapp.ticketpass.ticket.domain.TicketRepository;
import com.yourapp.ticketpass.ticket.domain.TicketStatus;
import com.yourapp.ticketpass.ticket.domain.TicketType;
import com.yourapp.ticketpass.user.domain.User;
import com.yourapp.ticketpass.user.domain.UserRepository;
import com.yourapp.ticketpass.user.domain.UserRole;
import com.yourapp.ticketpass.user.domain.UserStatus;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Component khởi tạo dữ liệu mẫu (Data Seeding) cho toàn bộ hệ thống TicketPass.
 *
 * <p><b>Mục đích:</b> Tự động nạp các tài khoản người dùng chuẩn (Admin, Seller, Buyer,
 * VIP Seller), điểm uy tín tương ứng và các vé mẫu ở trạng thái {@link TicketStatus#LISTED}
 * ngay khi ứng dụng khởi động. Điều này giúp nhóm phát triển, tester và người chấm đồ án
 * có thể đăng nhập và trải nghiệm ngay sàn giao dịch mà không phải tạo thủ công qua form
 * hoặc can thiệp lệnh SQL vào database.</p>
 *
 * <p><b>Nguyên tắc bảo vệ kiến trúc:</b></p>
 * <ul>
 *   <li>{@code @Profile("!prod")}: Chỉ hoạt động trên các môi trường phát triển, cục bộ và kiểm thử
 *       (local, dev, test). Tuyệt đối không chạy trên môi trường production thực tế.</li>
 *   <li><b>Tính Idempotent:</b> Kiểm tra {@code userRepository.existsByEmail("admin@test.com")}. Nếu tài khoản
 *       admin đã tồn tại, seeder sẽ tự động bỏ qua toàn bộ để tránh lỗi vi phạm khoá duy nhất (duplicate key)
 *       khi khởi động lại ứng dụng.</li>
 *   <li><b>Nguyên tắc tham chiếu ID thuần (Nguyên tắc 1):</b> Mối quan hệ giữa Ticket, User và Reputation
 *       chỉ liên kết thông qua {@code Long sellerId}, {@code Long buyerId}, {@code Long userId},
 *       hoàn toàn không tạo quan hệ JPA cứng giữa các thực thể.</li>
 *   <li><b>Bảo mật thông tin:</b> Mật khẩu người dùng được băm qua bean {@link PasswordEncoder} (BCrypt).</li>
 * </ul>
 *
 * <p>Module sở hữu: shared (Hạ tầng dùng chung toàn sàn).</p>
 * <p>Chủ sở hữu theo phân công: Người C.</p>
 */
@Slf4j
@Component
@Profile("!prod")
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    /** Mật khẩu mặc định thống nhất cho toàn bộ tài khoản thử nghiệm. */
    public static final String DEFAULT_PASSWORD = "Password123@";

    private final UserRepository userRepository;
    private final ReputationRepository reputationRepository;
    private final TicketRepository ticketRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {
        // Kiểm tra tính Idempotent: nếu đã có tài khoản admin thì bỏ qua
        if (userRepository.existsByEmail("admin@test.com")) {
            log.info("[DataInitializer] Dữ liệu mẫu đã tồn tại trong CSDL. Bỏ qua quá trình seed dữ liệu.");
            return;
        }

        log.info("[DataInitializer] Bắt đầu khởi tạo dữ liệu mẫu cho hệ thống TicketPass...");

        String encodedPassword = passwordEncoder.encode(DEFAULT_PASSWORD);
        Instant now = Instant.now();

        // 1. Tạo tài khoản Quản trị viên (ADMIN)
        User admin = createUser(
                "admin@test.com",
                encodedPassword,
                "Quản trị viên TicketPass",
                "0900000001",
                UserRole.ADMIN,
                UserStatus.ACTIVE,
                null, null, null
        );
        admin = userRepository.save(admin);
        createReputationScore(admin.getId(), new BigDecimal("100.00"), 0, 0, BigDecimal.ZERO, null);
        log.info("[DataInitializer] Đã tạo tài khoản Quản trị viên: admin@test.com (ID={})", admin.getId());

        // 2. Tạo tài khoản Người bán thông thường (SELLER)
        User seller = createUser(
                "seller@test.com",
                encodedPassword,
                "Nguyễn Văn Bán",
                "0901234567",
                UserRole.USER,
                UserStatus.ACTIVE,
                "Vietcombank",
                "0071001234567",
                "NGUYEN VAN BAN"
        );
        seller = userRepository.save(seller);
        createReputationScore(
                seller.getId(),
                new BigDecimal("100.00"),
                2,
                2,
                new BigDecimal("3500000.00"),
                now.minus(Duration.ofDays(2))
        );
        log.info("[DataInitializer] Đã tạo tài khoản Người bán: seller@test.com (ID={})", seller.getId());

        // 3. Tạo tài khoản Người mua thông thường (BUYER)
        User buyer = createUser(
                "buyer@test.com",
                encodedPassword,
                "Trần Thị Mua",
                "0912345678",
                UserRole.USER,
                UserStatus.ACTIVE,
                "Techcombank",
                "19036543210987",
                "TRAN THI MUA"
        );
        buyer = userRepository.save(buyer);
        createReputationScore(
                buyer.getId(),
                new BigDecimal("100.00"),
                1,
                1,
                BigDecimal.ZERO,
                now.minus(Duration.ofDays(1))
        );
        log.info("[DataInitializer] Đã tạo tài khoản Người mua: buyer@test.com (ID={})", buyer.getId());

        // 4. Tạo tài khoản Người bán VIP uy tín cao (VIP SELLER - phục vụ Top Sellers)
        User vipSeller = createUser(
                "vip_seller@test.com",
                encodedPassword,
                "Lê Hoàng Uy Tín",
                "0988888888",
                UserRole.USER,
                UserStatus.ACTIVE,
                "MBBank",
                "0888888888",
                "LE HOANG UY TIN"
        );
        vipSeller = userRepository.save(vipSeller);
        createReputationScore(
                vipSeller.getId(),
                new BigDecimal("100.00"),
                25,
                25,
                new BigDecimal("50000000.00"),
                now.minus(Duration.ofHours(5))
        );
        log.info("[DataInitializer] Đã tạo tài khoản Người bán VIP: vip_seller@test.com (ID={})", vipSeller.getId());

        // 5. Khởi tạo các vé mẫu đang ở trạng thái LISTED
        createSampleTickets(seller.getId(), vipSeller.getId(), now);

        log.info("[DataInitializer] Hoàn tất khởi tạo dữ liệu mẫu thành công! Tất cả tài khoản dùng mật khẩu: {}", DEFAULT_PASSWORD);
    }

    /**
     * Helper tạo entity User với các cờ xác minh mặc định hợp lệ.
     */
    private User createUser(String email, String passwordHash, String fullName, String phoneNumber,
                           UserRole role, UserStatus status, String bankName, String bankAccountNumber,
                           String bankAccountHolder) {
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordHash);
        user.setFullName(fullName);
        user.setPhoneNumber(phoneNumber);
        user.setRole(role);
        user.setStatus(status);
        user.setEmailVerified(true);
        user.setPhoneVerified(true);
        user.setIdentityVerified(true);
        user.setBankName(bankName);
        user.setBankAccountNumber(bankAccountNumber);
        user.setBankAccountHolder(bankAccountHolder);
        return user;
    }

    /**
     * Helper tạo và lưu bản ghi điểm uy tín UserReputationScore.
     */
    private void createReputationScore(Long userId, BigDecimal score, int totalTransactions,
                                      int successfulTransactions, BigDecimal totalGmv, Instant lastTxAt) {
        UserReputationScore rep = new UserReputationScore();
        rep.setUserId(userId);
        rep.setScore(score);
        rep.setTotalTransactions(totalTransactions);
        rep.setSuccessfulTransactions(successfulTransactions);
        rep.setCancelledListings(0);
        rep.setDisputesOpenedAgainst(0);
        rep.setDisputesLost(0);
        rep.setDisputesWon(0);
        rep.setTotalGmv(totalGmv);
        rep.setLastTransactionAt(lastTxAt);
        reputationRepository.save(rep);
    }

    /**
     * Helper tạo 3 vé mẫu đại diện cho 3 danh mục CONCERT, MOVIE, BUS.
     */
    private void createSampleTickets(Long sellerId, Long vipSellerId, Instant now) {
        // Vé 1: Concert (Seller bán)
        Ticket concert = new Ticket();
        concert.setSellerId(sellerId);
        concert.setTicketType(TicketType.CONCERT);
        concert.setStatus(TicketStatus.LISTED);
        concert.setTitle("Vé Concert 'Anh Trai Vượt Ngàn Chông Gai 2026' - Khán đài A VIP");
        concert.setEventName("Anh Trai Vượt Ngàn Chông Gai Live Concert 2026");
        concert.setVenue("Sân vận động Quốc gia Mỹ Đình, Hà Nội");
        concert.setSeatInfo("Khán đài A - Khu A1 - Hàng 05 - Ghế 12");
        concert.setEventStartAt(now.plus(Duration.ofDays(7)));
        concert.setOriginalPrice(new BigDecimal("2500000.00"));
        concert.setSalePrice(new BigDecimal("2000000.00"));
        concert.setProviderName("Ticketbox");
        concert.setProviderOrderCode("TB-CONCERT-2026-VIP");
        concert.setTicketImageStorageKey("samples/concert-sample.jpg");
        concert.setQrImageStorageKey("samples-qr/concert-secret-qr.png");
        concert.setTransferInstruction("[CONCERT] Chuyển nhượng trực tiếp\n  1. Người bán mở khoá mã QR bí mật sau khi ký quỹ hoàn tất\n  2. Người mua xuất trình mã QR quét tại cổng soát vé");
        concert.setListedAt(now);
        ticketRepository.save(concert);

        // Vé 2: Movie (Seller bán)
        Ticket movie = new Ticket();
        movie.setSellerId(sellerId);
        movie.setTicketType(TicketType.MOVIE);
        movie.setStatus(TicketStatus.LISTED);
        movie.setTitle("Vé Xem Phim 'Dune: Part Three' - Suất chiếu sớm IMAX Laser");
        movie.setEventName("Dune: Part Three (Khởi chiếu đặc biệt)");
        movie.setVenue("CGV Vincom Center Landmark 81, TP. Hồ Chí Minh");
        movie.setSeatInfo("Phòng chiếu IMAX - Hàng H - Ghế 15, 16");
        movie.setEventStartAt(now.plus(Duration.ofDays(2)));
        movie.setOriginalPrice(new BigDecimal("250000.00"));
        movie.setSalePrice(new BigDecimal("200000.00"));
        movie.setProviderName("CGV Cinemas");
        movie.setProviderOrderCode("CGV-DUNE3-88992");
        movie.setTicketImageStorageKey("samples/movie-sample.jpg");
        movie.setQrImageStorageKey("samples-qr/movie-secret-qr.png");
        movie.setTransferInstruction("[MOVIE] Chuyển vé điện tử\n  1. Người mua quét mã check-in trực tiếp tại Kiosk CGV\n  2. In vé cứng hoặc vào rạp trực tiếp bằng mã vạch");
        movie.setListedAt(now);
        ticketRepository.save(movie);

        // Vé 3: Bus (VIP Seller bán)
        Ticket bus = new Ticket();
        bus.setSellerId(vipSellerId);
        bus.setTicketType(TicketType.BUS);
        bus.setStatus(TicketStatus.LISTED);
        bus.setTitle("Vé Xe Giường Nằm VIP Limousine Sài Gòn đi Đà Lạt (Cuối tuần)");
        bus.setEventName("Tuyến Xe Khách Sài Gòn - Đà Lạt Chuyến 23:00");
        bus.setVenue("Bến xe Miền Đông Mới, TP. Thủ Đức, TP. Hồ Chí Minh");
        bus.setSeatInfo("Tầng 1 - Giường 06 (Khoang VIP riêng tư)");
        bus.setEventStartAt(now.plus(Duration.ofDays(3)));
        bus.setOriginalPrice(new BigDecimal("380000.00"));
        bus.setSalePrice(new BigDecimal("300000.00"));
        bus.setProviderName("Phương Trang FUTA Bus Lines");
        bus.setProviderOrderCode("FUTA-SGN-DLA-5511");
        bus.setTicketImageStorageKey("samples/bus-sample.jpg");
        bus.setQrImageStorageKey("samples-qr/bus-secret-qr.png");
        bus.setTransferInstruction("[BUS] Chuyển vé xe khách\n  1. Đến phòng vé FUTA trước 30 phút xuất trình mã vé\n  2. Nhận vé lên xe và xuất bến đúng giờ");
        bus.setListedAt(now);
        ticketRepository.save(bus);

        log.info("[DataInitializer] Đã khởi tạo 3 vé mẫu (CONCERT, MOVIE, BUS) thành công ở trạng thái LISTED.");
    }
}
