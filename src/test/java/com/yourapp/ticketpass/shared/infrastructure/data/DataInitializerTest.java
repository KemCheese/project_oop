package com.yourapp.ticketpass.shared.infrastructure.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

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

/**
 * Bộ kiểm thử đơn vị cho {@link DataInitializer}.
 *
 * <p>Xác thực các quy tắc nghiệp vụ quan trọng:</p>
 * <ul>
 *   <li>Tính Idempotent: chỉ chạy khi dữ liệu chưa tồn tại; bỏ qua an toàn nếu đã có admin.</li>
 *   <li>Đầy đủ các tài khoản: Admin, Seller, Buyer, VIP Seller với quyền và trạng thái chuẩn.</li>
 *   <li>Mật khẩu luôn được băm qua {@link PasswordEncoder}.</li>
 *   <li>Tạo đủ 3 vé mẫu ở trạng thái {@link TicketStatus#LISTED} thuộc 3 loại CONCERT, MOVIE, BUS.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Kiểm thử thành phần DataInitializer")
class DataInitializerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private ReputationRepository reputationRepository;

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private DataInitializer dataInitializer;

    @Captor
    private ArgumentCaptor<User> userCaptor;

    @Captor
    private ArgumentCaptor<UserReputationScore> reputationCaptor;

    @Captor
    private ArgumentCaptor<Ticket> ticketCaptor;

    @Nested
    @DisplayName("Kiểm thử tính Idempotent (Chống chạy lặp)")
    class IdempotencyTests {

        @Test
        @DisplayName("Khi admin@test.com đã tồn tại trong CSDL -> Bỏ qua toàn bộ quá trình seed")
        void shouldSkipSeeding_whenAdminAlreadyExists() {
            // Arrange
            when(userRepository.existsByEmail("admin@test.com")).thenReturn(true);

            // Act
            dataInitializer.run();

            // Assert
            verify(userRepository, never()).save(any(User.class));
            verify(reputationRepository, never()).save(any(UserReputationScore.class));
            verify(ticketRepository, never()).save(any(Ticket.class));
            verify(passwordEncoder, never()).encode(anyString());
        }
    }

    @Nested
    @DisplayName("Kiểm thử khởi tạo dữ liệu mẫu khi CSDL trống")
    class InitialSeedingTests {

        @BeforeEach
        void setUp() {
            when(userRepository.existsByEmail("admin@test.com")).thenReturn(false);
            when(passwordEncoder.encode(DataInitializer.DEFAULT_PASSWORD)).thenReturn("$2a$10$dummyHashBCryptForTesting");

            // Mock trả về User có ID tự tăng tuần tự 1, 2, 3, 4
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
                User u = invocation.getArgument(0);
                if ("admin@test.com".equals(u.getEmail())) u.setId(1L);
                else if ("seller@test.com".equals(u.getEmail())) u.setId(2L);
                else if ("buyer@test.com".equals(u.getEmail())) u.setId(3L);
                else if ("vip_seller@test.com".equals(u.getEmail())) u.setId(4L);
                return u;
            });
        }

        @Test
        @DisplayName("Khởi tạo đúng và đủ 4 tài khoản người dùng với thông tin chuẩn")
        void shouldCreateFourUsersWithCorrectAttributes() {
            // Act
            dataInitializer.run();

            // Assert
            verify(userRepository, times(4)).save(userCaptor.capture());
            List<User> savedUsers = userCaptor.getAllValues();
            assertThat(savedUsers).hasSize(4);

            // 1. Kiểm tra Admin
            User admin = savedUsers.stream()
                    .filter(u -> "admin@test.com".equals(u.getEmail()))
                    .findFirst().orElseThrow();
            assertThat(admin.getRole()).isEqualTo(UserRole.ADMIN);
            assertThat(admin.getStatus()).isEqualTo(UserStatus.ACTIVE);
            assertThat(admin.getPasswordHash()).isEqualTo("$2a$10$dummyHashBCryptForTesting");
            assertThat(admin.isEmailVerified()).isTrue();
            assertThat(admin.isPhoneVerified()).isTrue();
            assertThat(admin.isIdentityVerified()).isTrue();

            // 2. Kiểm tra Seller
            User seller = savedUsers.stream()
                    .filter(u -> "seller@test.com".equals(u.getEmail()))
                    .findFirst().orElseThrow();
            assertThat(seller.getRole()).isEqualTo(UserRole.USER);
            assertThat(seller.getStatus()).isEqualTo(UserStatus.ACTIVE);
            assertThat(seller.getBankName()).isEqualTo("Vietcombank");
            assertThat(seller.getBankAccountNumber()).isEqualTo("0071001234567");
            assertThat(seller.getBankAccountHolder()).isEqualTo("NGUYEN VAN BAN");

            // 3. Kiểm tra Buyer
            User buyer = savedUsers.stream()
                    .filter(u -> "buyer@test.com".equals(u.getEmail()))
                    .findFirst().orElseThrow();
            assertThat(buyer.getRole()).isEqualTo(UserRole.USER);
            assertThat(buyer.getStatus()).isEqualTo(UserStatus.ACTIVE);
            assertThat(buyer.getBankName()).isEqualTo("Techcombank");

            // 4. Kiểm tra VIP Seller
            User vipSeller = savedUsers.stream()
                    .filter(u -> "vip_seller@test.com".equals(u.getEmail()))
                    .findFirst().orElseThrow();
            assertThat(vipSeller.getRole()).isEqualTo(UserRole.USER);
            assertThat(vipSeller.getStatus()).isEqualTo(UserStatus.ACTIVE);
            assertThat(vipSeller.getBankName()).isEqualTo("MBBank");
        }

        @Test
        @DisplayName("Khởi tạo đúng 4 bản ghi điểm uy tín tương ứng cho từng người dùng")
        void shouldCreateReputationScoresForUsers() {
            // Act
            dataInitializer.run();

            // Assert
            verify(reputationRepository, times(4)).save(reputationCaptor.capture());
            List<UserReputationScore> scores = reputationCaptor.getAllValues();
            assertThat(scores).hasSize(4);

            // VIP Seller phải có 25 giao dịch thành công và GMV 50.000.000
            UserReputationScore vipScore = scores.stream()
                    .filter(s -> Long.valueOf(4L).equals(s.getUserId()))
                    .findFirst().orElseThrow();
            assertThat(vipScore.getScore()).isEqualByComparingTo(new BigDecimal("100.00"));
            assertThat(vipScore.getTotalTransactions()).isEqualTo(25);
            assertThat(vipScore.getSuccessfulTransactions()).isEqualTo(25);
            assertThat(vipScore.getTotalGmv()).isEqualByComparingTo(new BigDecimal("50000000.00"));
            assertThat(vipScore.getLastTransactionAt()).isNotNull();

            // Admin có 0 giao dịch
            UserReputationScore adminScore = scores.stream()
                    .filter(s -> Long.valueOf(1L).equals(s.getUserId()))
                    .findFirst().orElseThrow();
            assertThat(adminScore.getTotalTransactions()).isZero();
        }

        @Test
        @DisplayName("Khởi tạo 53 vé mẫu ở trạng thái LISTED (CONCERT, MOVIE, BUS) với giá bán hợp lệ")
        void shouldCreateThreeListedTickets() {
            // Act
            dataInitializer.run();

            // Assert
            verify(ticketRepository, times(53)).save(ticketCaptor.capture());
            List<Ticket> tickets = ticketCaptor.getAllValues();
            assertThat(tickets).hasSize(53);

            // Mọi vé đều phải ở trạng thái LISTED
            assertThat(tickets).allMatch(t -> t.getStatus() == TicketStatus.LISTED);

            // Đếm số lượng từng loại vé (3 vé cơ sở + 50 vé test phân trang: 24 phim, 14 concert, 12 xe khách)
            long movieCount = tickets.stream().filter(t -> t.getTicketType() == TicketType.MOVIE).count();
            long concertCount = tickets.stream().filter(t -> t.getTicketType() == TicketType.CONCERT).count();
            long busCount = tickets.stream().filter(t -> t.getTicketType() == TicketType.BUS).count();

            assertThat(movieCount).isEqualTo(25); // 1 + 24
            assertThat(concertCount).isEqualTo(15); // 1 + 14
            assertThat(busCount).isEqualTo(13); // 1 + 12

            // Vé Concert (2tr)
            Ticket concert = tickets.stream()
                    .filter(t -> t.getTicketType() == TicketType.CONCERT)
                    .findFirst().orElseThrow();
            assertThat(concert.getSellerId()).isEqualTo(2L); // seller@test.com
            assertThat(concert.getSalePrice()).isEqualByComparingTo(new BigDecimal("2000000.00"));
            assertThat(concert.getOriginalPrice()).isEqualByComparingTo(new BigDecimal("2500000.00"));
            assertThat(concert.getVenue()).contains("Mỹ Đình");
            assertThat(concert.getTransferInstruction()).isNotBlank();

            // Vé Movie (200k)
            Ticket movie = tickets.stream()
                    .filter(t -> t.getTicketType() == TicketType.MOVIE)
                    .findFirst().orElseThrow();
            assertThat(movie.getSellerId()).isEqualTo(2L);
            assertThat(movie.getSalePrice()).isEqualByComparingTo(new BigDecimal("200000.00"));
            assertThat(movie.getVenue()).contains("Landmark 81");

            // Vé Bus (300k) do VIP Seller bán
            Ticket bus = tickets.stream()
                    .filter(t -> t.getTicketType() == TicketType.BUS)
                    .findFirst().orElseThrow();
            assertThat(bus.getSellerId()).isEqualTo(4L); // vip_seller@test.com
            assertThat(bus.getSalePrice()).isEqualByComparingTo(new BigDecimal("300000.00"));
            assertThat(bus.getVenue()).contains("Miền Đông");
        }
    }
}
