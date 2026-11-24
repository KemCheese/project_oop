package com.yourapp.ticketpass.system.base;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.yourapp.ticketpass.dispute.domain.Dispute;
import com.yourapp.ticketpass.dispute.domain.DisputeEvidenceRepository;
import com.yourapp.ticketpass.dispute.domain.DisputeReason;
import com.yourapp.ticketpass.dispute.domain.DisputeRepository;
import com.yourapp.ticketpass.dispute.domain.DisputeStatus;
import com.yourapp.ticketpass.dispute.infrastructure.DisputeEvidenceJpaRepository;
import com.yourapp.ticketpass.dispute.infrastructure.DisputeJpaRepository;
import com.yourapp.ticketpass.payment.domain.Deposit;
import com.yourapp.ticketpass.payment.domain.DepositRepository;
import com.yourapp.ticketpass.payment.domain.DepositStatus;
import com.yourapp.ticketpass.payment.domain.EscrowStatus;
import com.yourapp.ticketpass.payment.domain.EscrowTransaction;
import com.yourapp.ticketpass.payment.domain.EscrowTransactionRepository;
import com.yourapp.ticketpass.payment.infrastructure.DepositJpaRepository;
import com.yourapp.ticketpass.payment.infrastructure.EscrowTransactionJpaRepository;
import com.yourapp.ticketpass.reputation.domain.ReputationRepository;
import com.yourapp.ticketpass.reputation.domain.UserReputationScore;
import com.yourapp.ticketpass.reputation.infrastructure.ReputationJpaRepository;
import com.yourapp.ticketpass.ticket.domain.Ticket;
import com.yourapp.ticketpass.ticket.domain.TicketRepository;
import com.yourapp.ticketpass.ticket.domain.TicketStatus;
import com.yourapp.ticketpass.ticket.domain.TicketType;
import com.yourapp.ticketpass.ticket.infrastructure.TicketJpaRepository;
import com.yourapp.ticketpass.user.domain.StarredTicketRepository;
import com.yourapp.ticketpass.user.domain.User;
import com.yourapp.ticketpass.user.domain.UserRepository;
import com.yourapp.ticketpass.user.domain.UserRole;
import com.yourapp.ticketpass.user.domain.UserStatus;
import com.yourapp.ticketpass.user.infrastructure.StarredTicketJpaRepository;
import com.yourapp.ticketpass.user.infrastructure.UserJpaRepository;
import com.yourapp.ticketpass.wallet.domain.AccountLiquidationRepository;
import com.yourapp.ticketpass.wallet.domain.WalletRepository;
import com.yourapp.ticketpass.wallet.domain.WalletTransactionRepository;
import com.yourapp.ticketpass.wallet.infrastructure.AccountLiquidationJpaRepository;
import com.yourapp.ticketpass.wallet.infrastructure.WalletJpaRepository;
import com.yourapp.ticketpass.wallet.infrastructure.WalletTransactionJpaRepository;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class AbstractSystemIntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected UserJpaRepository userJpaRepository;

    @Autowired
    protected TicketRepository ticketRepository;

    @Autowired
    protected TicketJpaRepository ticketJpaRepository;

    @Autowired
    protected EscrowTransactionRepository escrowTransactionRepository;

    @Autowired
    protected EscrowTransactionJpaRepository escrowTransactionJpaRepository;

    @Autowired
    protected DepositRepository depositRepository;

    @Autowired
    protected DepositJpaRepository depositJpaRepository;

    @Autowired
    protected DisputeRepository disputeRepository;

    @Autowired
    protected DisputeJpaRepository disputeJpaRepository;

    @Autowired
    protected DisputeEvidenceRepository disputeEvidenceRepository;

    @Autowired
    protected DisputeEvidenceJpaRepository disputeEvidenceJpaRepository;

    @Autowired
    protected ReputationRepository reputationRepository;

    @Autowired
    protected ReputationJpaRepository reputationJpaRepository;

    @Autowired
    protected AccountLiquidationRepository accountLiquidationRepository;

    @Autowired
    protected AccountLiquidationJpaRepository accountLiquidationJpaRepository;

    @Autowired
    protected WalletRepository walletRepository;

    @Autowired
    protected WalletJpaRepository walletJpaRepository;

    @Autowired
    protected WalletTransactionRepository walletTransactionRepository;

    @Autowired
    protected WalletTransactionJpaRepository walletTransactionJpaRepository;

    @Autowired
    protected StarredTicketRepository starredTicketRepository;

    @Autowired
    protected StarredTicketJpaRepository starredTicketJpaRepository;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    protected final List<Long> createdUserIds = new ArrayList<>();
    protected final List<Long> createdTicketIds = new ArrayList<>();
    protected final List<Long> createdEscrowIds = new ArrayList<>();
    protected final List<Long> createdDepositIds = new ArrayList<>();
    protected final List<Long> createdDisputeIds = new ArrayList<>();

    @AfterEach
    void tearDownData() {
        cleanAllTestData();
    }

    protected void cleanAllTestData() {
        try {
            if (!createdDisputeIds.isEmpty()) {
                disputeEvidenceJpaRepository.deleteAll();
                disputeJpaRepository.deleteAllById(createdDisputeIds);
                createdDisputeIds.clear();
            }
            if (!createdDepositIds.isEmpty()) {
                depositJpaRepository.deleteAllById(createdDepositIds);
                createdDepositIds.clear();
            }
            if (!createdEscrowIds.isEmpty()) {
                escrowTransactionJpaRepository.deleteAllById(createdEscrowIds);
                createdEscrowIds.clear();
            }
            starredTicketJpaRepository.deleteAll();
            if (!createdTicketIds.isEmpty()) {
                ticketJpaRepository.deleteAllById(createdTicketIds);
                createdTicketIds.clear();
            }
            if (!createdUserIds.isEmpty()) {
                for (Long uid : createdUserIds) {
                    accountLiquidationRepository.findByTriggerKey("ZERO_REPUTATION_" + uid)
                            .ifPresent(l -> accountLiquidationJpaRepository.deleteById(l.getId()));
                    walletRepository.findByUserId(uid).ifPresent(w -> {
                        walletTransactionRepository.findByWalletIdOrderByCreatedAtDesc(w.getId())
                                .forEach(tx -> walletTransactionJpaRepository.deleteById(tx.getId()));
                        walletJpaRepository.deleteById(w.getId());
                    });
                    reputationRepository.findByUserId(uid).ifPresent(r -> reputationJpaRepository.deleteById(r.getId()));
                }
                userJpaRepository.deleteAllById(createdUserIds);
                createdUserIds.clear();
            }
        } catch (Exception ignored) {
        }
    }

    protected User createAndSaveUser(String email, String rawPassword, UserRole role) {
        Optional<User> existing = userRepository.findByEmail(email);
        if (existing.isPresent()) {
            return existing.get();
        }

        User user = new User();
        user.setEmail(email);
        user.setFullName("User " + email.split("@")[0]);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        user.setEmailVerified(true);
        user.setPhoneVerified(true);
        user.setPhoneNumber("0987654321");
        user.setBankName("Techcombank");
        user.setBankAccountNumber("190367891234");
        user.setBankAccountHolder(user.getFullName().toUpperCase());
        User savedUser = userRepository.save(user);
        createdUserIds.add(savedUser.getId());

        if (reputationRepository.findByUserId(savedUser.getId()).isEmpty()) {
            UserReputationScore rep = new UserReputationScore();
            rep.setUserId(savedUser.getId());
            rep.setScore(new BigDecimal("3.00"));
            rep.setTotalTransactions(0);
            rep.setSuccessfulTransactions(0);
            rep.setCancelledListings(0);
            rep.setDisputesOpenedAgainst(0);
            rep.setDisputesLost(0);
            rep.setDisputesWon(0);
            rep.setTotalGmv(BigDecimal.ZERO);
            reputationRepository.save(rep);
        }

        return savedUser;
    }

    protected Ticket createAndSaveTicket(Long sellerId, String title, TicketType type, BigDecimal price, TicketStatus status) {
        Ticket ticket = new Ticket();
        ticket.setSellerId(sellerId);
        ticket.setTitle(title);
        ticket.setEventName(title);
        ticket.setTicketType(type);
        ticket.setVenue("Hà Nội Arena");
        ticket.setSeatInfo("Ghế VIP A-10");
        ticket.setOriginalPrice(price);
        ticket.setSalePrice(price);
        ticket.setStatus(status);
        ticket.setProviderName("TicketBox");
        ticket.setProviderOrderCode("TB-" + System.currentTimeMillis());
        ticket.setTicketImageStorageKey("tickets/public-" + System.currentTimeMillis() + ".png");
        ticket.setQrImageStorageKey("tickets/secret-qr-" + System.currentTimeMillis() + ".png");
        ticket.setTransferInstruction("Xuất trình mã QR tại cửa soát vé");
        ticket.setListedAt(Instant.now().minus(2, ChronoUnit.HOURS));
        ticket.setEventStartAt(Instant.now().plus(3, ChronoUnit.DAYS));
        Ticket savedTicket = ticketRepository.save(ticket);
        createdTicketIds.add(savedTicket.getId());
        return savedTicket;
    }

    protected EscrowTransaction createAndSaveEscrow(Long ticketId, Long buyerId, BigDecimal amount, EscrowStatus status) {
        Ticket ticket = ticketRepository.findById(ticketId).orElse(null);
        Long sellerId = ticket != null ? ticket.getSellerId() : 1L;

        EscrowTransaction escrow = new EscrowTransaction();
        escrow.setTicketId(ticketId);
        escrow.setSellerId(sellerId);
        escrow.setBuyerId(buyerId);
        escrow.setSalePrice(amount);
        escrow.setBuyerCommission(new BigDecimal("10000.00"));
        escrow.setSellerCommission(new BigDecimal("10000.00"));
        escrow.setTotalCommission(new BigDecimal("20000.00"));
        escrow.setTaxWithheld(BigDecimal.ZERO);
        escrow.setSellerNetPayout(amount.subtract(new BigDecimal("10000.00")));
        escrow.setAmountHeld(amount);
        escrow.setStatus(status);
        escrow.setGatewayName("MANUAL_TRANSFER");
        escrow.setGatewayTransactionId("TXN-ESCROW-" + System.currentTimeMillis());
        escrow.setHeldAt(Instant.now());
        EscrowTransaction savedEscrow = escrowTransactionRepository.save(escrow);
        createdEscrowIds.add(savedEscrow.getId());
        return savedEscrow;
    }

    protected Deposit createAndSaveDeposit(Long ticketId, Long sellerId, BigDecimal amount, DepositStatus status) {
        Deposit deposit = new Deposit();
        deposit.setTicketId(ticketId);
        deposit.setSellerId(sellerId);
        deposit.setAmount(amount);
        deposit.setDepositPercent(new BigDecimal("50.00"));
        deposit.setStatus(status);
        deposit.setGatewayName("MANUAL_TRANSFER");
        deposit.setGatewayTransactionId("TXN-DEP-" + System.currentTimeMillis());
        deposit.setLockedAt(Instant.now());
        Deposit savedDeposit = depositRepository.save(deposit);
        createdDepositIds.add(savedDeposit.getId());
        return savedDeposit;
    }

    protected Dispute createAndSaveDispute(Long ticketId, Long buyerId, Long sellerId, DisputeReason reason, DisputeStatus status) {
        Dispute dispute = new Dispute();
        dispute.setTicketId(ticketId);
        dispute.setBuyerId(buyerId);
        dispute.setSellerId(sellerId);
        dispute.setReason(reason);
        dispute.setStatus(status);
        dispute.setDescription("Vé không sử dụng được tại cổng soát vé");
        dispute.setFiledAt(Instant.now());
        dispute.setWindowClosesAt(Instant.now().plus(2, ChronoUnit.DAYS));
        dispute.setSellerResponseDeadline(Instant.now().plus(24, ChronoUnit.HOURS));
        Dispute savedDispute = disputeRepository.save(dispute);
        createdDisputeIds.add(savedDispute.getId());
        return savedDispute;
    }
}
