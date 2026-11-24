package com.yourapp.ticketpass.reputation.web;

import com.yourapp.ticketpass.reputation.application.ReputationPolicyService;
import com.yourapp.ticketpass.reputation.application.UpdateReputationUseCase;
import com.yourapp.ticketpass.reputation.domain.ReputationRepository;
import com.yourapp.ticketpass.reputation.domain.ReputationTier;
import com.yourapp.ticketpass.reputation.domain.ReputationTierPolicy;
import com.yourapp.ticketpass.reputation.domain.UserReputationScore;
import com.yourapp.ticketpass.reputation.web.dto.ReputationResponse;
import com.yourapp.ticketpass.reputation.web.dto.TransactionHistoryItem;
import com.yourapp.ticketpass.ticket.domain.Ticket;
import com.yourapp.ticketpass.ticket.domain.TicketRepository;
import com.yourapp.ticketpass.ticket.domain.TicketStatus;
import com.yourapp.ticketpass.user.application.UserQueryService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/reputation")
public class ReputationController {

    private final ReputationRepository reputationRepository;
    private final UpdateReputationUseCase updateReputationUseCase;
    private final UserQueryService userQueryService;
    private final TicketRepository ticketRepository;
    private final ReputationPolicyService reputationPolicyService;

    @Autowired
    public ReputationController(ReputationRepository reputationRepository,
                                UpdateReputationUseCase updateReputationUseCase,
                                UserQueryService userQueryService,
                                TicketRepository ticketRepository,
                                ReputationPolicyService reputationPolicyService) {
        this.reputationRepository = reputationRepository;
        this.updateReputationUseCase = updateReputationUseCase;
        this.userQueryService = userQueryService;
        this.ticketRepository = ticketRepository;
        this.reputationPolicyService = reputationPolicyService;
    }

    public ReputationController(ReputationRepository reputationRepository,
                                UpdateReputationUseCase updateReputationUseCase,
                                UserQueryService userQueryService,
                                TicketRepository ticketRepository) {
        this(reputationRepository, updateReputationUseCase, userQueryService, ticketRepository, null);
    }

    @GetMapping("/{userId}")
    public String detail(@PathVariable Long userId, Model model) {
        UserReputationScore score = updateReputationUseCase.getOrCreate(userId);
        ReputationResponse response = mapToResponse(score);
        model.addAttribute("reputation", response);

        List<TransactionHistoryItem> history = buildTransactionHistory(userId);
        model.addAttribute("history", history);

        return "reputation/detail";
    }

    @GetMapping("/top")
    public String topSellers(@RequestParam(defaultValue = "10") int limit, Model model) {
        int clampedLimit = Math.max(1, Math.min(limit, 100));

        List<UserReputationScore> topScores = reputationRepository.findTopSellers(clampedLimit);
        List<ReputationResponse> sellers = topScores.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());

        model.addAttribute("sellers", sellers);
        model.addAttribute("limit", clampedLimit);
        return "reputation/top";
    }

    private ReputationResponse mapToResponse(UserReputationScore score) {
        ReputationResponse response = new ReputationResponse();
        response.setUserId(score.getUserId());
        response.setScore(score.getScore());

        int scoreInt = ReputationTier.normalizeScore(score.getScore());
        ReputationTier tier;
        String limitDesc;
        if (reputationPolicyService != null) {
            ReputationTierPolicy policy = reputationPolicyService.resolvePolicy(score.getScore());
            tier = policy.tier();
            limitDesc = policy.limitDescription();
        } else {
            tier = ReputationTier.fromScoreInt(scoreInt);
            limitDesc = tier.getLimitDescription();
        }
        response.setScoreInt(scoreInt);
        response.setTierName(tier.getDisplayName());
        response.setTierBadgeColor(tier.getBadgeColor());
        response.setTransactionLimitDescription(limitDesc);

        response.setTotalTransactions(score.getTotalTransactions());
        response.setSuccessfulTransactions(score.getSuccessfulTransactions());
        response.setDisputesLost(score.getDisputesLost());
        response.setDisputesWon(score.getDisputesWon());
        response.setLastTransactionAt(score.getLastTransactionAt());

        userQueryService.findById(score.getUserId()).ifPresent(user ->
                response.setFullName(user.getFullName()));
        if (response.getFullName() == null) {
            response.setFullName("Thành viên #" + score.getUserId());
        }

        return response;
    }

    private List<TransactionHistoryItem> buildTransactionHistory(Long userId) {
        List<TransactionHistoryItem> result = new ArrayList<>();

        ticketRepository.findBySellerId(userId).forEach(ticket -> {
            if (ticket.getStatus() != TicketStatus.LISTED) {
                result.add(toHistoryItem(ticket, "SELLER"));
            }
        });

        ticketRepository.findByBuyerId(userId).forEach(ticket ->
                result.add(toHistoryItem(ticket, "BUYER")));

        result.sort(Comparator.comparing(
                item -> item.getEventAt() != null ? item.getEventAt() : Instant.EPOCH,
                Comparator.reverseOrder()
        ));

        return result;
    }

    private TransactionHistoryItem toHistoryItem(Ticket ticket, String role) {
        TransactionHistoryItem item = new TransactionHistoryItem();
        item.setTicketId(ticket.getId());
        item.setEventName(ticket.getEventName() != null ? ticket.getEventName() : ticket.getTitle());
        item.setTitle(ticket.getTitle());
        item.setSalePrice(ticket.getSalePrice());
        item.setRole(role);
        item.setRawStatus(ticket.getStatus() != null ? ticket.getStatus().name() : "");

        TicketStatus status = ticket.getStatus();
        if (status == TicketStatus.AUTO_COMPLETE) {
            item.setOutcomeLabel("Thành công");
            item.setOutcomeColor("success");
        } else if (status == TicketStatus.RESOLVED_SELLER_WINS) {
            if ("SELLER".equals(role)) {
                item.setOutcomeLabel("Thắng tranh chấp");
                item.setOutcomeColor("success");
            } else {
                item.setOutcomeLabel("Thua tranh chấp");
                item.setOutcomeColor("danger");
            }
        } else if (status == TicketStatus.RESOLVED_BUYER_WINS) {
            if ("BUYER".equals(role)) {
                item.setOutcomeLabel("Thắng tranh chấp");
                item.setOutcomeColor("success");
            } else {
                item.setOutcomeLabel("Thua tranh chấp");
                item.setOutcomeColor("danger");
            }
        } else if (status == TicketStatus.CANCELLED) {
            item.setOutcomeLabel("Đã hủy");
            item.setOutcomeColor("danger");
        } else if (status == TicketStatus.DISPUTE_UNDER_REVIEW) {
            item.setOutcomeLabel("Đang tranh chấp");
            item.setOutcomeColor("warning");
        } else {
            item.setOutcomeLabel("Đang xử lý");
            item.setOutcomeColor("info");
        }

        Instant eventAt = ticket.getCompletedAt();
        if (eventAt == null) eventAt = ticket.getTransferredAt();
        if (eventAt == null) eventAt = ticket.getListedAt();
        if (eventAt == null) eventAt = ticket.getCreatedAt();
        item.setEventAt(eventAt);

        return item;
    }
}
