package com.yourapp.ticketpass.system.flow;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.yourapp.ticketpass.payment.application.ReleaseFundsUseCase;
import com.yourapp.ticketpass.payment.domain.Deposit;
import com.yourapp.ticketpass.payment.domain.DepositStatus;
import com.yourapp.ticketpass.payment.domain.EscrowStatus;
import com.yourapp.ticketpass.payment.domain.EscrowTransaction;
import com.yourapp.ticketpass.reputation.domain.UserReputationScore;
import com.yourapp.ticketpass.shared.event.CompletionReason;
import com.yourapp.ticketpass.system.base.AbstractSystemIntegrationTest;
import com.yourapp.ticketpass.ticket.application.TicketStateMachineService;
import com.yourapp.ticketpass.ticket.domain.Ticket;
import com.yourapp.ticketpass.ticket.domain.TicketStatus;
import com.yourapp.ticketpass.ticket.domain.TicketType;
import com.yourapp.ticketpass.user.domain.User;
import com.yourapp.ticketpass.user.domain.UserRole;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Hệ Thống - Luồng 1: Happy Path Giao Dịch Vé Toàn Diện (Escrow -> Cọc -> Hoàn Tất -> Giải Ngân & Điểm Uy Tín)")
class HappyPathEscrowTransactionFlowTest extends AbstractSystemIntegrationTest {

    @Autowired
    private ReleaseFundsUseCase releaseFundsUseCase;

    @Autowired
    private TicketStateMachineService ticketStateMachineService;

    @Test
    @DisplayName("Chu trình giao dịch vé thành công: Tiền giải ngân cho Seller, Cọc hoàn trả, Điểm uy tín 2 bên tăng +2.00")
    void shouldExecuteFullHappyPathTransactionLifecycleSuccessfully() {
        // 1. Arrange: Tạo Người bán và Người mua
        User seller = createAndSaveUser("happy_seller@test.com", "Password123@", UserRole.USER);
        User buyer = createAndSaveUser("happy_buyer@test.com", "Password123@", UserRole.USER);

        // Thiết lập điểm uy tín ban đầu 90.00 để kiểm tra cộng điểm rõ ràng
        UserReputationScore sellerRep = reputationRepository.findByUserId(seller.getId()).orElseThrow();
        sellerRep.setScore(new BigDecimal("90.00"));
        reputationRepository.save(sellerRep);

        UserReputationScore buyerRep = reputationRepository.findByUserId(buyer.getId()).orElseThrow();
        buyerRep.setScore(new BigDecimal("90.00"));
        reputationRepository.save(buyerRep);

        // 2. Seller đăng bán vé
        BigDecimal salePrice = new BigDecimal("500000.00");
        Ticket ticket = createAndSaveTicket(seller.getId(), "Concert Anh Trai Vượt Ngàn Chông Gai", TicketType.CONCERT, salePrice, TicketStatus.LISTED);

        // 3. Buyer mua vé -> Nạp tiền vào Escrow
        EscrowTransaction escrow = createAndSaveEscrow(ticket.getId(), buyer.getId(), salePrice, EscrowStatus.HELD);
        ticket.setBuyerId(buyer.getId());
        ticket.setEscrowTransactionId(escrow.getId());
        ticket.setEscrowedAt(Instant.now());
        ticketRepository.save(ticket);
        ticketStateMachineService.transition(ticket.getId(), TicketStatus.PAYMENT_ESCROWED);

        // 4. Seller nạp Cọc 50%
        BigDecimal depositAmount = salePrice.multiply(new BigDecimal("0.5"));
        Deposit deposit = createAndSaveDeposit(ticket.getId(), seller.getId(), depositAmount, DepositStatus.LOCKED);
        ticket = ticketRepository.findById(ticket.getId()).orElseThrow();
        ticket.setDepositId(deposit.getId());
        ticketRepository.save(ticket);
        ticketStateMachineService.transition(ticket.getId(), TicketStatus.DEPOSIT_LOCKED);

        // 5. Chuyển giao vé (ảnh QR bí mật mở khoá) cho Buyer
        ticketStateMachineService.transition(ticket.getId(), TicketStatus.TRANSFERRED);
        ticket = ticketRepository.findById(ticket.getId()).orElseThrow();
        ticket.setTransferredAt(Instant.now());
        ticketRepository.save(ticket);

        // 6. Buyer xác nhận nhận vé -> Chuyển sang chờ sự kiện / hết cửa sổ khiếu nại
        ticketStateMachineService.transition(ticket.getId(), TicketStatus.AWAITING_USE);
        ticket = ticketRepository.findById(ticket.getId()).orElseThrow();
        ticket.setReceivedAt(Instant.now());
        ticket.setDisputeWindowClosesAt(Instant.now().minus(1, ChronoUnit.HOURS)); // Giả lập hết cửa sổ khiếu nại
        ticketRepository.save(ticket);

        // 7. Hoàn tất giao dịch: Kích hoạt ReleaseFundsUseCase
        releaseFundsUseCase.release(ticket.getId(), CompletionReason.AUTO_COMPLETE);

        // 8. Assertions: Kiểm tra tính nhất quán toàn hệ thống
        // 8.1 Trạng thái vé: Sau AUTO_COMPLETE sẽ có trạng thái TicketStatus.AUTO_COMPLETE
        Ticket finalTicket = ticketRepository.findById(ticket.getId()).orElseThrow();
        assertThat(finalTicket.getStatus()).isEqualTo(TicketStatus.AUTO_COMPLETE);

        // 8.2 Trạng thái Escrow và Deposit
        EscrowTransaction finalEscrow = escrowTransactionRepository.findById(escrow.getId()).orElseThrow();
        assertThat(finalEscrow.getStatus()).isEqualTo(EscrowStatus.RELEASED);

        Deposit finalDeposit = depositRepository.findById(deposit.getId()).orElseThrow();
        assertThat(finalDeposit.getStatus()).isEqualTo(DepositStatus.REFUNDED);

        // 8.3 Kiểm chứng Observer Pattern cập nhật điểm uy tín sau commit (+2.00 điểm mỗi bên)
        UserReputationScore updatedSellerRep = reputationRepository.findByUserId(seller.getId()).orElseThrow();
        assertThat(updatedSellerRep.getScore()).isEqualByComparingTo(new BigDecimal("92.00"));
        assertThat(updatedSellerRep.getSuccessfulTransactions()).isEqualTo(1);
        assertThat(updatedSellerRep.getTotalTransactions()).isEqualTo(1);

        UserReputationScore updatedBuyerRep = reputationRepository.findByUserId(buyer.getId()).orElseThrow();
        assertThat(updatedBuyerRep.getScore()).isEqualByComparingTo(new BigDecimal("92.00"));
        assertThat(updatedBuyerRep.getSuccessfulTransactions()).isEqualTo(1);
        assertThat(updatedBuyerRep.getTotalTransactions()).isEqualTo(1);
    }
}
