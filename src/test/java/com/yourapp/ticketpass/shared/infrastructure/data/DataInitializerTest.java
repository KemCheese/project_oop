package com.yourapp.ticketpass.shared.infrastructure.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yourapp.ticketpass.dispute.domain.Dispute;
import com.yourapp.ticketpass.dispute.domain.DisputeEvidence;
import com.yourapp.ticketpass.dispute.domain.DisputeEvidenceRepository;
import com.yourapp.ticketpass.dispute.domain.DisputeRepository;
import com.yourapp.ticketpass.payment.domain.Deposit;
import com.yourapp.ticketpass.payment.domain.DepositRepository;
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
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
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

@ExtendWith(MockitoExtension.class)
@DisplayName("Kiểm thử thành phần DataInitializer (Minimal Web Test Dataset)")
class DataInitializerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private ReputationRepository reputationRepository;

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private EscrowTransactionRepository escrowTransactionRepository;

    @Mock
    private DepositRepository depositRepository;

    @Mock
    private DisputeRepository disputeRepository;

    @Mock
    private DisputeEvidenceRepository disputeEvidenceRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private WalletService walletService;

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
            when(userRepository.existsByEmail("admin@test.com")).thenReturn(true);

            dataInitializer.run();

            verify(userRepository, never()).save(any(User.class));
            verify(reputationRepository, never()).save(any(UserReputationScore.class));
            verify(ticketRepository, never()).save(any(Ticket.class));
            verify(passwordEncoder, never()).encode(anyString());
        }
    }

    @Nested
    @DisplayName("Kiểm thử khởi tạo bộ dữ liệu tối thiểu (6 users + 6 tickets)")
    class InitialSeedingTests {

        @BeforeEach
        void setUp() {
            when(userRepository.existsByEmail("admin@test.com")).thenReturn(false);
            when(passwordEncoder.encode(DataInitializer.DEFAULT_PASSWORD)).thenReturn("$2a$10$dummyHashBCryptForTesting");

            AtomicLong userSeq = new AtomicLong(1);
            when(userRepository.save(any(User.class))).thenAnswer(inv -> {
                User u = inv.getArgument(0);
                if (u.getId() == null) u.setId(userSeq.getAndIncrement());
                return u;
            });

            AtomicLong ticketSeq = new AtomicLong(1);
            when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> {
                Ticket t = inv.getArgument(0);
                if (t.getId() == null) t.setId(ticketSeq.getAndIncrement());
                return t;
            });

            AtomicLong escrowSeq = new AtomicLong(1);
            when(escrowTransactionRepository.save(any(EscrowTransaction.class))).thenAnswer(inv -> {
                EscrowTransaction e = inv.getArgument(0);
                if (e.getId() == null) e.setId(escrowSeq.getAndIncrement());
                return e;
            });

            AtomicLong depositSeq = new AtomicLong(1);
            when(depositRepository.save(any(Deposit.class))).thenAnswer(inv -> {
                Deposit d = inv.getArgument(0);
                if (d.getId() == null) d.setId(depositSeq.getAndIncrement());
                return d;
            });

            AtomicLong disputeSeq = new AtomicLong(1);
            when(disputeRepository.save(any(Dispute.class))).thenAnswer(inv -> {
                Dispute d = inv.getArgument(0);
                if (d.getId() == null) d.setId(disputeSeq.getAndIncrement());
                return d;
            });
        }

        @Test
        @DisplayName("Khởi tạo đúng 6 tài khoản (Admin, Seller, Buyer, VIP Seller, Broke Buyer, Risky User)")
        void shouldCreateSixUsersWithCorrectAttributes() {
            dataInitializer.run();

            verify(userRepository, times(6)).save(userCaptor.capture());
            List<User> savedUsers = userCaptor.getAllValues();
            assertThat(savedUsers).extracting(User::getEmail).containsExactly(
                    "admin@test.com",
                    "seller@test.com",
                    "buyer@test.com",
                    "vip_seller@test.com",
                    "broke_buyer@test.com",
                    "risky_user@test.com"
            );

            User admin = savedUsers.get(0);
            assertThat(admin.getRole()).isEqualTo(UserRole.ADMIN);
            assertThat(admin.getStatus()).isEqualTo(UserStatus.ACTIVE);
            assertThat(admin.getPasswordHash()).isEqualTo("$2a$10$dummyHashBCryptForTesting");

            User riskyUser = savedUsers.get(5);
            assertThat(riskyUser.getRole()).isEqualTo(UserRole.USER);
            assertThat(riskyUser.getBankName()).isEqualTo("VPBank");
        }

        @Test
        @DisplayName("Khởi tạo đúng 6 bản ghi điểm uy tín trên thang 0-5 (5, 4, 3, 5, 3, 1)")
        void shouldCreateSixReputationScoresOnScale0To5() {
            dataInitializer.run();

            verify(reputationRepository, times(6)).save(reputationCaptor.capture());
            List<UserReputationScore> scores = reputationCaptor.getAllValues();
            assertThat(scores).extracting(UserReputationScore::getScore)
                    .usingElementComparator(BigDecimal::compareTo)
                    .containsExactly(
                            new BigDecimal("5.00"),
                            new BigDecimal("4.00"),
                            new BigDecimal("3.00"),
                            new BigDecimal("5.00"),
                            new BigDecimal("3.00"),
                            new BigDecimal("1.00")
                    );
        }

        @Test
        @DisplayName("Khởi tạo đúng 6 vé tối thiểu phủ đủ các trạng thái kiểm thử web (không sinh 50 vé rác)")
        void shouldCreateSixMinimalTicketsCoveringAllWebFlows() {
            dataInitializer.run();

            verify(ticketRepository, times(9)).save(ticketCaptor.capture());
            List<Ticket> distinctTickets = ticketCaptor.getAllValues().stream()
                    .collect(java.util.stream.Collectors.toMap(Ticket::getId, t -> t, (a, b) -> b))
                    .values().stream().toList();

            assertThat(distinctTickets).hasSize(6);
            assertThat(distinctTickets.stream().filter(t -> t.getStatus() == TicketStatus.LISTED).count()).isEqualTo(3);
            assertThat(distinctTickets.stream().filter(t -> t.getStatus() == TicketStatus.DISPUTE_UNDER_REVIEW).count()).isEqualTo(1);
            assertThat(distinctTickets.stream().filter(t -> t.getStatus() == TicketStatus.TRANSFERRED).count()).isEqualTo(1);
            assertThat(distinctTickets.stream().filter(t -> t.getStatus() == TicketStatus.AUTO_COMPLETE).count()).isEqualTo(1);

            assertThat(distinctTickets.stream()
                    .filter(t -> t.getStatus() == TicketStatus.LISTED)
                    .map(Ticket::getTicketType))
                    .containsExactlyInAnyOrder(TicketType.MOVIE, TicketType.CONCERT, TicketType.BUS);

            verify(escrowTransactionRepository, times(3)).save(any(EscrowTransaction.class));
            verify(depositRepository, times(3)).save(any(Deposit.class));
            verify(disputeRepository, times(1)).save(any(Dispute.class));
            verify(disputeEvidenceRepository, times(1)).save(any(DisputeEvidence.class));
        }
    }
}
