package com.yourapp.ticketpass.admin.application;

import com.yourapp.ticketpass.admin.web.dto.AdminDashboardSummaryResponse;
import com.yourapp.ticketpass.admin.web.dto.RevenueLedgerRowDto;
import com.yourapp.ticketpass.admin.web.dto.TicketCategoryBreakdownDto;
import com.yourapp.ticketpass.payment.domain.Deposit;
import com.yourapp.ticketpass.payment.domain.DepositRepository;
import com.yourapp.ticketpass.payment.domain.EscrowTransaction;
import com.yourapp.ticketpass.payment.domain.EscrowTransactionRepository;
import com.yourapp.ticketpass.shared.exception.BusinessRuleViolationException;
import com.yourapp.ticketpass.shared.util.CsvExportUtil;
import com.yourapp.ticketpass.ticket.domain.Ticket;
import com.yourapp.ticketpass.ticket.domain.TicketRepository;
import com.yourapp.ticketpass.ticket.domain.TicketStatus;
import com.yourapp.ticketpass.ticket.domain.TicketType;
import com.yourapp.ticketpass.ticket.domain.TicketTypeStatusCount;
import com.yourapp.ticketpass.wallet.domain.AccountLiquidation;
import com.yourapp.ticketpass.wallet.domain.AccountLiquidationRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminDashboardService {

    public static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    public static final int DEFAULT_RANGE_DAYS = 30;

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final DateTimeFormatter LEDGER_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(VIETNAM_ZONE);
    private static final List<TicketType> CATEGORY_ORDER =
            List.of(TicketType.MOVIE, TicketType.CONCERT, TicketType.BUS);

    private final EscrowTransactionRepository escrowTransactionRepository;
    private final DepositRepository depositRepository;
    private final TicketRepository ticketRepository;
    private final AccountLiquidationRepository accountLiquidationRepository;
    private final Clock clock;

    @Autowired
    public AdminDashboardService(EscrowTransactionRepository escrowTransactionRepository,
                                 DepositRepository depositRepository,
                                 TicketRepository ticketRepository,
                                 AccountLiquidationRepository accountLiquidationRepository,
                                 Clock clock) {
        this.escrowTransactionRepository = escrowTransactionRepository;
        this.depositRepository = depositRepository;
        this.ticketRepository = ticketRepository;
        this.accountLiquidationRepository = accountLiquidationRepository;
        this.clock = clock;
    }

    public AdminDashboardService(EscrowTransactionRepository escrowTransactionRepository,
                                 DepositRepository depositRepository,
                                 TicketRepository ticketRepository,
                                 Clock clock) {
        this(escrowTransactionRepository, depositRepository, ticketRepository, null, clock);
    }

    public record ResolvedDateRange(LocalDate fromDate, LocalDate toDate,
                                    Instant startInclusive, Instant endExclusive) {
    }

    @Transactional(readOnly = true)
    public AdminDashboardSummaryResponse getDashboardSummary(LocalDate fromDate, LocalDate toDate) {
        ResolvedDateRange range = resolveDateRange(fromDate, toDate);

        List<EscrowTransaction> releasedEscrows =
                escrowTransactionRepository.findReleasedBetween(range.startInclusive(), range.endExclusive());
        List<Deposit> forfeitedDeposits =
                depositRepository.findForfeitedBetween(range.startInclusive(), range.endExclusive());
        List<AccountLiquidation> liquidations = loadLiquidationsWithFee(range.startInclusive(), range.endExclusive());
        BigDecimal liveHeldAmount = nullSafeZero(escrowTransactionRepository.sumHeldAmount());

        List<TicketTypeStatusCount> completedCounts =
                ticketRepository.countCompletedGroupedByTypeAndStatus(range.startInclusive(), range.endExclusive());
        List<TicketTypeStatusCount> liveCounts =
                ticketRepository.countLiveGroupedByTypeAndStatus();

        Map<Long, Ticket> ticketMap = batchLoadTickets(releasedEscrows, forfeitedDeposits);

        BigDecimal totalGmv = BigDecimal.ZERO;
        BigDecimal totalCommission = BigDecimal.ZERO;
        BigDecimal totalTaxWithheld = BigDecimal.ZERO;
        for (EscrowTransaction escrow : releasedEscrows) {
            totalGmv = totalGmv.add(nullSafeZero(escrow.getSalePrice()));
            totalCommission = totalCommission.add(nullSafeZero(escrow.getTotalCommission()));
            totalTaxWithheld = totalTaxWithheld.add(nullSafeZero(escrow.getTaxWithheld()));
        }

        BigDecimal totalForfeitCut = BigDecimal.ZERO;
        for (Deposit deposit : forfeitedDeposits) {
            totalForfeitCut = totalForfeitCut.add(nullSafeZero(deposit.getFounderCut()));
        }

        BigDecimal totalLiquidationFee = BigDecimal.ZERO;
        for (AccountLiquidation liquidation : liquidations) {
            totalLiquidationFee = totalLiquidationFee.add(nullSafeZero(liquidation.getFeeAmount()));
        }

        BigDecimal totalPlatformRevenue = totalCommission.add(totalForfeitCut).add(totalLiquidationFee);
        List<TicketCategoryBreakdownDto> categoryBreakdowns = buildCategoryBreakdowns(completedCounts, liveCounts);
        List<RevenueLedgerRowDto> ledgerRows = buildLedgerRows(releasedEscrows, forfeitedDeposits, liquidations, ticketMap);

        return AdminDashboardSummaryResponse.builder()
                .fromDate(range.fromDate())
                .toDate(range.toDate())
                .releasedCount(releasedEscrows.size())
                .forfeitedCount(forfeitedDeposits.size())
                .liquidationCount(liquidations.size())
                .totalGmv(totalGmv)
                .totalCommission(totalCommission)
                .totalForfeitCut(totalForfeitCut)
                .totalLiquidationFee(totalLiquidationFee)
                .totalPlatformRevenue(totalPlatformRevenue)
                .totalTaxWithheld(totalTaxWithheld)
                .liveHeldEscrowAmount(liveHeldAmount)
                .categoryBreakdowns(categoryBreakdowns)
                .ledgerRows(ledgerRows)
                .build();
    }

    @Transactional(readOnly = true)
    public byte[] exportDashboardCsv(LocalDate fromDate, LocalDate toDate) {
        ResolvedDateRange range = resolveDateRange(fromDate, toDate);
        List<EscrowTransaction> releasedEscrows =
                escrowTransactionRepository.findReleasedBetween(range.startInclusive(), range.endExclusive());
        List<Deposit> forfeitedDeposits =
                depositRepository.findForfeitedBetween(range.startInclusive(), range.endExclusive());
        List<AccountLiquidation> liquidations = loadLiquidationsWithFee(range.startInclusive(), range.endExclusive());
        Map<Long, Ticket> ticketMap = batchLoadTickets(releasedEscrows, forfeitedDeposits);
        List<RevenueLedgerRowDto> ledgerRows = buildLedgerRows(releasedEscrows, forfeitedDeposits, liquidations, ticketMap);

        List<String> headers = List.of(
                "LoaiGiaoDich",
                "ThoiGian_VN",
                "MaChungTu",
                "MaVe",
                "TieuDeVe",
                "LoaiVe",
                "GMV_VND",
                "DoanhThuSan_VND",
                "ThueKhauTru_VND",
                "GhiChu"
        );

        List<List<String>> rows = ledgerRows.stream()
                .map(row -> Arrays.asList(
                        Objects.toString(row.getSourceType(), ""),
                        Objects.toString(row.getOccurredAtDisplay(), ""),
                        row.getReferenceCode(),
                        row.getTicketId() != null ? String.valueOf(row.getTicketId()) : "",
                        Objects.toString(row.getTicketTitle(), ""),
                        row.getTicketType() != null ? row.getTicketType().name() : "",
                        formatDecimalPlain(row.getGmv()),
                        formatDecimalPlain(row.getPlatformRevenue()),
                        formatDecimalPlain(row.getTaxWithheld()),
                        Objects.toString(row.getNote(), "")
                ))
                .toList();

        return CsvExportUtil.toUtf8BomCsv(headers, rows);
    }

    public ResolvedDateRange resolveDateRange(LocalDate fromDate, LocalDate toDate) {
        LocalDate today = LocalDate.now(clock.withZone(VIETNAM_ZONE));
        LocalDate effectiveTo = toDate != null ? toDate : today;
        LocalDate effectiveFrom = fromDate != null ? fromDate : effectiveTo.minusDays(DEFAULT_RANGE_DAYS - 1L);

        if (effectiveFrom.isAfter(effectiveTo)) {
            throw new BusinessRuleViolationException("Ngày bắt đầu không được lớn hơn ngày kết thúc.");
        }

        Instant startInclusive = effectiveFrom.atStartOfDay(VIETNAM_ZONE).toInstant();
        Instant endExclusive = effectiveTo.plusDays(1).atStartOfDay(VIETNAM_ZONE).toInstant();
        return new ResolvedDateRange(effectiveFrom, effectiveTo, startInclusive, endExclusive);
    }

    private Map<Long, Ticket> batchLoadTickets(List<EscrowTransaction> escrows, List<Deposit> deposits) {
        Set<Long> ticketIds = new LinkedHashSet<>();
        for (EscrowTransaction e : escrows) {
            if (e.getTicketId() != null) {
                ticketIds.add(e.getTicketId());
            }
        }
        for (Deposit d : deposits) {
            if (d.getTicketId() != null) {
                ticketIds.add(d.getTicketId());
            }
        }
        if (ticketIds.isEmpty()) {
            return Map.of();
        }
        return ticketRepository.findByIdIn(ticketIds).stream()
                .collect(Collectors.toMap(Ticket::getId, Function.identity(), (a, b) -> a));
    }

    private List<TicketCategoryBreakdownDto> buildCategoryBreakdowns(List<TicketTypeStatusCount> completedCounts,
                                                                     List<TicketTypeStatusCount> liveCounts) {
        Map<TicketType, Map<TicketStatus, Long>> completedMap = toNestedCountMap(completedCounts);
        Map<TicketType, Map<TicketStatus, Long>> liveMap = toNestedCountMap(liveCounts);

        List<TicketCategoryBreakdownDto> result = new ArrayList<>();
        for (TicketType type : CATEGORY_ORDER) {
            Map<TicketStatus, Long> period = completedMap.getOrDefault(type, Map.of());
            long completed = period.getOrDefault(TicketStatus.AUTO_COMPLETE, 0L)
                    + period.getOrDefault(TicketStatus.RESOLVED_SELLER_WINS, 0L);
            long buyerWonDispute = period.getOrDefault(TicketStatus.RESOLVED_BUYER_WINS, 0L);
            long cancelled = period.getOrDefault(TicketStatus.CANCELLED, 0L);
            long periodTotal = completed + buyerWonDispute + cancelled;

            Map<TicketStatus, Long> live = liveMap.getOrDefault(type, Map.of());
            long liveListed = live.getOrDefault(TicketStatus.LISTED, 0L);
            long liveInProgress = live.getOrDefault(TicketStatus.PAYMENT_ESCROWED, 0L)
                    + live.getOrDefault(TicketStatus.DEPOSIT_LOCKED, 0L)
                    + live.getOrDefault(TicketStatus.TRANSFERRED, 0L)
                    + live.getOrDefault(TicketStatus.AWAITING_USE, 0L);
            long liveDisputeReview = live.getOrDefault(TicketStatus.DISPUTE_UNDER_REVIEW, 0L);

            result.add(TicketCategoryBreakdownDto.builder()
                    .ticketType(type)
                    .displayName(displayNameFor(type))
                    .completedCount(completed)
                    .buyerWonDisputeCount(buyerWonDispute)
                    .cancelledCount(cancelled)
                    .periodTotalCount(periodTotal)
                    .completionRatePercent(toPercent(completed, periodTotal))
                    .disputeRatePercent(toPercent(buyerWonDispute, periodTotal))
                    .cancelRatePercent(toPercent(cancelled, periodTotal))
                    .liveListedCount(liveListed)
                    .liveInProgressCount(liveInProgress)
                    .liveDisputeReviewCount(liveDisputeReview)
                    .build());
        }
        return result;
    }

    private Map<TicketType, Map<TicketStatus, Long>> toNestedCountMap(List<TicketTypeStatusCount> counts) {
        Map<TicketType, Map<TicketStatus, Long>> grouped = new EnumMap<>(TicketType.class);
        if (counts == null) {
            return grouped;
        }
        for (TicketTypeStatusCount item : counts) {
            if (item == null || item.ticketType() == null || item.status() == null) {
                continue;
            }
            grouped.computeIfAbsent(item.ticketType(), k -> new EnumMap<>(TicketStatus.class))
                    .merge(item.status(), item.count(), Long::sum);
        }
        return grouped;
    }

    private List<AccountLiquidation> loadLiquidationsWithFee(Instant startInclusive, Instant endExclusive) {
        if (accountLiquidationRepository == null) {
            return Collections.emptyList();
        }
        List<AccountLiquidation> result =
                accountLiquidationRepository.findWithFeeBetween(startInclusive, endExclusive);
        return result != null ? result : Collections.emptyList();
    }

    private List<RevenueLedgerRowDto> buildLedgerRows(List<EscrowTransaction> escrows,
                                                      List<Deposit> deposits,
                                                      List<AccountLiquidation> liquidations,
                                                      Map<Long, Ticket> ticketMap) {
        List<RevenueLedgerRowDto> rows = new ArrayList<>();
        for (EscrowTransaction escrow : escrows) {
            Ticket ticket = ticketMap.get(escrow.getTicketId());
            String title = resolveTicketTitle(ticket, escrow.getTicketId());
            String note = escrow.getTierCode() != null && !escrow.getTierCode().isBlank()
                    ? "Bậc phí " + escrow.getTierCode()
                    : "Giải ngân escrow";

            rows.add(RevenueLedgerRowDto.builder()
                    .sourceType("ESCROW_RELEASED")
                    .sourceLabel("Giải ngân ký quỹ")
                    .occurredAt(escrow.getReleasedAt())
                    .occurredAtDisplay(formatInstant(escrow.getReleasedAt()))
                    .referenceId(escrow.getId())
                    .ticketId(escrow.getTicketId())
                    .ticketTitle(title)
                    .ticketType(ticket != null ? ticket.getTicketType() : null)
                    .gmv(nullSafeZero(escrow.getSalePrice()))
                    .platformRevenue(nullSafeZero(escrow.getTotalCommission()))
                    .taxWithheld(nullSafeZero(escrow.getTaxWithheld()))
                    .note(note)
                    .build());
        }

        for (Deposit deposit : deposits) {
            Ticket ticket = ticketMap.get(deposit.getTicketId());
            String title = resolveTicketTitle(ticket, deposit.getTicketId());
            String note = deposit.getForfeitReason() != null && !deposit.getForfeitReason().isBlank()
                    ? deposit.getForfeitReason()
                    : "Tịch thu cọc người bán";

            rows.add(RevenueLedgerRowDto.builder()
                    .sourceType("DEPOSIT_FORFEITED")
                    .sourceLabel("Tịch thu cọc")
                    .occurredAt(deposit.getForfeitedAt())
                    .occurredAtDisplay(formatInstant(deposit.getForfeitedAt()))
                    .referenceId(deposit.getId())
                    .ticketId(deposit.getTicketId())
                    .ticketTitle(title)
                    .ticketType(ticket != null ? ticket.getTicketType() : null)
                    .gmv(BigDecimal.ZERO)
                    .platformRevenue(nullSafeZero(deposit.getFounderCut()))
                    .taxWithheld(BigDecimal.ZERO)
                    .note(note)
                    .build());
        }

        if (liquidations != null) {
            for (AccountLiquidation liquidation : liquidations) {
                String note = String.format(
                        "Thanh lý ví tài khoản #%d (Số dư gốc: %,.0f đ · Thực chuyển: %,.0f đ · %s)",
                        liquidation.getUserId(),
                        nullSafeZero(liquidation.getGrossBalance()),
                        nullSafeZero(liquidation.getNetPayoutAmount()),
                        liquidation.getStatus() != null ? liquidation.getStatus().name() : "");

                rows.add(RevenueLedgerRowDto.builder()
                        .sourceType("ACCOUNT_LIQUIDATION_FEE")
                        .sourceLabel("Phí thanh lý (Uy tín = 0)")
                        .occurredAt(liquidation.getLiquidatedAt())
                        .occurredAtDisplay(formatInstant(liquidation.getLiquidatedAt()))
                        .referenceId(liquidation.getId())
                        .ticketId(null)
                        .ticketTitle("Thanh lý tài khoản #" + liquidation.getUserId())
                        .ticketType(null)
                        .gmv(BigDecimal.ZERO)
                        .platformRevenue(nullSafeZero(liquidation.getFeeAmount()))
                        .taxWithheld(BigDecimal.ZERO)
                        .note(note)
                        .build());
            }
        }

        rows.sort(Comparator.comparing(
                        RevenueLedgerRowDto::getOccurredAt,
                        Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(RevenueLedgerRowDto::getReferenceId,
                        Comparator.nullsLast(Comparator.reverseOrder())));
        return rows;
    }

    private String resolveTicketTitle(Ticket ticket, Long ticketId) {
        if (ticket != null && ticket.getTitle() != null && !ticket.getTitle().isBlank()) {
            return ticket.getTitle();
        }
        return ticketId != null ? "Vé #" + ticketId : "Vé không xác định";
    }

    private String formatInstant(Instant instant) {
        return instant != null ? LEDGER_TIME_FORMATTER.format(instant) : "";
    }

    private String formatDecimalPlain(BigDecimal value) {
        return nullSafeZero(value).stripTrailingZeros().toPlainString();
    }

    private BigDecimal toPercent(long count, long total) {
        if (total <= 0) {
            return BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(count)
                .multiply(ONE_HUNDRED)
                .divide(BigDecimal.valueOf(total), 1, RoundingMode.HALF_UP);
    }

    private BigDecimal nullSafeZero(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private String displayNameFor(TicketType type) {
        return switch (type) {
            case MOVIE -> "Vé xem phim";
            case CONCERT -> "Vé sự kiện / Concert";
            case BUS -> "Vé xe khách";
        };
    }
}
