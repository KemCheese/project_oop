package com.yourapp.ticketpass.shared.infrastructure.data;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
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
import com.yourapp.ticketpass.wallet.application.WalletService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Component khởi tạo dữ liệu mẫu (Data Seeding) cho toàn bộ hệ thống
 * TicketPass.
 *
 * <p>
 * <b>Mục đích:</b> Tự động nạp các tài khoản người dùng chuẩn (Admin, Seller,
 * Buyer,
 * VIP Seller), điểm uy tín tương ứng và các vé mẫu ở trạng thái
 * {@link TicketStatus#LISTED}
 * ngay khi ứng dụng khởi động. Điều này giúp nhóm phát triển, tester và người
 * chấm đồ án
 * có thể đăng nhập và trải nghiệm ngay sàn giao dịch mà không phải tạo thủ công
 * qua form
 * hoặc can thiệp lệnh SQL vào database.
 * </p>
 *
 * <p>
 * <b>Nguyên tắc bảo vệ kiến trúc:</b>
 * </p>
 * <ul>
 * <li>{@code @Profile("!prod")}: Chỉ hoạt động trên các môi trường phát triển,
 * cục bộ và kiểm thử
 * (local, dev, test). Tuyệt đối không chạy trên môi trường production thực
 * tế.</li>
 * <li><b>Tính Idempotent:</b> Kiểm tra
 * {@code userRepository.existsByEmail("admin@test.com")}. Nếu tài khoản
 * admin đã tồn tại, seeder sẽ tự động bỏ qua toàn bộ để tránh lỗi vi phạm khoá
 * duy nhất (duplicate key)
 * khi khởi động lại ứng dụng.</li>
 * <li><b>Nguyên tắc tham chiếu ID thuần (Nguyên tắc 1):</b> Mối quan hệ giữa
 * Ticket, User và Reputation
 * chỉ liên kết thông qua {@code Long sellerId}, {@code Long buyerId},
 * {@code Long userId},
 * hoàn toàn không tạo quan hệ JPA cứng giữa các thực thể.</li>
 * <li><b>Bảo mật thông tin:</b> Mật khẩu người dùng được băm qua bean
 * {@link PasswordEncoder} (BCrypt).</li>
 * </ul>
 *
 * <p>
 * Module sở hữu: shared (Hạ tầng dùng chung toàn sàn).
 * </p>
 * <p>
 * Chủ sở hữu theo phân công: Người C.
 * </p>
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
    private final WalletService walletService;

    @Value("${app.seed.c15-testing-tickets:false}")
    private boolean enableC15TestingTickets;

    @Override
    @Transactional
    public void run(String... args) {
        // Kiểm tra tính Idempotent: nếu đã có tài khoản admin thì bỏ qua tạo user
        if (userRepository.existsByEmail("admin@test.com")) {
            User seller = userRepository.findByEmail("seller@test.com").orElse(null);
            User buyer = userRepository.findByEmail("buyer@test.com").orElse(null);
            User vipSeller = userRepository.findByEmail("vip_seller@test.com").orElse(null);
            Instant now = Instant.now();

            if (enableC15TestingTickets && buyer != null && seller != null) {
                seedC15TestingTickets(buyer.getId(), seller.getId(), now);
            }

            long listedCount = ticketRepository.findByStatus(TicketStatus.LISTED).size();
            if (listedCount < 50) {
                if (seller != null && vipSeller != null) {
                    log.info(
                            "[DataInitializer] Phát hiện sàn có {} vé, đang nạp bổ sung 50 vé mẫu phục vụ kiểm thử phân trang...",
                            listedCount);
                    seedFiftyCustomTickets(seller.getId(), vipSeller.getId(), now);
                }
            } else {
                log.info("[DataInitializer] Dữ liệu mẫu đã tồn tại trong CSDL. Bỏ qua quá trình seed dữ liệu.");
            }
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
                null, null, null);
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
                "NGUYEN VAN BAN");
        seller = userRepository.save(seller);
        createReputationScore(
                seller.getId(),
                new BigDecimal("100.00"),
                2,
                2,
                new BigDecimal("3500000.00"),
                now.minus(Duration.ofDays(2)));
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
                "TRAN THI MUA");
        buyer = userRepository.save(buyer);
        createReputationScore(
                buyer.getId(),
                new BigDecimal("100.00"),
                1,
                1,
                BigDecimal.ZERO,
                now.minus(Duration.ofDays(1)));
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
                "LE HOANG UY TIN");
        vipSeller = userRepository.save(vipSeller);
        createReputationScore(
                vipSeller.getId(),
                new BigDecimal("100.00"),
                25,
                25,
                new BigDecimal("50000000.00"),
                now.minus(Duration.ofHours(5)));
        // 5. Tạo tài khoản Người mua Hết Tiền (BROKE BUYER - Số dư 0đ phục vụ test
        // không đủ tiền)
        User brokeBuyer = createUser(
                "broke_buyer@test.com",
                encodedPassword,
                "Lê Văn Hết Tiền",
                "0911000000",
                UserRole.USER,
                UserStatus.ACTIVE,
                null, null, null);
        brokeBuyer = userRepository.save(brokeBuyer);
        createReputationScore(brokeBuyer.getId(), new BigDecimal("100.00"), 0, 0, BigDecimal.ZERO, null);
        walletService.getOrCreateWallet(brokeBuyer.getId()); // Số dư ban đầu chính xác 0đ
        log.info("[DataInitializer] Đã tạo tài khoản Người mua hết tiền: broke_buyer@test.com (ID={}, Số dư ví: 0đ)",
                brokeBuyer.getId());

        // 6. Nạp số dư ban đầu cho các ví điện tử thử nghiệm
        walletService.topUp(admin.getId(), new BigDecimal("50000000.00"), "Quỹ số dư ban đầu quản trị viên");
        walletService.topUp(seller.getId(), new BigDecimal("10000000.00"), "Số dư ban đầu phục vụ nộp cọc vé");
        walletService.topUp(buyer.getId(), new BigDecimal("10000000.00"), "Số dư ban đầu phục vụ mua vé ký quỹ");
        walletService.topUp(vipSeller.getId(), new BigDecimal("20000000.00"), "Số dư ban đầu tài khoản người bán VIP");
        log.info(
                "[DataInitializer] Đã khởi tạo số dư ví điện tử thành công: Admin (50tr), Seller (10tr), Buyer (10tr), BrokeBuyer (0đ)!");

        // 7. Khởi tạo các vé mẫu đang ở trạng thái LISTED
        createSampleTickets(seller.getId(), vipSeller.getId(), now);
        seedFiftyCustomTickets(seller.getId(), vipSeller.getId(), now);
        if (enableC15TestingTickets) {
            seedC15TestingTickets(buyer.getId(), seller.getId(), now);
        }

        log.info("[DataInitializer] Hoàn tất khởi tạo dữ liệu mẫu thành công! Tất cả tài khoản dùng mật khẩu: {}",
                DEFAULT_PASSWORD);
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
        concert.setEventEndAt(concert.getEventStartAt().plus(Duration.ofHours(3)));
        concert.setOriginalPrice(new BigDecimal("2500000.00"));
        concert.setSalePrice(new BigDecimal("2000000.00"));
        concert.setProviderName("Ticketbox");
        concert.setProviderOrderCode("TB-CONCERT-2026-VIP");
        concert.setTicketImageStorageKey("samples/concert-sample.jpg");
        concert.setQrImageStorageKey("samples-qr/concert-secret-qr.png");
        concert.setTransferInstruction(
                "[CONCERT] Chuyển nhượng trực tiếp\n  1. Người bán mở khoá mã QR bí mật sau khi ký quỹ hoàn tất\n  2. Người mua xuất trình mã QR quét tại cổng soát vé");
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
        movie.setEventEndAt(movie.getEventStartAt().plus(Duration.ofHours(2)));
        movie.setOriginalPrice(new BigDecimal("250000.00"));
        movie.setSalePrice(new BigDecimal("200000.00"));
        movie.setProviderName("CGV Cinemas");
        movie.setProviderOrderCode("CGV-DUNE3-88992");
        movie.setTicketImageStorageKey("samples/movie-sample.jpg");
        movie.setQrImageStorageKey("samples-qr/movie-secret-qr.png");
        movie.setTransferInstruction(
                "[MOVIE] Chuyển vé điện tử\n  1. Người mua quét mã check-in trực tiếp tại Kiosk CGV\n  2. In vé cứng hoặc vào rạp trực tiếp bằng mã vạch");
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
        bus.setEventEndAt(bus.getEventStartAt().plus(Duration.ofHours(4)));
        bus.setOriginalPrice(new BigDecimal("380000.00"));
        bus.setSalePrice(new BigDecimal("300000.00"));
        bus.setProviderName("Phương Trang FUTA Bus Lines");
        bus.setProviderOrderCode("FUTA-SGN-DLA-5511");
        bus.setTicketImageStorageKey("samples/bus-sample.jpg");
        bus.setQrImageStorageKey("samples-qr/bus-secret-qr.png");
        bus.setTransferInstruction(
                "[BUS] Chuyển vé xe khách\n  1. Đến phòng vé FUTA trước 30 phút xuất trình mã vé\n  2. Nhận vé lên xe và xuất bến đúng giờ");
        bus.setListedAt(now);
        ticketRepository.save(bus);

        log.info("[DataInitializer] Đã khởi tạo 3 vé mẫu (CONCERT, MOVIE, BUS) thành công ở trạng thái LISTED.");
    }

    /**
     * Helper tạo entity Ticket với đầy đủ thông tin chi tiết.
     */
    private Ticket createTicket(Long sellerId, TicketType type, String title, String eventName,
            String venue, String seatInfo, Instant startAt,
            BigDecimal originalPrice, BigDecimal salePrice,
            String providerName, String orderCode,
            String imageUrl, String instruction, Instant listedAt) {
        Ticket ticket = new Ticket();
        ticket.setSellerId(sellerId);
        ticket.setTicketType(type);
        ticket.setStatus(TicketStatus.LISTED);
        ticket.setTitle(title);
        ticket.setEventName(eventName);
        ticket.setVenue(venue);
        ticket.setSeatInfo(seatInfo);
        ticket.setEventStartAt(startAt);
        ticket.setEventEndAt(startAt != null ? startAt.plus(resolveSampleDuration(type)) : null);
        ticket.setOriginalPrice(originalPrice);
        ticket.setSalePrice(salePrice);
        ticket.setProviderName(providerName);
        ticket.setProviderOrderCode(orderCode);
        ticket.setTicketImageStorageKey(imageUrl);
        ticket.setQrImageStorageKey("samples-qr/mock-secret-qr.png");
        ticket.setTransferInstruction(instruction);
        ticket.setListedAt(listedAt);
        return ticket;
    }

    private Duration resolveSampleDuration(TicketType type) {
        if (type == null) {
            return Duration.ofHours(2);
        }
        return switch (type) {
            case MOVIE -> Duration.ofHours(2);
            case CONCERT -> Duration.ofHours(3);
            case BUS -> Duration.ofHours(4);
        };
    }

    /**
     * Khởi tạo bổ sung 50 vé mẫu: 24 vé phim, 14 vé concert, 12 vé xe khách có ảnh
     * web Unsplash trực tiếp.
     */
    private void seedFiftyCustomTickets(Long sellerId, Long vipSellerId, Instant now) {
        List<Ticket> tickets = new ArrayList<>(50);

        // ──────────────────────── 24 VÉ XEM PHIM (MOVIE) ────────────────────────
        String movieImg1 = "https://images.unsplash.com/photo-1489599849927-2ee91cede3ba?w=800&auto=format&fit=crop&q=80";
        String movieImg2 = "https://images.unsplash.com/photo-1517604931442-7e0c8ed2963c?w=800&auto=format&fit=crop&q=80";
        String movieImg3 = "https://images.unsplash.com/photo-1536440136628-849c177e76a1?w=800&auto=format&fit=crop&q=80";
        String movieImg4 = "https://images.unsplash.com/photo-1478720568477-152d9b164e26?w=800&auto=format&fit=crop&q=80";
        String movieImg5 = "https://images.unsplash.com/photo-1509198397868-475647b2a1e5?w=800&auto=format&fit=crop&q=80";
        String movieImg6 = "https://images.unsplash.com/photo-1440404653325-ab127d49abc1?w=800&auto=format&fit=crop&q=80";
        String movieImg7 = "https://images.unsplash.com/photo-1594909122845-11baa439b7bf?w=800&auto=format&fit=crop&q=80";
        String movieImg8 = "https://images.unsplash.com/photo-1574375927938-d5a98e8ffe85?w=800&auto=format&fit=crop&q=80";
        String movieInstruction = "[MOVIE] Chuyển vé điện tử\n  1. Quét mã check-in trực tiếp tại Kiosk rạp\n  2. In vé cứng hoặc vào rạp trực tiếp bằng mã vạch";

        tickets.add(createTicket(sellerId, TicketType.MOVIE, "Vé Xem Phim 'Dune: Part Two' - Suất Chiếu IMAX Laser",
                "Dune: Part Two", "CGV Landmark 81, TP.HCM", "Phòng IMAX - Hàng F - Ghế 10, 11",
                now.plus(Duration.ofDays(2)), new BigDecimal("260000"), new BigDecimal("200000"), "CGV Cinemas",
                "CGV-DUNE2-01", movieImg1, movieInstruction, now));
        tickets.add(createTicket(vipSellerId, TicketType.MOVIE, "Vé Xem Phim 'Mai' - Phòng Chiếu Giường Nằm L'amour",
                "Mai (Trấn Thành)", "CGV Crescent Mall, Quận 7", "Phòng L'amour - Giường 02",
                now.plus(Duration.ofDays(3)), new BigDecimal("350000"), new BigDecimal("280000"), "CGV Cinemas",
                "CGV-MAI-02", movieImg2, movieInstruction, now));
        tickets.add(createTicket(sellerId, TicketType.MOVIE, "Vé Phim 'Lật Mặt 7: Một Điều Ước' - Ghế VIP Đôi",
                "Lật Mặt 7: Một Điều Ước", "CGV Vincom Đồng Khởi, Quận 1", "Phòng 3 - Hàng H - Ghế 08, 09",
                now.plus(Duration.ofDays(4)), new BigDecimal("240000"), new BigDecimal("190000"), "CGV Cinemas",
                "CGV-LM7-03", movieImg3, movieInstruction, now));
        tickets.add(createTicket(sellerId, TicketType.MOVIE, "Vé Xem Phim 'Conan: Ngôi Sao 5 Cánh 1 Triệu Đô'",
                "Thám Tử Lừng Danh Conan 2026", "BHD Star Bitexco, TP.HCM", "Phòng 2 - Hàng G - Ghế 14",
                now.plus(Duration.ofDays(1)), new BigDecimal("130000"), new BigDecimal("95000"), "BHD Star",
                "BHD-CONAN-04", movieImg4, movieInstruction, now));
        tickets.add(createTicket(vipSellerId, TicketType.MOVIE, "Vé 'Avengers: Doomsday' - Suất Chiếu Nửa Đêm Premiere",
                "Avengers: Doomsday", "CGV Metropolis Liễu Giai, Hà Nội", "Phòng IMAX Laser - Hàng E - Ghế 12",
                now.plus(Duration.ofDays(5)), new BigDecimal("320000"), new BigDecimal("250000"), "CGV Cinemas",
                "CGV-AVEN-05", movieImg5, movieInstruction, now));
        tickets.add(createTicket(sellerId, TicketType.MOVIE, "Vé 'Godzilla x Kong: Đế Chế Mới' - 3D Atmos",
                "Godzilla x Kong: The New Empire", "Lotte Cinema Nowzone, TP.HCM", "Phòng Superplex - Hàng K - Ghế 05",
                now.plus(Duration.ofDays(6)), new BigDecimal("180000"), new BigDecimal("140000"), "Lotte Cinema",
                "LC-GZK-06", movieImg6, movieInstruction, now));
        tickets.add(createTicket(vipSellerId, TicketType.MOVIE, "Vé 'Inside Out 2' - Lồng Tiếng Việt Phòng Gia Đình",
                "Những Mảnh Ghép Cảm Xúc 2", "CGV Aeon Mall Hà Đông", "Phòng 5 - Hàng D - Ghế 06, 07, 08",
                now.plus(Duration.ofDays(2)), new BigDecimal("360000"), new BigDecimal("290000"), "CGV Cinemas",
                "CGV-INSO-07", movieImg7, movieInstruction, now));
        tickets.add(createTicket(sellerId, TicketType.MOVIE, "Vé Phim 'Deadpool & Wolverine' - Ghế Sweetbox Đôi",
                "Deadpool & Wolverine", "CGV Vạn Hạnh Mall, Quận 10", "Phòng 6 - Sweetbox 04",
                now.plus(Duration.ofDays(3)), new BigDecimal("280000"), new BigDecimal("220000"), "CGV Cinemas",
                "CGV-DPWL-08", movieImg8, movieInstruction, now));
        tickets.add(createTicket(sellerId, TicketType.MOVIE, "Vé Phim 'Kung Fu Panda 4' - Phòng Chiếu Tiêu Chuẩn",
                "Kung Fu Panda 4", "BHD Star Phạm Ngọc Thạch, Hà Nội", "Phòng 4 - Hàng F - Ghế 09",
                now.plus(Duration.ofDays(4)), new BigDecimal("120000"), new BigDecimal("90000"), "BHD Star",
                "BHD-KFP4-09", movieImg1, movieInstruction, now));
        tickets.add(createTicket(vipSellerId, TicketType.MOVIE, "Vé 'Quỷ Cẩu' - Suất Chiếu Kinh Dị Đêm Muộn 23:15",
                "Quỷ Cẩu (Phim Kinh Dị)", "CGV Sư Vạn Hạnh, Quận 10", "Phòng 1 - Hàng J - Ghế 11",
                now.plus(Duration.ofDays(1)), new BigDecimal("150000"), new BigDecimal("110000"), "CGV Cinemas",
                "CGV-QC-10", movieImg2, movieInstruction, now));
        tickets.add(createTicket(sellerId, TicketType.MOVIE, "Vé 'Đất Rừng Phương Nam' - Suất Chiếu Đặc Biệt",
                "Đất Rừng Phương Nam", "Lotte Cinema Tây Hồ, Hà Nội", "Phòng 3 - Hàng H - Ghế 07",
                now.plus(Duration.ofDays(7)), new BigDecimal("140000"), new BigDecimal("100000"), "Lotte Cinema",
                "LC-DRPN-11", movieImg3, movieInstruction, now));
        tickets.add(createTicket(vipSellerId, TicketType.MOVIE, "Vé 'Oppenheimer' - Tái Chiếu Định Dạng IMAX Laser",
                "Oppenheimer", "CGV Landmark 81, TP.HCM", "Phòng IMAX - Hàng G - Ghế 15", now.plus(Duration.ofDays(8)),
                new BigDecimal("250000"), new BigDecimal("200000"), "CGV Cinemas", "CGV-OPPEN-12", movieImg4,
                movieInstruction, now));
        tickets.add(createTicket(sellerId, TicketType.MOVIE, "Vé 'Spider-Man: Beyond the Spider-Verse' - VIP",
                "Spider-Man: Beyond the Spider-Verse", "CGV Vincom Bà Triệu, Hà Nội", "Phòng 2 - Hàng F - Ghế 12, 13",
                now.plus(Duration.ofDays(9)), new BigDecimal("260000"), new BigDecimal("210000"), "CGV Cinemas",
                "CGV-SPID-13", movieImg5, movieInstruction, now));
        tickets.add(createTicket(sellerId, TicketType.MOVIE, "Vé 'Exhuma: Quật Mộ Trùng Ma' - Suất Chiếu Cuối Tuần",
                "Exhuma (Quật Mộ Trùng Ma)", "BHD Star 3/2, Quận 10", "Phòng 5 - Hàng E - Ghế 08",
                now.plus(Duration.ofDays(2)), new BigDecimal("130000"), new BigDecimal("100000"), "BHD Star",
                "BHD-EXHU-14", movieImg6, movieInstruction, now));
        tickets.add(createTicket(vipSellerId, TicketType.MOVIE, "Vé 'Despicable Me 4' - Phòng Chiếu Premium",
                "Kẻ Trộm Mặt Trăng 4", "CGV SC VivoCity, Quận 7", "Phòng Premium - Hàng C - Ghế 05",
                now.plus(Duration.ofDays(5)), new BigDecimal("200000"), new BigDecimal("160000"), "CGV Cinemas",
                "CGV-DM4-15", movieImg7, movieInstruction, now));
        tickets.add(createTicket(sellerId, TicketType.MOVIE, "Vé 'Transformers One' - Suất Chiếu 3D IMAX",
                "Transformers One", "CGV Metropolis Liễu Giai, Hà Nội", "Phòng IMAX - Hàng H - Ghế 09",
                now.plus(Duration.ofDays(6)), new BigDecimal("240000"), new BigDecimal("190000"), "CGV Cinemas",
                "CGV-TFONE-16", movieImg8, movieInstruction, now));
        tickets.add(
                createTicket(vipSellerId, TicketType.MOVIE, "Vé 'Joker: Folie à Deux' - Rạp Gold Class VIP Sang Trọng",
                        "Joker 2: Folie à Deux", "CGV Parkson Hùng Vương, Quận 5", "Phòng Gold Class - Ghế Luxury 06",
                        now.plus(Duration.ofDays(4)), new BigDecimal("350000"), new BigDecimal("290000"), "CGV Cinemas",
                        "CGV-JOKER-17", movieImg1, movieInstruction, now));
        tickets.add(createTicket(sellerId, TicketType.MOVIE, "Vé 'Kingdom of the Planet of the Apes' - Suất Chiếu Sớm",
                "Hành Tinh Khỉ: Vương Quốc Mới", "Lotte Cinema Diamond Plaza, Quận 1", "Phòng 1 - Hàng G - Ghế 10",
                now.plus(Duration.ofDays(3)), new BigDecimal("150000"), new BigDecimal("120000"), "Lotte Cinema",
                "LC-APES-18", movieImg2, movieInstruction, now));
        tickets.add(createTicket(sellerId, TicketType.MOVIE, "Vé 'Nhà Bà Nữ' - Suất Chiếu Kỷ Niệm Phòng Chiếu 1",
                "Nhà Bà Nữ", "CGV Aeon Mall Bình Tân", "Phòng 1 - Hàng F - Ghế 04", now.plus(Duration.ofDays(10)),
                new BigDecimal("120000"), new BigDecimal("90000"), "CGV Cinemas", "CGV-NBN-19", movieImg3,
                movieInstruction, now));
        tickets.add(createTicket(vipSellerId, TicketType.MOVIE, "Vé 'The Expendables 4' - Phòng Chiếu Âm Thanh Dolby",
                "Biệt Đội Đánh Thuê 4", "Galaxy Cinema Nguyễn Du, Quận 1", "Phòng 2 - Hàng H - Ghế 16",
                now.plus(Duration.ofDays(4)), new BigDecimal("130000"), new BigDecimal("95000"), "Galaxy Cinema",
                "GLX-EXP4-20", movieImg4, movieInstruction, now));
        tickets.add(createTicket(sellerId, TicketType.MOVIE, "Vé 'Doraemon: Bản Giao Hưởng Địa Cầu' - Phim Gia Đình",
                "Doraemon Movie 2026", "CGV Vincom Ocean Park, Hà Nội", "Phòng 4 - Hàng D - Ghế 11, 12",
                now.plus(Duration.ofDays(1)), new BigDecimal("220000"), new BigDecimal("170000"), "CGV Cinemas",
                "CGV-DORA-21", movieImg5, movieInstruction, now));
        tickets.add(createTicket(vipSellerId, TicketType.MOVIE, "Vé 'Gladiator II' - Suất Chiếu Đặc Biệt IMAX Laser",
                "Võ Sĩ Giác Đấu 2", "CGV Landmark 81, TP.HCM", "Phòng IMAX - Hàng F - Ghế 14",
                now.plus(Duration.ofDays(11)), new BigDecimal("270000"), new BigDecimal("220000"), "CGV Cinemas",
                "CGV-GLAD2-22", movieImg6, movieInstruction, now));
        tickets.add(createTicket(sellerId, TicketType.MOVIE, "Vé Phim Kinh Dị 'Móng Vuốt' - Ghế Trung Tâm",
                "Móng Vuốt (Kinh Dị Sinh Tồn)", "BHD Star Discovery, Cầu Giấy, Hà Nội", "Phòng 3 - Hàng G - Ghế 08",
                now.plus(Duration.ofDays(5)), new BigDecimal("130000"), new BigDecimal("95000"), "BHD Star",
                "BHD-MV-23", movieImg7, movieInstruction, now));
        tickets.add(createTicket(sellerId, TicketType.MOVIE, "Vé 'Furiosa: Mad Max Saga' - Phòng Chiếu Màn Hình Lớn",
                "Furiosa: A Mad Max Saga", "CGV Sư Vạn Hạnh, Quận 10", "Phòng 7 - Hàng J - Ghế 10",
                now.plus(Duration.ofDays(7)), new BigDecimal("160000"), new BigDecimal("130000"), "CGV Cinemas",
                "CGV-FURI-24", movieImg8, movieInstruction, now));

        // ──────────────────────── 14 VÉ CONCERT (CONCERT) ────────────────────────
        String concertImg1 = "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=800&auto=format&fit=crop&q=80";
        String concertImg2 = "https://images.unsplash.com/photo-1501386761578-eac5c94b800a?w=800&auto=format&fit=crop&q=80";
        String concertImg3 = "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=800&auto=format&fit=crop&q=80";
        String concertImg4 = "https://images.unsplash.com/photo-1459749411175-04bf5292ceea?w=800&auto=format&fit=crop&q=80";
        String concertImg5 = "https://images.unsplash.com/photo-1492684223066-81342ee5ff30?w=800&auto=format&fit=crop&q=80";
        String concertImg6 = "https://images.unsplash.com/photo-1465847899084-d164df4dedc6?w=800&auto=format&fit=crop&q=80";
        String concertInstruction = "[CONCERT] Chuyển nhượng trực tiếp\n  1. Mở khoá mã QR bí mật sau khi ký quỹ hoàn tất\n  2. Xuất trình mã QR quét tại cổng soát vé vào khán đài";

        tickets.add(createTicket(vipSellerId, TicketType.CONCERT,
                "Vé Concert 'Anh Trai Say Hi 2026' - Khu Fanzone Đứng Sát Sân Khấu",
                "Anh Trai Say Hi Live Concert 2026", "Sân Vận Động Mỹ Đình, Hà Nội",
                "Khu Fanzone A - Vé đứng gần sân khấu", now.plus(Duration.ofDays(14)), new BigDecimal("2500000"),
                new BigDecimal("2200000"), "Ticketbox", "TB-ATSH-01", concertImg1, concertInstruction, now));
        tickets.add(createTicket(sellerId, TicketType.CONCERT,
                "Vé Mỹ Tâm 'My Soul 1981' Mùa 4 Tại Đà Lạt - Hạng VIP Đèn Lồng", "My Soul 1981 Mùa 4 (Mỹ Tâm)",
                "Đồi thông Đà Lạt, Lâm Đồng", "Khu VIP Đèn Lồng - Bàn 08 - Ghế 02", now.plus(Duration.ofDays(20)),
                new BigDecimal("3500000"), new BigDecimal("3100000"), "Ticketbox", "TB-MYSOUL-02", concertImg2,
                concertInstruction, now));
        tickets.add(createTicket(vipSellerId, TicketType.CONCERT, "Vé Hà Anh Tuấn 'Chân Trời Rực Rỡ' - Cố Đô Hoa Lư",
                "Chân Trời Rực Rỡ Live Concert", "Cố Đô Hoa Lư, Ninh Bình", "Khán đài Chân Trời - Hàng C - Ghế 15",
                now.plus(Duration.ofDays(18)), new BigDecimal("2800000"), new BigDecimal("2400000"), "Ticketbox",
                "TB-HAT-03", concertImg3, concertInstruction, now));
        tickets.add(createTicket(sellerId, TicketType.CONCERT, "Vé Vũ 'Bảo Tàng Của Nuối Tiếc' Tour 2026 - Khu A",
                "Bảo Tàng Của Nuối Tiếc Concert", "Nhà Thi Đấu Phú Thọ, TP.HCM", "Khu Đứng A - Vé Early Entry",
                now.plus(Duration.ofDays(10)), new BigDecimal("1200000"), new BigDecimal("950000"), "Ticketbox",
                "TB-VU-04", concertImg4, concertInstruction, now));
        tickets.add(createTicket(vipSellerId, TicketType.CONCERT,
                "Vé Đen Vâu 'Show Của Đen 2026' - Khán Đài A1 Mỹ Đình", "Show Của Đen 2026", "Cung Điền Kinh Hà Nội",
                "Khán đài A1 - Hàng 06 - Ghế 18", now.plus(Duration.ofDays(25)), new BigDecimal("1800000"),
                new BigDecimal("1500000"), "Ticketbox", "TB-DEN-05", concertImg5, concertInstruction, now));
        tickets.add(
                createTicket(sellerId, TicketType.CONCERT, "Vé Chillies Tour 'Trên Những Đám Mây' - Vé Đứng Sân Khấu",
                        "Trên Những Đám Mây Concert Tour", "Nhà Thi Đấu Cầu Giấy, Hà Nội",
                        "Khu vực Đứng Tự Do - Cửa 02", now.plus(Duration.ofDays(12)), new BigDecimal("850000"),
                        new BigDecimal("700000"), "Ticketbox", "TB-CHILL-06", concertImg6, concertInstruction, now));
        tickets.add(createTicket(vipSellerId, TicketType.CONCERT,
                "Vé Monsoon Music Festival 2026 - Vé Combo 3 Ngày Hoàng Thành", "Monsoon Music Festival 2026",
                "Hoàng Thành Thăng Long, Hà Nội", "Vé 3-Day Pass Toàn Bộ Sân Khấu", now.plus(Duration.ofDays(30)),
                new BigDecimal("1600000"), new BigDecimal("1350000"), "Monsoon Festival", "MMF-PASS-07", concertImg1,
                concertInstruction, now));
        tickets.add(createTicket(sellerId, TicketType.CONCERT,
                "Vé Rap Việt All-Star Concert 2026 - Hạng Vé VIP Diamond", "Rap Việt All-Star Concert 2026",
                "Trung Tâm Hội Chợ Triển Lãm SECC, Q.7", "Khu VIP Diamond - Ghế D12", now.plus(Duration.ofDays(15)),
                new BigDecimal("2200000"), new BigDecimal("1850000"), "VieON Tickets", "VIE-RAP-08", concertImg2,
                concertInstruction, now));
        tickets.add(createTicket(vipSellerId, TicketType.CONCERT,
                "Vé Hoàng Dũng 'Yên' Concert TP.HCM - Hạng Vé Nắng Thủy Tinh", "Yên Concert 2026 (Hoàng Dũng)",
                "Sân Khấu Lan Anh, TP.HCM", "Khu Nắng Thủy Tinh - Ghế B09", now.plus(Duration.ofDays(16)),
                new BigDecimal("1100000"), new BigDecimal("900000"), "Ticketbox", "TB-HDUNG-09", concertImg3,
                concertInstruction, now));
        tickets.add(createTicket(sellerId, TicketType.CONCERT, "Vé Soobin Live Stage 'Bật Nó Lên' - Khán Đài B1",
                "Soobin Live Stage 2026", "Nhà Thi Đấu Nguyễn Du, Quận 1", "Khán đài B1 - Hàng 04 - Ghế 10",
                now.plus(Duration.ofDays(8)), new BigDecimal("1400000"), new BigDecimal("1150000"), "Ticketbox",
                "TB-SOOB-10", concertImg4, concertInstruction, now));
        tickets.add(
                createTicket(vipSellerId, TicketType.CONCERT, "Vé Đêm Nhạc Indie 'Tháng Năm' - Sân Khấu Acoustic Mộc",
                        "Đêm Nhạc Indie Tháng Năm", "Phù Sa Lab, Tây Hồ, Hà Nội", "Khu vực Bàn Trà - Vé Đôi 02 Người",
                        now.plus(Duration.ofDays(5)), new BigDecimal("600000"), new BigDecimal("480000"), "Ticketbox",
                        "TB-INDIE-11", concertImg5, concertInstruction, now));
        tickets.add(
                createTicket(sellerId, TicketType.CONCERT, "Vé Da LAB 'Thanh Xuân' Farewell Concert - Hạng GA Standing",
                        "Da LAB Farewell Concert 2026", "Cung Thể Thao Quần Ngựa, Hà Nội",
                        "Khu GA Standing - Vé Nhập Cửa Sớm", now.plus(Duration.ofDays(22)), new BigDecimal("950000"),
                        new BigDecimal("800000"), "Ticketbox", "TB-DALAB-12", concertImg6, concertInstruction, now));
        tickets.add(
                createTicket(vipSellerId, TicketType.CONCERT, "Vé Tăng Duy Tân Live Show 'Cắt Đôi Nỗi Sầu' - Ghế VIP 1",
                        "Cắt Đôi Nỗi Sầu Live Tour", "Sân Khấu Trống Đồng, TP.HCM", "Khu VIP 1 - Hàng A - Ghế 06",
                        now.plus(Duration.ofDays(11)), new BigDecimal("1300000"), new BigDecimal("1050000"),
                        "Ticketbox", "TB-TDT-13", concertImg1, concertInstruction, now));
        tickets.add(createTicket(sellerId, TicketType.CONCERT,
                "Vé The Masked Singer Vietnam All-Star Concert - Khán Đài VVIP", "The Masked Singer All-Star 2026",
                "Khu Đô Thị Sala Thủ Thiêm, TP.HCM", "Khán đài VVIP - Ghế 08 Hàng 2", now.plus(Duration.ofDays(28)),
                new BigDecimal("3000000"), new BigDecimal("2600000"), "VieON Tickets", "VIE-MASK-14", concertImg2,
                concertInstruction, now));

        // ──────────────────────── 12 VÉ XE KHÁCH (BUS) ────────────────────────
        String busImg1 = "https://images.unsplash.com/photo-1544620347-c4fd4a3d5957?w=800&auto=format&fit=crop&q=80";
        String busImg2 = "https://images.unsplash.com/photo-1570125909232-eb263c188f7e?w=800&auto=format&fit=crop&q=80";
        String busImg3 = "https://images.unsplash.com/photo-1557223562-6c77ef16210f?w=800&auto=format&fit=crop&q=80";
        String busImg4 = "https://images.unsplash.com/photo-1517524008697-84bbe3c3fd98?w=800&auto=format&fit=crop&q=80";
        String busInstruction = "[BUS] Chuyển vé xe khách\n  1. Đến văn phòng nhà xe trước giờ khởi hành 30 phút\n  2. Xuất trình mã vé điện tử để nhận vé lên xe đúng giờ";

        tickets.add(createTicket(vipSellerId, TicketType.BUS,
                "Vé Xe Limousine 34 Giường Sài Gòn đi Đà Lạt (23:30 Tối Thứ Sáu)",
                "Tuyến Xe Sài Gòn - Đà Lạt (Phương Trang)", "Bến Xe Miền Đông Mới, TP.HCM",
                "Tầng 1 - Giường 08 (Khoang riêng tư)", now.plus(Duration.ofDays(2)), new BigDecimal("350000"),
                new BigDecimal("290000"), "Phương Trang FUTA", "FUTA-SG-DL-01", busImg1, busInstruction, now));
        tickets.add(
                createTicket(sellerId, TicketType.BUS, "Vé Xe Cabin Đôi Cung Điện Hà Nội đi Sa Pa (Inter Bus Lines)",
                        "Tuyến Hà Nội - Sa Pa (Xe Cabin Tình Yêu)", "Bến Xe Mỹ Đình, Hà Nội",
                        "Cabin Đôi Số 05 - Tầng 1", now.plus(Duration.ofDays(3)), new BigDecimal("680000"),
                        new BigDecimal("550000"), "Inter Bus Lines", "IBL-HN-SP-02", busImg2, busInstruction, now));
        tickets.add(createTicket(vipSellerId, TicketType.BUS,
                "Vé Limousine 9 Chỗ VIP Sài Gòn đi Vũng Tàu (Đưa Đón Tận Nơi)", "Tuyến Sài Gòn - Vũng Tàu (Hoa Mai)",
                "Số 83 Nguyễn Thái Bình, Quận 1", "Ghế VIP Số 03 (Ghế massage bọc da)", now.plus(Duration.ofDays(1)),
                new BigDecimal("220000"), new BigDecimal("180000"), "Hoa Mai Limousine", "HM-SG-VT-03", busImg3,
                busInstruction, now));
        tickets.add(createTicket(sellerId, TicketType.BUS, "Vé Xe Giường Nằm Đà Nẵng đi Quy Nhơn (Thuận Thảo)",
                "Tuyến Đà Nẵng - Quy Nhơn (Bình Định)", "Bến Xe Trung Tâm Đà Nẵng", "Tầng 1 - Giường A04",
                now.plus(Duration.ofDays(4)), new BigDecimal("280000"), new BigDecimal("220000"), "Thuận Thảo",
                "TT-DN-QN-04", busImg4, busInstruction, now));
        tickets.add(createTicket(vipSellerId, TicketType.BUS, "Vé Xe Cabin Hoàng Gia Hà Nội đi Hà Giang (Bằng Phấn)",
                "Tuyến Hà Nội - Hà Giang Chuyến Đêm", "Bến Xe Giáp Bát, Hà Nội", "Cabin Hoàng Gia 02 - Tầng Dưới",
                now.plus(Duration.ofDays(5)), new BigDecimal("450000"), new BigDecimal("370000"), "Bằng Phấn Limousine",
                "BP-HN-HG-05", busImg1, busInstruction, now));
        tickets.add(createTicket(sellerId, TicketType.BUS, "Vé Xe Giường Nằm VIP Sài Gòn đi Nha Trang (Cúc Tùng)",
                "Tuyến Sài Gòn - Nha Trang (Khánh Hòa)", "Bến Xe Miền Đông Cũ, Bình Thạnh", "Tầng 1 - Giường 12",
                now.plus(Duration.ofDays(3)), new BigDecimal("380000"), new BigDecimal("300000"), "Cúc Tùng Limousine",
                "CT-SG-NT-06", busImg2, busInstruction, now));
        tickets.add(
                createTicket(vipSellerId, TicketType.BUS, "Vé Xe Limousine Hà Nội đi Hạ Long Quảng Ninh (Phúc Xuyên)",
                        "Tuyến Hà Nội - Bãi Cháy Hạ Long", "Bến Xe Nước Ngầm, Hà Nội", "Ghế VIP Thương Gia 04",
                        now.plus(Duration.ofDays(2)), new BigDecimal("260000"), new BigDecimal("210000"),
                        "Phúc Xuyên Luxury", "PX-HN-HL-07", busImg3, busInstruction, now));
        tickets.add(createTicket(sellerId, TicketType.BUS, "Vé Xe Khách Giường Nằm Cần Thơ đi Rạch Giá Kiên Giang",
                "Tuyến Cần Thơ - Rạch Giá (Phương Trang)", "Bến Xe Trung Tâm Cần Thơ", "Tầng 1 - Giường 06",
                now.plus(Duration.ofDays(1)), new BigDecimal("160000"), new BigDecimal("125000"), "Phương Trang FUTA",
                "FUTA-CT-RG-08", busImg4, busInstruction, now));
        tickets.add(createTicket(vipSellerId, TicketType.BUS, "Vé Xe Limousine Sài Gòn đi Mũi Né Phan Thiết (G5Car)",
                "Tuyến Sài Gòn - Mũi Né (Bình Thuận)", "Trạm Đón Sân Bay Tân Sơn Nhất", "Ghế Massage VIP 05",
                now.plus(Duration.ofDays(4)), new BigDecimal("320000"), new BigDecimal("260000"), "G5Car Limousine",
                "G5-SG-PT-09", busImg1, busInstruction, now));
        tickets.add(createTicket(sellerId, TicketType.BUS, "Vé Xe Khách VIP Huế đi Đà Nẵng (HAV Travel)",
                "Tuyến Cố Đô Huế - Đà Nẵng", "Bến Xe Phía Nam TP. Huế", "Ghế Hạng Nhất Số 02",
                now.plus(Duration.ofDays(2)), new BigDecimal("180000"), new BigDecimal("140000"), "HAV Travel",
                "HAV-HUE-DN-10", busImg2, busInstruction, now));
        tickets.add(createTicket(vipSellerId, TicketType.BUS,
                "Vé Xe Giường Đôi Buôn Ma Thuột Đắk Lắk đi Sài Gòn (Tiến Oanh)", "Tuyến Buôn Ma Thuột - Sài Gòn",
                "Bến Xe Liên Tỉnh Đắk Lắk", "Cabin Đôi Số 04", now.plus(Duration.ofDays(6)), new BigDecimal("480000"),
                new BigDecimal("390000"), "Tiến Oanh Limousine", "TO-BMT-SG-11", busImg3, busInstruction, now));
        tickets.add(createTicket(sellerId, TicketType.BUS,
                "Vé Xe Giường Nằm Cao Cấp Hà Nội đi Cố Đô Huế (Camel Travel)", "Tuyến Hà Nội - Huế (Chạy Quốc Lộ 1A)",
                "Bến Xe Giáp Bát, Hà Nội", "Tầng 1 - Giường 10", now.plus(Duration.ofDays(7)), new BigDecimal("420000"),
                new BigDecimal("340000"), "Camel Travel Bus", "CL-HN-HUE-12", busImg4, busInstruction, now));

        for (Ticket t : tickets) {
            ticketRepository.save(t);
        }
        log.info(
                "[DataInitializer] Đã nạp thành công 50 vé mẫu bổ sung (24 phim, 14 concert, 12 xe khách) có ảnh web chất lượng cao.");
    }

    /**
     * Khởi tạo 5 vé mẫu đặc thù phục vụ kiểm thử Task C15 (Kiểm tra trùng giờ &
     * Nhắc nhở 24h).
     */
    private void seedC15TestingTickets(Long buyerId, Long sellerId, Instant now) {
        boolean alreadySeeded = ticketRepository.findBySellerId(sellerId).stream()
                .anyMatch(t -> t.getTitle() != null && t.getTitle().startsWith("[TEST-C15]"));
        if (alreadySeeded) {
            log.info("[DataInitializer] Dữ liệu vé kiểm thử Task C15 ([TEST-C15]) đã tồn tại. Bỏ qua.");
            return;
        }

        log.info("[DataInitializer] Đang nạp 5 vé kiểm thử Task C15 (Trùng lịch & Nhắc nhở sự kiện 24h)...");
        List<Ticket> c15Tickets = new ArrayList<>(5);

        // Vé 1: Vé gốc đã sở hữu (TRANSFERRED) - 3h đến 6h tới
        Ticket t1 = new Ticket();
        t1.setSellerId(sellerId);
        t1.setBuyerId(buyerId);
        t1.setTicketType(TicketType.CONCERT);
        t1.setStatus(TicketStatus.TRANSFERRED);
        t1.setTitle("[TEST-C15] Vé Live Concert Mỹ Đình - Khán đài A (Đã sở hữu)");
        t1.setEventName("Anh Trai Say Hi Live Concert 2026 (Suất 1)");
        t1.setVenue("Sân Vận Động Quốc Gia Mỹ Đình, Hà Nội");
        t1.setSeatInfo("Khán đài A - Hàng 01 - Ghế 10");
        t1.setEventStartAt(now.plus(Duration.ofHours(3)));
        t1.setEventEndAt(now.plus(Duration.ofHours(6)));
        t1.setOriginalPrice(new BigDecimal("2000000.00"));
        t1.setSalePrice(new BigDecimal("1500000.00"));
        t1.setProviderName("Ticketbox");
        t1.setProviderOrderCode("TB-C15-TEST-01");
        t1.setTicketImageStorageKey(
                "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=800&auto=format&fit=crop&q=80");
        t1.setQrImageStorageKey("samples-qr/concert-secret-qr.png");
        t1.setTransferInstruction("[CONCERT] Vé đã bàn giao thành công. Mã QR bí mật đã mở khóa cho người mua.");
        t1.setListedAt(now.minus(Duration.ofDays(1)));
        c15Tickets.add(t1);

        // Vé 2: Vé trùng giờ (LISTED) - 4h đến 7h tới (Giao thoa 4h-6h với Vé 1)
        Ticket t2 = new Ticket();
        t2.setSellerId(sellerId);
        t2.setTicketType(TicketType.MOVIE);
        t2.setStatus(TicketStatus.LISTED);
        t2.setTitle("[TEST-C15] Vé Phim IMAX Laser CGV Landmark (Trùng giờ vé Concert)");
        t2.setEventName("Dune: Part Three (Suất Trùng Giờ)");
        t2.setVenue("CGV Vincom Center Landmark 81, TP. Hồ Chí Minh");
        t2.setSeatInfo("Phòng IMAX - Hàng H - Ghế 12");
        t2.setEventStartAt(now.plus(Duration.ofHours(4)));
        t2.setEventEndAt(now.plus(Duration.ofHours(7)));
        t2.setOriginalPrice(new BigDecimal("250000.00"));
        t2.setSalePrice(new BigDecimal("180000.00"));
        t2.setProviderName("CGV Cinemas");
        t2.setProviderOrderCode("CGV-C15-TEST-02");
        t2.setTicketImageStorageKey(
                "https://images.unsplash.com/photo-1489599849927-2ee91cede3ba?w=800&auto=format&fit=crop&q=80");
        t2.setQrImageStorageKey("samples-qr/movie-secret-qr.png");
        t2.setTransferInstruction("[MOVIE] Chuyển vé điện tử sau khi ký quỹ và khóa cọc thành công.");
        t2.setListedAt(now);
        c15Tickets.add(t2);

        // Vé 3: Vé không trùng giờ (LISTED) - 28h đến 34h tới (ngoài 24h)
        Ticket t3 = new Ticket();
        t3.setSellerId(sellerId);
        t3.setTicketType(TicketType.BUS);
        t3.setStatus(TicketStatus.LISTED);
        t3.setTitle("[TEST-C15] Vé Xe Limousine Sài Gòn - Đà Lạt (Không trùng giờ)");
        t3.setEventName("Tuyến Xe Khách Sài Gòn - Đà Lạt Chuyến Đêm");
        t3.setVenue("Bến xe Miền Đông Mới, TP. Thủ Đức, TP. Hồ Chí Minh");
        t3.setSeatInfo("Tầng 1 - Giường 05 VIP");
        t3.setEventStartAt(now.plus(Duration.ofHours(28)));
        t3.setEventEndAt(now.plus(Duration.ofHours(34)));
        t3.setOriginalPrice(new BigDecimal("350000.00"));
        t3.setSalePrice(new BigDecimal("280000.00"));
        t3.setProviderName("Phương Trang FUTA");
        t3.setProviderOrderCode("FUTA-C15-TEST-03");
        t3.setTicketImageStorageKey(
                "https://images.unsplash.com/photo-1544620347-c4fd4a3d5957?w=800&auto=format&fit=crop&q=80");
        t3.setQrImageStorageKey("samples-qr/bus-secret-qr.png");
        t3.setTransferInstruction("[BUS] Xuất trình mã vé điện tử khi lên xe.");
        t3.setListedAt(now);
        c15Tickets.add(t3);

        // Vé 4: Vé chờ Cron tự động nhắc 24h (TRANSFERRED) - 8h đến 11h tới
        // (reminded24hAt = null)
        Ticket t4 = new Ticket();
        t4.setSellerId(sellerId);
        t4.setBuyerId(buyerId);
        t4.setTicketType(TicketType.CONCERT);
        t4.setStatus(TicketStatus.TRANSFERRED);
        t4.setTitle("[TEST-C15] Vé Nhạc Kịch Mùa Thu (Chờ Cron tự động nhắc 24h)");
        t4.setEventName("Đêm Nhạc Kịch Giai Điệu Mùa Thu 2026");
        t4.setVenue("Nhà Hát Lớn Hà Nội, Tràng Tiền, Hoàn Kiếm");
        t4.setSeatInfo("Tầng 1 - Hàng D - Ghế 08");
        t4.setEventStartAt(now.plus(Duration.ofHours(8)));
        t4.setEventEndAt(now.plus(Duration.ofHours(11)));
        t4.setOriginalPrice(new BigDecimal("1200000.00"));
        t4.setSalePrice(new BigDecimal("900000.00"));
        t4.setProviderName("Ticketbox");
        t4.setProviderOrderCode("TB-C15-TEST-04");
        t4.setTicketImageStorageKey(
                "https://images.unsplash.com/photo-1501386761578-eac5c94b800a?w=800&auto=format&fit=crop&q=80");
        t4.setQrImageStorageKey("samples-qr/concert-secret-qr.png");
        t4.setTransferInstruction("[CONCERT] Vé đã chuyển giao. Sắp đến giờ diễn ra sự kiện.");
        t4.setListedAt(now.minus(Duration.ofDays(2)));
        c15Tickets.add(t4);

        // Vé 5: Vé test Mua cận giờ (LISTED) - 10h đến 13h tới
        Ticket t5 = new Ticket();
        t5.setSellerId(sellerId);
        t5.setTicketType(TicketType.MOVIE);
        t5.setStatus(TicketStatus.LISTED);
        t5.setTitle("[TEST-C15] Vé Phim Suất Chiếu Đặc Biệt (Test Mua Cận Giờ)");
        t5.setEventName("Oppenheimer Tái Chiếu (Cận Giờ)");
        t5.setVenue("CGV Metropolis Liễu Giai, Hà Nội");
        t5.setSeatInfo("Phòng 3 - Hàng F - Ghế 09");
        t5.setEventStartAt(now.plus(Duration.ofHours(10)));
        t5.setEventEndAt(now.plus(Duration.ofHours(13)));
        t5.setOriginalPrice(new BigDecimal("180000.00"));
        t5.setSalePrice(new BigDecimal("130000.00"));
        t5.setProviderName("CGV Cinemas");
        t5.setProviderOrderCode("CGV-C15-TEST-05");
        t5.setTicketImageStorageKey(
                "https://images.unsplash.com/photo-1517604931442-7e0c8ed2963c?w=800&auto=format&fit=crop&q=80");
        t5.setQrImageStorageKey("samples-qr/movie-secret-qr.png");
        t5.setTransferInstruction("[MOVIE] Vé cận giờ, vui lòng nhận vé và nộp cọc nhanh chóng.");
        t5.setListedAt(now);
        c15Tickets.add(t5);

        for (Ticket ticket : c15Tickets) {
            ticketRepository.save(ticket);
        }
        log.info("[DataInitializer] Đã nạp thành công 5 vé kiểm thử Task C15 ([TEST-C15]) vào CSDL.");
    }
}
