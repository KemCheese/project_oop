package com.yourapp.ticketpass.admin.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yourapp.ticketpass.admin.web.dto.AdminDashboardSummaryResponse;
import com.yourapp.ticketpass.admin.web.dto.TicketCategoryBreakdownDto;
import com.yourapp.ticketpass.payment.domain.Deposit;
import com.yourapp.ticketpass.payment.domain.DepositRepository;
import com.yourapp.ticketpass.payment.domain.DepositStatus;
import com.yourapp.ticketpass.payment.domain.EscrowStatus;
import com.yourapp.ticketpass.payment.domain.EscrowTransaction;
import com.yourapp.ticketpass.payment.domain.EscrowTransactionRepository;
import com.yourapp.ticketpass.shared.exception.BusinessRuleViolationException;
import com.yourapp.ticketpass.ticket.domain.Ticket;
import com.yourapp.ticketpass.ticket.domain.TicketRepository;
import com.yourapp.ticketpass.ticket.domain.TicketStatus;
import com.yourapp.ticketpass.ticket.domain.TicketType;
import com.yourapp.ticketpass.ticket.domain.TicketTypeStatusCount;
import com.yourapp.ticketpass.wallet.domain.AccountLiquidation;
import com.yourapp.ticketpass.wallet.domain.AccountLiquidationRepository;
import com.yourapp.ticketpass.wallet.domain.LiquidationStatus;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("Unit Test - AdminDashboardService (GMV, Hoa hồng, Thuế, Live HELD & CSV)")
class AdminDashboardServiceTest {

        @Mock
        private EscrowTransactionRepository escrowTransactionRepository;

        @Mock
        private DepositRepository depositRepository;

        @Mock
        private TicketRepository ticketRepository;

        @Mock
        private AccountLiquidationRepository accountLiquidationRepository;

        private Clock fixedClock;
        private AdminDashboardService service;

        @BeforeEach
        void setUp() {
                fixedClock = Clock.fixed(Instant.parse("2026-11-17T10:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
                service = new AdminDashboardService(
                                escrowTransactionRepository, depositRepository, ticketRepository,
                                accountLiquidationRepository, fixedClock);
        }

        @Test
        @DisplayName("Tính đúng GMV, Doanh thu sàn (gồm phí thanh lý), Thuế tách riêng, Snapshot HELD và CSV LIQ-*")
        void getDashboardSummary_ShouldComputeCashBasisMetricsAndBreakdowns() {
                EscrowTransaction released = new EscrowTransaction();
                released.setId(101L);
                released.setTicketId(11L);
                released.setStatus(EscrowStatus.RELEASED);
                released.setSalePrice(new BigDecimal("1000000"));
                released.setBuyerCommission(new BigDecimal("25000"));
                released.setSellerCommission(new BigDecimal("25000"));
                released.setTotalCommission(new BigDecimal("50000"));
                released.setTaxWithheld(new BigDecimal("15000"));
                released.setAmountHeld(new BigDecimal("1000000"));
                released.setTierCode("UNDER_5M");
                released.setReleasedAt(Instant.parse("2026-11-16T08:00:00Z"));

                Deposit forfeited = new Deposit();
                forfeited.setId(202L);
                forfeited.setTicketId(12L);
                forfeited.setStatus(DepositStatus.FORFEITED);
                forfeited.setFounderCut(new BigDecimal("35000"));
                forfeited.setForfeitReason("Người bán giao vé sai mã");
                forfeited.setForfeitedAt(Instant.parse("2026-11-16T09:00:00Z"));

                AccountLiquidation liquidation = new AccountLiquidation();
                liquidation.setId(303L);
                liquidation.setUserId(88L);
                liquidation.setGrossBalance(new BigDecimal("1000000.00"));
                liquidation.setFeeAmount(new BigDecimal("50000.00"));
                liquidation.setNetPayoutAmount(new BigDecimal("950000.00"));
                liquidation.setStatus(LiquidationStatus.TRANSFERRED);
                liquidation.setLiquidatedAt(Instant.parse("2026-11-16T10:00:00Z"));

                Ticket concertTicket = new Ticket();
                concertTicket.setId(11L);
                concertTicket.setTitle("=cmd|'/C calc'!A0 Vé Concert");
                concertTicket.setTicketType(TicketType.CONCERT);

                Ticket movieTicket = new Ticket();
                movieTicket.setId(12L);
                movieTicket.setTitle("Vé Phim IMAX");
                movieTicket.setTicketType(TicketType.MOVIE);

                LocalDate from = LocalDate.of(2026, 11, 10);
                LocalDate to = LocalDate.of(2026, 11, 17);
                Instant expectedStart = LocalDate.of(2026, 11, 10).atStartOfDay(AdminDashboardService.VIETNAM_ZONE)
                                .toInstant();
                Instant expectedEnd = LocalDate.of(2026, 11, 18).atStartOfDay(AdminDashboardService.VIETNAM_ZONE)
                                .toInstant();

                when(escrowTransactionRepository.findReleasedBetween(expectedStart, expectedEnd))
                                .thenReturn(List.of(released));
                when(depositRepository.findForfeitedBetween(expectedStart, expectedEnd))
                                .thenReturn(List.of(forfeited));
                when(accountLiquidationRepository.findWithFeeBetween(expectedStart, expectedEnd))
                                .thenReturn(List.of(liquidation));
                when(escrowTransactionRepository.sumHeldAmount())
                                .thenReturn(new BigDecimal("450000"));
                when(ticketRepository.findByIdIn(any()))
                                .thenReturn(List.of(concertTicket, movieTicket));
                when(ticketRepository.countCompletedGroupedByTypeAndStatus(expectedStart, expectedEnd))
                                .thenReturn(List.of(
                                                new TicketTypeStatusCount(TicketType.CONCERT,
                                                                TicketStatus.AUTO_COMPLETE, 3),
                                                new TicketTypeStatusCount(TicketType.CONCERT,
                                                                TicketStatus.RESOLVED_BUYER_WINS, 1),
                                                new TicketTypeStatusCount(TicketType.MOVIE, TicketStatus.CANCELLED,
                                                                1)));
                when(ticketRepository.countLiveGroupedByTypeAndStatus())
                                .thenReturn(List.of(
                                                new TicketTypeStatusCount(TicketType.CONCERT, TicketStatus.LISTED, 4),
                                                new TicketTypeStatusCount(TicketType.CONCERT,
                                                                TicketStatus.DISPUTE_UNDER_REVIEW, 1)));

                AdminDashboardSummaryResponse summary = service.getDashboardSummary(from, to);

                assertThat(summary.getTotalGmv()).isEqualByComparingTo("1000000");
                assertThat(summary.getTotalCommission()).isEqualByComparingTo("25000");
                assertThat(summary.getTotalForfeitCut()).isEqualByComparingTo("35000");
                assertThat(summary.getTotalLiquidationFee()).isEqualByComparingTo("50000");
                assertThat(summary.getLiquidationCount()).isEqualTo(1);
                assertThat(summary.getTotalPlatformRevenue()).isEqualByComparingTo("110000");
                assertThat(summary.getTotalTaxWithheld()).isEqualByComparingTo("15000");
                assertThat(summary.getLiveHeldEscrowAmount()).isEqualByComparingTo("450000");

                TicketCategoryBreakdownDto concertRow = summary.getCategoryBreakdowns().stream()
                                .filter(c -> c.getTicketType() == TicketType.CONCERT)
                                .findFirst().orElseThrow();
                assertThat(concertRow.getCompletedCount()).isEqualTo(3);
                assertThat(concertRow.getBuyerWonDisputeCount()).isEqualTo(1);
                assertThat(concertRow.getCompletionRatePercent()).isEqualByComparingTo("75.0");
                assertThat(concertRow.getDisputeRatePercent()).isEqualByComparingTo("25.0");
                assertThat(concertRow.getLiveListedCount()).isEqualTo(4);
                assertThat(concertRow.getLiveDisputeReviewCount()).isEqualTo(1);

                byte[] csvBytes = service.exportDashboardCsv(from, to);
                String csv = new String(csvBytes, StandardCharsets.UTF_8);
                assertThat(csv).startsWith("\uFEFF");
                assertThat(csv).contains("'=cmd|'/C calc'!A0 Vé Concert");
                assertThat(csv).contains("DEPOSIT_FORFEITED");
                assertThat(csv).contains("DEP-202,12,Vé Phim IMAX,MOVIE,0,35000,0");
                assertThat(csv).contains("ACCOUNT_LIQUIDATION_FEE");
                assertThat(csv).contains("LIQ-303,,Thanh lý tài khoản #88,,0,50000,0");
        }

        @Test
        @DisplayName("Khoảng ngày mặc định 30 ngày theo giờ VN và ném lỗi khi fromDate > toDate")
        void resolveDateRange_ShouldDefaultToLast30DaysAndRejectInvertedRange() {
                AdminDashboardService.ResolvedDateRange range = service.resolveDateRange(null, null);
                assertThat(range.toDate()).isEqualTo(LocalDate.of(2026, 11, 17));
                assertThat(range.fromDate()).isEqualTo(LocalDate.of(2026, 10, 19));

                assertThatThrownBy(() -> service.getDashboardSummary(
                                LocalDate.of(2026, 11, 18), LocalDate.of(2026, 11, 10)))
                                .isInstanceOf(BusinessRuleViolationException.class);
                verify(escrowTransactionRepository, org.mockito.Mockito.never()).findReleasedBetween(any(), any());
        }
}
