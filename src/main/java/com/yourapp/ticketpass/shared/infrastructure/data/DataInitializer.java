package com.yourapp.ticketpass.shared.infrastructure.data;

import com.yourapp.ticketpass.dispute.domain.Dispute;
import com.yourapp.ticketpass.dispute.domain.DisputeEvidence;
import com.yourapp.ticketpass.dispute.domain.DisputeEvidenceRepository;
import com.yourapp.ticketpass.dispute.domain.DisputeEvidenceType;
import com.yourapp.ticketpass.dispute.domain.DisputeReason;
import com.yourapp.ticketpass.dispute.domain.DisputeRepository;
import com.yourapp.ticketpass.dispute.domain.DisputeStatus;
import com.yourapp.ticketpass.payment.domain.Deposit;
import com.yourapp.ticketpass.payment.domain.DepositRepository;
import com.yourapp.ticketpass.payment.domain.DepositStatus;
import com.yourapp.ticketpass.payment.domain.EscrowStatus;
import com.yourapp.ticketpass.payment.domain.EscrowTransaction;
import com.yourapp.ticketpass.payment.domain.EscrowTransactionRepository;
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
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@Profile("!prod")
@Order(10)
public class DataInitializer implements CommandLineRunner {

    public static final String DEFAULT_PASSWORD = "Password123@";

    private static final String IMG_MOVIE_1 =
            "https://images.unsplash.com/photo-1489599849927-2ee91cede3ba?w=800&auto=format&fit=crop&q=80";
    private static final String IMG_MOVIE_2 =
            "https://images.unsplash.com/photo-1517604931442-7e0c8ed2963c?w=800&auto=format&fit=crop&q=80";
    private static final String IMG_MOVIE_3 =
            "https://images.unsplash.com/photo-1536440136628-849c177e76a1?w=800&auto=format&fit=crop&q=80";
    private static final String IMG_CONCERT_1 =
            "https://images.unsplash.com/photo-1540039155733-5bb30b53aa14?w=800&auto=format&fit=crop&q=80";
    private static final String IMG_CONCERT_2 =
            "https://images.unsplash.com/photo-1470229722913-7c0e2dbbafd3?w=800&auto=format&fit=crop&q=80";
    private static final String IMG_CONCERT_3 =
            "https://images.unsplash.com/photo-1501386761578-eac5c94b800a?w=800&auto=format&fit=crop&q=80";
    private static final String IMG_BUS_1 =
            "https://images.unsplash.com/photo-1544620347-c4fd4a3d5957?w=800&auto=format&fit=crop&q=80";
    private static final String IMG_QR_DEMO =
            "https://images.unsplash.com/photo-1595079676339-1534801ad6cf?w=600&auto=format&fit=crop&q=80";

    private final UserRepository userRepository;
    private final ReputationRepository reputationRepository;
    private final TicketRepository ticketRepository;
    private final EscrowTransactionRepository escrowTransactionRepository;
    private final DepositRepository depositRepository;
    private final DisputeRepository disputeRepository;
    private final DisputeEvidenceRepository disputeEvidenceRepository;
    private final PasswordEncoder passwordEncoder;
    private final WalletService walletService;

    @Autowired
    public DataInitializer(UserRepository userRepository,
                           ReputationRepository reputationRepository,
                           TicketRepository ticketRepository,
                           EscrowTransactionRepository escrowTransactionRepository,
                           DepositRepository depositRepository,
                           DisputeRepository disputeRepository,
                           DisputeEvidenceRepository disputeEvidenceRepository,
                           PasswordEncoder passwordEncoder,
                           WalletService walletService) {
        this.userRepository = userRepository;
        this.reputationRepository = reputationRepository;
        this.ticketRepository = ticketRepository;
        this.escrowTransactionRepository = escrowTransactionRepository;
        this.depositRepository = depositRepository;
        this.disputeRepository = disputeRepository;
        this.disputeEvidenceRepository = disputeEvidenceRepository;
        this.passwordEncoder = passwordEncoder;
        this.walletService = walletService;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (userRepository.existsByEmail("admin@test.com")) {
            log.info("[DataInitializer] Dữ liệu mẫu tối thiểu đã tồn tại. Bỏ qua quá trình seed.");
            return;
        }

        log.info("[DataInitializer] Bắt đầu khởi tạo dữ liệu mẫu (6 tài khoản + 26 vé)...");
        String encodedPassword = passwordEncoder.encode(DEFAULT_PASSWORD);
        Instant now = Instant.now();

        User admin = userRepository.save(createUser(
                "admin@test.com", encodedPassword, "Quản trị viên TicketPass", "0900000001",
                UserRole.ADMIN, UserStatus.ACTIVE, null, null, null));
        createReputation(admin.getId(), new BigDecimal("5.00"), 0, 0, 0, BigDecimal.ZERO, null);
        walletService.getOrCreateWallet(admin.getId());

        User seller = userRepository.save(createUser(
                "seller@test.com", encodedPassword, "Nguyễn Văn Bán", "0901234567",
                UserRole.USER, UserStatus.ACTIVE, "Vietcombank", "0071001234567", "NGUYEN VAN BAN"));
        createReputation(seller.getId(), new BigDecimal("4.00"), 1, 1, 0,
                new BigDecimal("1200000.00"), now.minus(Duration.ofDays(2)));
        walletService.topUp(seller.getId(), new BigDecimal("2000000.00"), "Số dư ban đầu phục vụ nộp cọc bán vé");

        User buyer = userRepository.save(createUser(
                "buyer@test.com", encodedPassword, "Trần Thị Mua", "0912345678",
                UserRole.USER, UserStatus.ACTIVE, "Techcombank", "19036543210987", "TRAN THI MUA"));
        createReputation(buyer.getId(), new BigDecimal("3.00"), 0, 0, 0,
                BigDecimal.ZERO, null);
        walletService.topUp(buyer.getId(), new BigDecimal("5000000.00"), "Số dư ban đầu phục vụ mua vé ký quỹ");

        User vipSeller = userRepository.save(createUser(
                "vip_seller@test.com", encodedPassword, "Lê Hoàng Uy Tín", "0988888888",
                UserRole.USER, UserStatus.ACTIVE, "MBBank", "0888888888", "LE HOANG UY TIN"));
        createReputation(vipSeller.getId(), new BigDecimal("5.00"), 12, 12, 0,
                new BigDecimal("25000000.00"), now.minus(Duration.ofDays(2)));
        walletService.topUp(vipSeller.getId(), new BigDecimal("10000000.00"), "Số dư ban đầu người bán VIP");

        User brokeBuyer = userRepository.save(createUser(
                "broke_buyer@test.com", encodedPassword, "Lê Văn Hết Tiền", "0911000000",
                UserRole.USER, UserStatus.ACTIVE, null, null, null));
        createReputation(brokeBuyer.getId(), new BigDecimal("3.00"), 0, 0, 0, BigDecimal.ZERO, null);
        walletService.getOrCreateWallet(brokeBuyer.getId());

        User riskyUser = userRepository.save(createUser(
                "risky_user@test.com", encodedPassword, "Phạm Văn Rủi Ro", "0933111222",
                UserRole.USER, UserStatus.ACTIVE, "VPBank", "123456789012", "PHAM VAN RUI RO"));
        createReputation(riskyUser.getId(), new BigDecimal("1.00"), 4, 2, 2,
                new BigDecimal("400000.00"), now.minus(Duration.ofDays(3)));
        walletService.topUp(riskyUser.getId(), new BigDecimal("500000.00"), "Số dư kiểm thử cảnh báo & thanh lý ví");

        Ticket t1 = buildTicket(
                seller.getId(), buyer.getId(), TicketType.MOVIE, TicketStatus.DISPUTE_UNDER_REVIEW,
                "Vé Xem Phim 'Mai' - Phòng IMAX (Đang tranh chấp mẫu)",
                "Mai - Suất chiếu đặc biệt",
                "CGV Vincom Bà Triệu, Hà Nội", "Phòng 3 - Hàng G - Ghế 09",
                now.plus(Duration.ofDays(1)), Duration.ofHours(2),
                new BigDecimal("280000.00"), new BigDecimal("250000.00"),
                "CGV Cinemas", "CGV-DISPUTE-001", IMG_MOVIE_1, now.minus(Duration.ofDays(1)));
        t1.setEscrowedAt(now.minus(Duration.ofHours(10)));
        t1.setTransferredAt(now.minus(Duration.ofHours(9)));
        t1.setReceivedAt(now.minus(Duration.ofHours(8)));
        t1.setDisputeWindowClosesAt(t1.getEventStartAt().plus(Duration.ofMinutes(30)));
        t1 = ticketRepository.save(t1);
        EscrowTransaction e1 = createHeldEscrow(t1, buyer.getId(), seller.getId(), "ESCROW-SEED-1", now.minus(Duration.ofHours(10)));
        Deposit d1 = createLockedDeposit(t1, seller.getId(), now.minus(Duration.ofHours(9)));
        t1.setEscrowTransactionId(e1.getId());
        t1.setDepositId(d1.getId());
        ticketRepository.save(t1);
        seedSampleDispute(t1, buyer.getId(), seller.getId(), e1.getId(), d1.getId(), now);

        Instant overlapWindowStart = now.plus(Duration.ofDays(2));
        Ticket t2 = buildTicket(
                seller.getId(), null, TicketType.MOVIE, TicketStatus.LISTED,
                "Vé Xem Phim 'Dune: Part Three' - Suất Chiếu IMAX Laser",
                "Dune: Part Three (Khởi chiếu đặc biệt)",
                "CGV Vincom Center Landmark 81, TP. Hồ Chí Minh", "Phòng IMAX - Hàng H - Ghế 15",
                overlapWindowStart, Duration.ofHours(2),
                new BigDecimal("250000.00"), new BigDecimal("180000.00"),
                "CGV Cinemas", "CGV-DUNE3-002", IMG_MOVIE_2, now.minus(Duration.ofMinutes(30)));
        ticketRepository.save(t2);

        Ticket t3 = buildTicket(
                vipSeller.getId(), null, TicketType.CONCERT, TicketStatus.LISTED,
                "Vé Concert 'Anh Trai Vượt Ngàn Chông Gai 2026' - Hạng VVIP",
                "Anh Trai Vượt Ngàn Chông Gai Live Concert 2026",
                "Sân vận động Quốc gia Mỹ Đình, Hà Nội", "Khán đài A - VVIP 01 - Ghế 05",
                now.plus(Duration.ofDays(5)), Duration.ofHours(3),
                new BigDecimal("9500000.00"), new BigDecimal("8500000.00"),
                "Ticketbox", "TB-CONCERT-VVIP-003", IMG_CONCERT_2, now.minus(Duration.ofMinutes(20)));
        ticketRepository.save(t3);

        Ticket t4 = buildTicket(
                seller.getId(), null, TicketType.BUS, TicketStatus.LISTED,
                "Vé Xe Giường Nằm VIP Limousine Sài Gòn đi Đà Lạt",
                "Tuyến Xe Khách Sài Gòn - Đà Lạt Chuyến Đêm",
                "Bến xe Miền Đông Mới, TP. Thủ Đức, TP. Hồ Chí Minh", "Tầng 1 - Giường 06 (Khoang VIP)",
                overlapWindowStart.plus(Duration.ofMinutes(30)), Duration.ofHours(4),
                new BigDecimal("380000.00"), new BigDecimal("320000.00"),
                "Phương Trang FUTA Bus Lines", "FUTA-SGN-DLA-004", IMG_BUS_1, now.minus(Duration.ofMinutes(10)));
        ticketRepository.save(t4);

        Ticket t5 = buildTicket(
                seller.getId(), buyer.getId(), TicketType.MOVIE, TicketStatus.AWAITING_USE,
                "Vé Xem Phim 'Avengers: Doomsday' - Suất Chiếu Sớm (Đã mở mã QR)",
                "Avengers: Doomsday Premiere",
                "CGV Metropolis Liễu Giai, Hà Nội", "Phòng IMAX - Hàng E - Ghế 12",
                now.plus(Duration.ofDays(4)), Duration.ofHours(2),
                new BigDecimal("250000.00"), new BigDecimal("200000.00"),
                "CGV Cinemas", "CGV-AVEN-005", IMG_MOVIE_3, now.minus(Duration.ofHours(3)));
        t5.setEscrowedAt(now.minus(Duration.ofMinutes(15)));
        t5.setTransferredAt(now.minus(Duration.ofMinutes(5)));
        t5.setReceivedAt(now.minus(Duration.ofMinutes(5)));
        t5.setDisputeWindowClosesAt(t5.getEventStartAt().plus(Duration.ofMinutes(30)));
        t5 = ticketRepository.save(t5);
        EscrowTransaction e5 = createHeldEscrow(t5, buyer.getId(), seller.getId(), "ESCROW-SEED-5", now.minus(Duration.ofMinutes(15)));
        Deposit d5 = createLockedDeposit(t5, seller.getId(), now.minus(Duration.ofMinutes(5)));
        t5.setEscrowTransactionId(e5.getId());
        t5.setDepositId(d5.getId());
        ticketRepository.save(t5);

        Instant completedAt = now.minus(Duration.ofDays(2));
        Ticket t6 = buildTicket(
                vipSeller.getId(), seller.getId(), TicketType.CONCERT, TicketStatus.AUTO_COMPLETE,
                "Vé Concert Acoustic Mùa Thu 2026 - Hàng Ghế Đầu (Đã hoàn tất)",
                "Acoustic Mùa Thu Live Show 2026",
                "Cung Văn Hóa Hữu Nghị Việt Xô, Hà Nội", "Khu A - Hàng 02 - Ghế 08",
                completedAt.minus(Duration.ofHours(3)), Duration.ofHours(3),
                new BigDecimal("1500000.00"), new BigDecimal("1200000.00"),
                "Ticketbox", "TB-ACOUSTIC-006", IMG_CONCERT_3, completedAt.minus(Duration.ofDays(2)));
        t6.setEscrowedAt(completedAt.minus(Duration.ofDays(1)));
        t6.setTransferredAt(completedAt.minus(Duration.ofHours(20)));
        t6.setReceivedAt(completedAt.minus(Duration.ofHours(19)));
        t6.setDisputeWindowClosesAt(t6.getEventStartAt().plus(Duration.ofMinutes(30)));
        t6.setCompletedAt(completedAt);
        t6 = ticketRepository.save(t6);
        EscrowTransaction e6 = createReleasedEscrow(t6, seller.getId(), vipSeller.getId(), completedAt);
        Deposit d6 = createRefundedDeposit(t6, vipSeller.getId(), completedAt);
        t6.setEscrowTransactionId(e6.getId());
        t6.setDepositId(d6.getId());
        ticketRepository.save(t6);

        seedAdditionalListedTickets(seller.getId(), vipSeller.getId(), now);

        log.info("[DataInitializer] Hoàn tất khởi tạo 6 tài khoản & 26 vé phục vụ kiểm thử.");
    }

    private void seedAdditionalListedTickets(Long sellerId, Long vipSellerId, Instant now) {
        ticketRepository.save(buildTicket(
                sellerId, null, TicketType.MOVIE, TicketStatus.LISTED,
                "Vé Xem Phim 'Lật Mặt 8: Vòng Tay Nắng' - Suất Tối Cuối Tuần",
                "Lật Mặt 8: Vòng Tay Nắng",
                "CGV Aeon Mall Long Biên, Hà Nội", "Phòng 5 - Hàng F - Ghế 08",
                now.plus(Duration.ofDays(3)), Duration.ofHours(2),
                new BigDecimal("140000.00"), new BigDecimal("110000.00"),
                "CGV Cinemas", "CGV-LM8-007", IMG_MOVIE_1, now.minus(Duration.ofMinutes(45))));

        ticketRepository.save(buildTicket(
                vipSellerId, null, TicketType.MOVIE, TicketStatus.LISTED,
                "Vé Xem Phim 'Đào, Phở và Piano' - Suất Chiếu Đặc Biệt 2D",
                "Đào, Phở và Piano",
                "Trung tâm Chiếu phim Quốc gia, Hà Nội", "Phòng 1 - Hàng G - Ghế 11",
                now.plus(Duration.ofDays(4)), Duration.ofHours(2),
                new BigDecimal("100000.00"), new BigDecimal("80000.00"),
                "NCC Cinema", "NCC-DPP-008", IMG_MOVIE_2, now.minus(Duration.ofMinutes(50))));

        ticketRepository.save(buildTicket(
                sellerId, null, TicketType.MOVIE, TicketStatus.LISTED,
                "Vé Xem Phim 'Avatar: Fire and Ash' - Phòng IMAX 3D",
                "Avatar: Fire and Ash IMAX 3D",
                "CGV SC VivoCity, Quận 7, TP. Hồ Chí Minh", "Phòng IMAX - Hàng J - Ghế 14",
                now.plus(Duration.ofDays(5)), Duration.ofHours(3),
                new BigDecimal("310000.00"), new BigDecimal("260000.00"),
                "CGV Cinemas", "CGV-AVA3-009", IMG_MOVIE_3, now.minus(Duration.ofMinutes(55))));

        ticketRepository.save(buildTicket(
                vipSellerId, null, TicketType.MOVIE, TicketStatus.LISTED,
                "Vé Xem Phim 'Thám Tử Lừng Danh Conan' - Suất Lồng Tiếng",
                "Thám Tử Lừng Danh Conan: Ngôi Sao 5 Cánh",
                "Lotte Cinema West Lake, Tây Hồ, Hà Nội", "Phòng 4 - Hàng E - Ghế 07",
                now.plus(Duration.ofDays(6)), Duration.ofHours(2),
                new BigDecimal("135000.00"), new BigDecimal("105000.00"),
                "Lotte Cinema", "LTC-CONAN-010", IMG_MOVIE_1, now.minus(Duration.ofMinutes(60))));

        ticketRepository.save(buildTicket(
                sellerId, null, TicketType.MOVIE, TicketStatus.LISTED,
                "Vé Xem Phim 'Spider-Man: Brand New Day' - Phòng 4DX",
                "Spider-Man: Brand New Day 4DX",
                "CGV Giga Mall Thủ Đức, TP. Hồ Chí Minh", "Phòng 4DX - Hàng D - Ghế 05",
                now.plus(Duration.ofDays(7)), Duration.ofHours(2),
                new BigDecimal("260000.00"), new BigDecimal("210000.00"),
                "CGV Cinemas", "CGV-SPIDEY-011", IMG_MOVIE_2, now.minus(Duration.ofMinutes(65))));

        ticketRepository.save(buildTicket(
                vipSellerId, null, TicketType.MOVIE, TicketStatus.LISTED,
                "Vé Xem Phim 'Inside Out 3' - Ghế Đôi Sweetbox",
                "Inside Out 3 (2D Phụ Đề)",
                "Galaxy Nguyễn Du, Quận 1, TP. Hồ Chí Minh", "Phòng 2 - Hàng L - Sweetbox 03",
                now.plus(Duration.ofDays(8)), Duration.ofHours(2),
                new BigDecimal("240000.00"), new BigDecimal("195000.00"),
                "Galaxy Cinema", "GLX-IO3-012", IMG_MOVIE_3, now.minus(Duration.ofMinutes(70))));

        ticketRepository.save(buildTicket(
                sellerId, null, TicketType.MOVIE, TicketStatus.LISTED,
                "Vé Xem Phim 'Interstellar' - Suất Chiếu Lại IMAX",
                "Interstellar Anniversary IMAX",
                "BHD Star Bitexco Icon68, Quận 1, TP. Hồ Chí Minh", "Phòng 1 - Hàng H - Ghế 10",
                now.plus(Duration.ofDays(9)), Duration.ofHours(3),
                new BigDecimal("220000.00"), new BigDecimal("175000.00"),
                "BHD Star Cineplex", "BHD-INTER-013", IMG_MOVIE_1, now.minus(Duration.ofMinutes(75))));

        ticketRepository.save(buildTicket(
                vipSellerId, null, TicketType.CONCERT, TicketStatus.LISTED,
                "Vé Concert 'Hà Anh Tuấn - Sketch A Rose' Hạng Hạnh Phúc",
                "Hà Anh Tuấn Live Concert: Sketch A Rose",
                "Nhà hát Hòa Bình, Quận 10, TP. Hồ Chí Minh", "Khu A - Hàng 05 - Ghế 12",
                now.plus(Duration.ofDays(6)), Duration.ofHours(3),
                new BigDecimal("3800000.00"), new BigDecimal("3200000.00"),
                "Ticketbox", "TB-HAT-014", IMG_CONCERT_1, now.minus(Duration.ofMinutes(80))));

        ticketRepository.save(buildTicket(
                sellerId, null, TicketType.CONCERT, TicketStatus.LISTED,
                "Vé Live Concert 'Show Của Đen' - Khu Đứng Fanzone A",
                "Show Của Đen 2026 Hà Nội",
                "Cung Điền Kinh Mỹ Đình, Hà Nội", "Fanzone A - Khu Đứng Cổng 2",
                now.plus(Duration.ofDays(7)), Duration.ofHours(3),
                new BigDecimal("1500000.00"), new BigDecimal("1250000.00"),
                "Ticketbox", "TB-DEN-015", IMG_CONCERT_2, now.minus(Duration.ofMinutes(85))));

        ticketRepository.save(buildTicket(
                vipSellerId, null, TicketType.CONCERT, TicketStatus.LISTED,
                "Vé Đêm Nhạc 'Mỹ Tâm - My Soul 1981' Hạng VIP Biển Xanh",
                "Mỹ Tâm Live Show: My Soul 1981",
                "Bãi biển Hồ Tràm, Bà Rịa - Vũng Tàu", "Khu VIP - Hàng 03 - Ghế 18",
                now.plus(Duration.ofDays(10)), Duration.ofHours(3),
                new BigDecimal("4500000.00"), new BigDecimal("3900000.00"),
                "Ticketbox", "TB-MYTAM-016", IMG_CONCERT_3, now.minus(Duration.ofMinutes(90))));

        ticketRepository.save(buildTicket(
                sellerId, null, TicketType.CONCERT, TicketStatus.LISTED,
                "Vé Lễ Hội Âm Nhạc 'GENfest 2026' - Vé GA 1 Ngày",
                "GENfest Music Festival 2026",
                "The Global City, TP. Thủ Đức, TP. Hồ Chí Minh", "Khu GA Standing - Cổng B",
                now.plus(Duration.ofDays(11)), Duration.ofHours(4),
                new BigDecimal("950000.00"), new BigDecimal("780000.00"),
                "Ticketbox", "TB-GENFEST-017", IMG_CONCERT_1, now.minus(Duration.ofMinutes(95))));

        ticketRepository.save(buildTicket(
                vipSellerId, null, TicketType.CONCERT, TicketStatus.LISTED,
                "Vé Concert 'Sơn Tùng M-TP Sky Tour' Hạng SVIP Soundcheck",
                "Sơn Tùng M-TP Sky Tour 2026",
                "Sân vận động Quân khu 7, TP. Hồ Chí Minh", "Khu SVIP - Hàng 01 - Ghế 06",
                now.plus(Duration.ofDays(12)), Duration.ofHours(3),
                new BigDecimal("6500000.00"), new BigDecimal("5800000.00"),
                "VinID Tickets", "VID-MTP-018", IMG_CONCERT_2, now.minus(Duration.ofMinutes(100))));

        ticketRepository.save(buildTicket(
                sellerId, null, TicketType.CONCERT, TicketStatus.LISTED,
                "Vé Minishow Acoustic 'Vũ. - Bảo Tàng Của Nuối Tiếc'",
                "Vũ. Live Acoustic Night",
                "Phòng trà Không Tên, Quận 1, TP. Hồ Chí Minh", "Tầng 1 - Bàn B04 - Ghế 02",
                now.plus(Duration.ofDays(8)), Duration.ofHours(3),
                new BigDecimal("850000.00"), new BigDecimal("700000.00"),
                "Ticketbox", "TB-VU-019", IMG_CONCERT_3, now.minus(Duration.ofMinutes(105))));

        ticketRepository.save(buildTicket(
                vipSellerId, null, TicketType.CONCERT, TicketStatus.LISTED,
                "Vé Concert Quốc Tế 'Coldplay Asia Encore' Hạng Platinum VIP",
                "Coldplay Music Of The Spheres Encore",
                "Sân vận động Quốc gia Mỹ Đình, Hà Nội", "Khán đài VIP Trung tâm - Hàng A - Ghế 09",
                now.plus(Duration.ofDays(15)), Duration.ofHours(3),
                new BigDecimal("12000000.00"), new BigDecimal("10800000.00"),
                "Live Nation VN", "LN-COLDPLAY-020", IMG_CONCERT_1, now.minus(Duration.ofMinutes(110))));

        ticketRepository.save(buildTicket(
                sellerId, null, TicketType.BUS, TicketStatus.LISTED,
                "Vé Xe Cabin Đôi VIP Hà Nội đi Sa Pa (Chuyến Đêm)",
                "Tuyến Xe Khách Hà Nội - Sa Pa Cabin VIP",
                "Bến xe Mỹ Đình, Hà Nội → Thị xã Sa Pa, Lào Cai", "Cabin Đôi Tầng 1 - Phòng 04",
                now.plus(Duration.ofDays(3)), Duration.ofHours(5),
                new BigDecimal("450000.00"), new BigDecimal("380000.00"),
                "Nhà xe Sao Việt", "SV-HN-SP-021", IMG_BUS_1, now.minus(Duration.ofMinutes(115))));

        ticketRepository.save(buildTicket(
                vipSellerId, null, TicketType.BUS, TicketStatus.LISTED,
                "Vé Xe Limousine 9 Chỗ Hà Nội đi Hải Phòng",
                "Tuyến Limousine Cao Tốc Hà Nội - Hải Phòng",
                "72 Trần Nhân Tông, Hà Nội → Bến xe Thượng Lý, Hải Phòng", "Ghế giữa massage - Ghế 03",
                now.plus(Duration.ofDays(4)), Duration.ofHours(2),
                new BigDecimal("250000.00"), new BigDecimal("195000.00"),
                "Hải Phòng Travel", "HPT-HN-HP-022", IMG_BUS_1, now.minus(Duration.ofMinutes(120))));

        ticketRepository.save(buildTicket(
                sellerId, null, TicketType.BUS, TicketStatus.LISTED,
                "Vé Xe Giường Nằm Cao Cấp Sài Gòn đi Nha Trang",
                "Tuyến Xe Khách Sài Gòn - Nha Trang Chuyến Tối",
                "Bến xe Miền Đông Mới, TP.HCM → Bến xe phía Nam Nha Trang", "Tầng 1 - Giường A08",
                now.plus(Duration.ofDays(5)), Duration.ofHours(6),
                new BigDecimal("340000.00"), new BigDecimal("280000.00"),
                "Phương Trang FUTA Bus Lines", "FUTA-SGN-NT-023", IMG_BUS_1, now.minus(Duration.ofMinutes(125))));

        ticketRepository.save(buildTicket(
                vipSellerId, null, TicketType.BUS, TicketStatus.LISTED,
                "Vé Xe Limousine Phòng Riêng Đà Nẵng đi Huế",
                "Tuyến Limousine Đà Nẵng - Huế Qua Hầm Hải Vân",
                "Bến xe Trung tâm Đà Nẵng → Bến xe phía Nam Huế", "Ghế VIP 02 - Khởi hành 08:30",
                now.plus(Duration.ofDays(6)), Duration.ofHours(3),
                new BigDecimal("220000.00"), new BigDecimal("170000.00"),
                "Hoàng Đức Limousine", "HD-DN-HUE-024", IMG_BUS_1, now.minus(Duration.ofMinutes(130))));

        ticketRepository.save(buildTicket(
                sellerId, null, TicketType.BUS, TicketStatus.LISTED,
                "Vé Xe Giường Nằm Sài Gòn đi Cần Thơ (Cao Tốc)",
                "Tuyến Xe Khách Sài Gòn - Cần Thơ",
                "Bến xe Miền Tây, TP.HCM → Bến xe Trung tâm Cần Thơ", "Tầng 1 - Giường B05",
                now.plus(Duration.ofDays(7)), Duration.ofHours(4),
                new BigDecimal("200000.00"), new BigDecimal("155000.00"),
                "Phương Trang FUTA Bus Lines", "FUTA-SGN-CT-025", IMG_BUS_1, now.minus(Duration.ofMinutes(135))));

        ticketRepository.save(buildTicket(
                vipSellerId, null, TicketType.BUS, TicketStatus.LISTED,
                "Vé Xe Cabin VIP Hà Nội đi Đà Nẵng (Giường Phòng Đơn)",
                "Tuyến Xe Cabin VIP Hà Nội - Đà Nẵng",
                "Bến xe Nước Ngầm, Hà Nội → Bến xe Trung tâm Đà Nẵng", "Cabin Đơn Tầng 1 - Phòng 09",
                now.plus(Duration.ofDays(9)), Duration.ofHours(12),
                new BigDecimal("580000.00"), new BigDecimal("490000.00"),
                "Tân Kim Chi", "TKC-HN-DN-026", IMG_BUS_1, now.minus(Duration.ofMinutes(140))));
    }

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

    private void createReputation(Long userId, BigDecimal score, int totalTransactions,
                                  int successfulTransactions, int disputesLost,
                                  BigDecimal totalGmv, Instant lastTxAt) {
        UserReputationScore rep = new UserReputationScore();
        rep.setUserId(userId);
        rep.setScore(score);
        rep.setTotalTransactions(totalTransactions);
        rep.setSuccessfulTransactions(successfulTransactions);
        rep.setCancelledListings(0);
        rep.setDisputesOpenedAgainst(disputesLost);
        rep.setDisputesLost(disputesLost);
        rep.setDisputesWon(0);
        rep.setTotalGmv(totalGmv);
        rep.setLastTransactionAt(lastTxAt);
        reputationRepository.save(rep);
    }

    private Ticket buildTicket(Long sellerId, Long buyerId, TicketType type, TicketStatus status,
                               String title, String eventName, String venue, String seatInfo,
                               Instant eventStartAt, Duration duration,
                               BigDecimal originalPrice, BigDecimal salePrice,
                               String providerName, String orderCode, String imageUrl, Instant listedAt) {
        Ticket ticket = new Ticket();
        ticket.setSellerId(sellerId);
        ticket.setBuyerId(buyerId);
        ticket.setTicketType(type);
        ticket.setStatus(status);
        ticket.setTitle(title);
        ticket.setEventName(eventName);
        ticket.setVenue(venue);
        ticket.setSeatInfo(seatInfo);
        ticket.setEventStartAt(eventStartAt);
        ticket.setEventEndAt(eventStartAt != null ? eventStartAt.plus(duration) : null);
        ticket.setOriginalPrice(originalPrice);
        ticket.setSalePrice(salePrice);
        ticket.setProviderName(providerName);
        ticket.setProviderOrderCode(orderCode);
        ticket.setTicketImageStorageKey(imageUrl);
        ticket.setQrImageStorageKey(IMG_QR_DEMO);
        ticket.setTransferInstruction("[" + type.name() + "] Xuất trình mã QR điện tử tại cổng soát vé.");
        ticket.setListedAt(listedAt);
        return ticket;
    }

    private EscrowTransaction createHeldEscrow(Ticket ticket, Long buyerId, Long sellerId,
                                               String gatewayTxId, Instant heldAt) {
        EscrowTransaction escrow = new EscrowTransaction();
        escrow.setTicketId(ticket.getId());
        escrow.setBuyerId(buyerId);
        escrow.setSellerId(sellerId);
        escrow.setSalePrice(ticket.getSalePrice());
        escrow.setAmountHeld(ticket.getSalePrice());
        escrow.setStatus(EscrowStatus.HELD);
        escrow.setGatewayName("WALLET");
        escrow.setGatewayTransactionId(gatewayTxId);
        escrow.setHeldAt(heldAt);
        return escrowTransactionRepository.save(escrow);
    }

    private EscrowTransaction createReleasedEscrow(Ticket ticket, Long buyerId, Long sellerId, Instant releasedAt) {
        EscrowTransaction escrow = new EscrowTransaction();
        escrow.setTicketId(ticket.getId());
        escrow.setBuyerId(buyerId);
        escrow.setSellerId(sellerId);
        escrow.setSalePrice(ticket.getSalePrice());
        escrow.setAmountHeld(BigDecimal.ZERO);
        escrow.setBuyerCommission(new BigDecimal("30000.00"));
        escrow.setSellerCommission(new BigDecimal("30000.00"));
        escrow.setTotalCommission(new BigDecimal("60000.00"));
        escrow.setTierCode("UNDER_5M");
        escrow.setTaxWithheld(BigDecimal.ZERO);
        escrow.setSellerNetPayout(new BigDecimal("1170000.00"));
        escrow.setStatus(EscrowStatus.RELEASED);
        escrow.setGatewayName("WALLET");
        escrow.setGatewayTransactionId("ESCROW-SEED-7");
        escrow.setHeldAt(releasedAt.minus(Duration.ofDays(1)));
        escrow.setReleasedAt(releasedAt);
        return escrowTransactionRepository.save(escrow);
    }

    private Deposit createLockedDeposit(Ticket ticket, Long sellerId, Instant lockedAt) {
        Deposit deposit = new Deposit();
        deposit.setTicketId(ticket.getId());
        deposit.setSellerId(sellerId);
        deposit.setAmount(ticket.getSalePrice().multiply(new BigDecimal("0.50")));
        deposit.setDepositPercent(new BigDecimal("50.00"));
        deposit.setStatus(DepositStatus.LOCKED);
        deposit.setGatewayName("WALLET");
        deposit.setLockedAt(lockedAt);
        return depositRepository.save(deposit);
    }

    private Deposit createRefundedDeposit(Ticket ticket, Long sellerId, Instant refundedAt) {
        Deposit deposit = new Deposit();
        deposit.setTicketId(ticket.getId());
        deposit.setSellerId(sellerId);
        deposit.setAmount(ticket.getSalePrice().multiply(new BigDecimal("0.50")));
        deposit.setDepositPercent(new BigDecimal("50.00"));
        deposit.setStatus(DepositStatus.REFUNDED);
        deposit.setGatewayName("WALLET");
        deposit.setLockedAt(refundedAt.minus(Duration.ofDays(1)));
        deposit.setRefundedAt(refundedAt);
        return depositRepository.save(deposit);
    }

    private void seedSampleDispute(Ticket ticket, Long buyerId, Long sellerId,
                                   Long escrowId, Long depositId, Instant now) {
        Dispute dispute = new Dispute();
        dispute.setTicketId(ticket.getId());
        dispute.setBuyerId(buyerId);
        dispute.setSellerId(sellerId);
        dispute.setEscrowTransactionId(escrowId);
        dispute.setDepositId(depositId);
        dispute.setStatus(DisputeStatus.UNDER_REVIEW);
        dispute.setReason(DisputeReason.TICKET_INVALID);
        dispute.setDescription("Nhân viên rạp báo mã vé này đã được check-in trước khi tôi tới cổng soát vé.");
        dispute.setFiledAt(now.minus(Duration.ofHours(4)));
        dispute.setWindowClosesAt(ticket.getDisputeWindowClosesAt());
        dispute.setSellerResponseDeadline(now.plus(Duration.ofHours(20)));
        dispute.setSellerResponse("Tôi cam kết mua vé chính chủ từ CGV và chỉ giao cho duy nhất người mua này.");
        dispute = disputeRepository.save(dispute);

        DisputeEvidence evidence = new DisputeEvidence();
        evidence.setDisputeId(dispute.getId());
        evidence.setStorageKey(IMG_MOVIE_1);
        evidence.setEvidenceType(DisputeEvidenceType.IMAGE);
        evidence.setContentType("image/jpeg");
        evidence.setOriginalFilename("gate-check-evidence.jpg");
        evidence.setSizeBytes(120000L);
        evidence.setUploadedByUserId(buyerId);
        evidence.setUploadedAt(now.minus(Duration.ofHours(4)));
        disputeEvidenceRepository.save(evidence);
    }
}
