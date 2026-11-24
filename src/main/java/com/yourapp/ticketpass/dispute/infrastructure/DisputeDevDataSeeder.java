package com.yourapp.ticketpass.dispute.infrastructure;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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
import com.yourapp.ticketpass.ticket.domain.Ticket;
import com.yourapp.ticketpass.ticket.domain.TicketRepository;
import com.yourapp.ticketpass.ticket.domain.TicketStatus;
import com.yourapp.ticketpass.user.domain.User;
import com.yourapp.ticketpass.user.domain.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@Profile("!prod")
@Order(100)
@RequiredArgsConstructor
public class DisputeDevDataSeeder implements CommandLineRunner {

    private final DisputeRepository disputeRepository;
    private final DisputeEvidenceRepository disputeEvidenceRepository;
    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final EscrowTransactionRepository escrowTransactionRepository;
    private final DepositRepository depositRepository;

    @Override
    @Transactional
    public void run(String... args) {
        Optional<User> buyerOpt = userRepository.findByEmail("buyer@test.com");
        Optional<User> sellerOpt = userRepository.findByEmail("seller@test.com");
        if (buyerOpt.isEmpty() || sellerOpt.isEmpty()) {
            return;
        }

        if (ticketRepository.countByStatus(TicketStatus.DISPUTE_UNDER_REVIEW) > 0) {
            return;
        }

        User buyer = buyerOpt.get();
        User seller = sellerOpt.get();
        List<Ticket> tickets = ticketRepository.findBySellerId(seller.getId());
        if (tickets.isEmpty()) {
            return;
        }

        Ticket ticket1 = tickets.get(0);
        if (disputeRepository.findByTicketId(ticket1.getId()).isPresent()) {
            return;
        }

        Instant now = Instant.now();

        ticket1.setBuyerId(buyer.getId());
        ticket1.setStatus(TicketStatus.DISPUTE_UNDER_REVIEW);
        ticketRepository.save(ticket1);

        BigDecimal price = (ticket1.getSalePrice() != null && ticket1.getSalePrice().compareTo(BigDecimal.ZERO) > 0)
                ? ticket1.getSalePrice()
                : new BigDecimal("250000.00");

        EscrowTransaction escrow = new EscrowTransaction();
        escrow.setTicketId(ticket1.getId());
        escrow.setBuyerId(buyer.getId());
        escrow.setSellerId(seller.getId());
        escrow.setSalePrice(price);
        escrow.setAmountHeld(price);
        escrow.setStatus(EscrowStatus.HELD);
        escrow.setGatewayName("MOMO_TEST");
        escrow.setHeldAt(now.minus(Duration.ofDays(1)));
        escrow = escrowTransactionRepository.save(escrow);

        Deposit deposit = new Deposit();
        deposit.setTicketId(ticket1.getId());
        deposit.setSellerId(seller.getId());
        deposit.setAmount(price.multiply(new BigDecimal("0.5")));
        deposit.setDepositPercent(new BigDecimal("50.00"));
        deposit.setStatus(DepositStatus.LOCKED);
        deposit.setLockedAt(now.minus(Duration.ofDays(1)));
        deposit = depositRepository.save(deposit);

        Dispute dispute1 = new Dispute();
        dispute1.setTicketId(ticket1.getId());
        dispute1.setBuyerId(buyer.getId());
        dispute1.setSellerId(seller.getId());
        dispute1.setEscrowTransactionId(escrow.getId());
        dispute1.setDepositId(deposit.getId());
        dispute1.setStatus(DisputeStatus.UNDER_REVIEW);
        dispute1.setReason(DisputeReason.TICKET_INVALID);
        dispute1.setDescription(
                "Tôi đến quầy soát vé rạp vào lúc 19:15 nhưng nhân viên thông báo mã vé này đã được check-in vào lúc 18:40 trước đó. Tôi đã bị từ chối vào rạp xem phim.");
        dispute1.setFiledAt(now.minus(Duration.ofHours(5)));
        dispute1.setSellerResponseDeadline(now.plus(Duration.ofHours(19)));
        dispute1.setSellerResponse(
                "Tôi cam kết vé mua chính chủ từ app CGV và chưa từng gửi mã cho ai khác trước thời điểm bàn giao cho người mua.");
        dispute1 = disputeRepository.save(dispute1);

        DisputeEvidence evidence = new DisputeEvidence();
        evidence.setDisputeId(dispute1.getId());
        evidence.setStorageKey("samples/movie-sample.jpg");
        evidence.setEvidenceType(DisputeEvidenceType.IMAGE);
        evidence.setContentType("image/jpeg");
        evidence.setOriginalFilename("ticket-invalid-gate.jpg");
        evidence.setSizeBytes(145000L);
        evidence.setUploadedByUserId(buyer.getId());
        evidence.setUploadedAt(now.minus(Duration.ofHours(5)));
        disputeEvidenceRepository.save(evidence);

        if (tickets.size() > 1) {
            Ticket ticket2 = tickets.get(1);
            ticket2.setBuyerId(buyer.getId());
            ticket2.setStatus(TicketStatus.TRANSFERRED);
            ticket2.setTransferredAt(now.minus(Duration.ofHours(1)));
            ticketRepository.save(ticket2);
        }

        log.info("[DisputeDevDataSeeder] Đã khởi tạo thành công khiếu nại mẫu #{} cho vé #{} phục vụ kiểm thử UI/UX!",
                dispute1.getId(), ticket1.getId());
    }
}
