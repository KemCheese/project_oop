package com.yourapp.ticketpass.admin.application;

import com.yourapp.ticketpass.admin.web.dto.AdminUserModerationView;
import com.yourapp.ticketpass.admin.web.dto.AdminUserStatsDto;
import com.yourapp.ticketpass.reputation.domain.ReputationRepository;
import com.yourapp.ticketpass.reputation.domain.UserReputationScore;
import com.yourapp.ticketpass.shared.config.ModerationConfig;
import com.yourapp.ticketpass.user.domain.User;
import com.yourapp.ticketpass.user.domain.UserRepository;
import com.yourapp.ticketpass.user.domain.UserRole;
import com.yourapp.ticketpass.user.domain.UserStatus;
import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Facade truy vấn chỉ đọc phục vụ màn hình quản trị người dùng ({@code GET /admin/users}).
 *
 * <p>Kết hợp dữ liệu tài khoản từ {@code user.domain.UserRepository} với điểm uy tín từ
 * {@code reputation.domain.ReputationRepository} qua batch query {@code findByUserIdIn}
 * để tránh N+1 query và giữ module {@code user} độc lập hoàn toàn với {@code reputation}.</p>
 *
 * <p>Module: admin</p>
 * <p>Chủ sở hữu: Người C (Dev C)</p>
 */
@Service
@RequiredArgsConstructor
public class AdminUserModerationQueryService {

    private static final BigDecimal DEFAULT_SCORE = new BigDecimal("100.00");
    private static final BigDecimal DEFAULT_LOW_THRESHOLD = new BigDecimal("60.00");

    private final UserRepository userRepository;
    private final ReputationRepository reputationRepository;
    private final ModerationConfig moderationConfig;

    /**
     * Kết quả tổng hợp cho màn hình quản trị tài khoản (thẻ thống kê + danh sách đã lọc).
     */
    public record ModerationOverview(AdminUserStatsDto stats, List<AdminUserModerationView> users) {
    }

    /**
     * Tải dữ liệu tổng quan và danh sách tài khoản khớp bộ lọc Admin.
     *
     * @param keyword từ khóa tìm theo email, họ tên hoặc SĐT (có thể null)
     * @param status trạng thái lọc (có thể null)
     * @param warningOnly {@code true} nếu chỉ hiển thị các tài khoản bị gắn cờ cảnh báo rủi ro
     * @return bản ghi gồm thẻ thống kê và danh sách người dùng đã ghép điểm uy tín
     */
    @Transactional(readOnly = true)
    public ModerationOverview loadModerationOverview(String keyword, UserStatus status, boolean warningOnly) {
        boolean hasDbFilter = (keyword != null && !keyword.isBlank()) || status != null;
        List<User> filteredUsers = userRepository.searchForAdmin(keyword, status);
        List<User> summarySource = hasDbFilter ? userRepository.searchForAdmin(null, null) : filteredUsers;
        if (hasDbFilter && summarySource.isEmpty()) {
            summarySource = filteredUsers;
        }

        Set<Long> allUserIds = new LinkedHashSet<>();
        filteredUsers.forEach(u -> allUserIds.add(u.getId()));
        summarySource.forEach(u -> allUserIds.add(u.getId()));

        Map<Long, UserReputationScore> scoreMap = loadScoreMap(allUserIds);

        BigDecimal scoreThreshold = moderationConfig.getLowReputationThreshold() != null
                ? moderationConfig.getLowReputationThreshold()
                : DEFAULT_LOW_THRESHOLD;
        int maxDisputesLost = moderationConfig.getMaxDisputesLostWarning();

        List<AdminUserModerationView> allFilteredViews = filteredUsers.stream()
                .map(user -> toView(user, scoreMap.get(user.getId()), scoreThreshold, maxDisputesLost))
                .toList();

        List<AdminUserModerationView> summaryViews = hasDbFilter
                ? summarySource.stream()
                        .map(u -> toView(u, scoreMap.get(u.getId()), scoreThreshold, maxDisputesLost))
                        .toList()
                : allFilteredViews;

        List<AdminUserModerationView> tableViews = warningOnly
                ? allFilteredViews.stream().filter(AdminUserModerationView::isFlaggedRisk).toList()
                : allFilteredViews;

        AdminUserStatsDto stats = buildStats(summaryViews);
        return new ModerationOverview(stats, tableViews);
    }

    private Map<Long, UserReputationScore> loadScoreMap(Set<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return reputationRepository.findByUserIdIn(userIds).stream()
                .collect(Collectors.toMap(
                        UserReputationScore::getUserId,
                        Function.identity(),
                        (first, second) -> first
                ));
    }

    private AdminUserModerationView toView(User user,
                                         UserReputationScore rep,
                                         BigDecimal scoreThreshold,
                                         int maxDisputesLost) {
        BigDecimal score = rep != null && rep.getScore() != null ? rep.getScore() : DEFAULT_SCORE;
        int totalTx = rep != null && rep.getTotalTransactions() != null ? rep.getTotalTransactions() : 0;
        int disputesLost = rep != null && rep.getDisputesLost() != null ? rep.getDisputesLost() : 0;
        int cancelledListings = rep != null && rep.getCancelledListings() != null ? rep.getCancelledListings() : 0;

        boolean isAdmin = user.getRole() == UserRole.ADMIN;
        boolean lowScore = !isAdmin && score.compareTo(scoreThreshold) < 0;
        boolean frequentDisputeLoss = !isAdmin && disputesLost >= maxDisputesLost;
        boolean flaggedRisk = lowScore || frequentDisputeLoss;
        String riskReason = buildRiskReason(lowScore, frequentDisputeLoss, scoreThreshold, maxDisputesLost);

        return AdminUserModerationView.builder()
                .id(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .phoneNumber(user.getPhoneNumber())
                .role(user.getRole())
                .status(user.getStatus())
                .emailVerified(user.isEmailVerified())
                .reputationScore(score)
                .totalTransactions(totalTx)
                .disputesLost(disputesLost)
                .cancelledListings(cancelledListings)
                .flaggedRisk(flaggedRisk)
                .riskReason(riskReason)
                .moderationReason(user.getModerationReason())
                .moderatedAt(user.getModeratedAt())
                .moderatedByAdminId(user.getModeratedByAdminId())
                .createdAt(user.getCreatedAt())
                .build();
    }

    private AdminUserStatsDto buildStats(List<AdminUserModerationView> views) {
        long total = views.size();
        long active = views.stream().filter(v -> v.getStatus() == UserStatus.ACTIVE).count();
        long locked = views.stream()
                .filter(v -> v.getStatus() == UserStatus.SUSPENDED || v.getStatus() == UserStatus.BANNED)
                .count();
        long warning = views.stream().filter(AdminUserModerationView::isFlaggedRisk).count();

        return AdminUserStatsDto.builder()
                .totalUsers(total)
                .activeUsers(active)
                .lockedUsers(locked)
                .warningUsers(warning)
                .build();
    }

    private String buildRiskReason(boolean lowScore,
                                   boolean frequentDisputeLoss,
                                   BigDecimal scoreThreshold,
                                   int maxDisputesLost) {
        if (lowScore && frequentDisputeLoss) {
            return "Điểm uy tín < " + scoreThreshold.stripTrailingZeros().toPlainString()
                    + " & thua >= " + maxDisputesLost + " tranh chấp";
        }
        if (lowScore) {
            return "Điểm uy tín thấp (< " + scoreThreshold.stripTrailingZeros().toPlainString() + ")";
        }
        if (frequentDisputeLoss) {
            return "Thua nhiều tranh chấp (>= " + maxDisputesLost + " lần)";
        }
        return "";
    }
}
