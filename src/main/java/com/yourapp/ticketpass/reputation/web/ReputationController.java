package com.yourapp.ticketpass.reputation.web;

import com.yourapp.ticketpass.reputation.application.UpdateReputationUseCase;
import com.yourapp.ticketpass.reputation.domain.ReputationRepository;
import com.yourapp.ticketpass.reputation.domain.UserReputationScore;
import com.yourapp.ticketpass.reputation.web.dto.ReputationResponse;
import com.yourapp.ticketpass.user.application.UserQueryService;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Controller hiển thị thông tin uy tín công khai.
 *
 * <p>
 * Bảng route:
 * </p>
 * 
 * <pre>
 * GET /reputation/{userId}  -&gt; ho so uy tin cong khai (view "reputation/detail")
 * GET /reputation/top       -&gt; bang xep hang seller (view "reputation/top")
 * </pre>
 *
 * <p>
 * Cả hai route đều {@code permitAll()} trong {@code SecurityConfig} — uy tín là
 * thông tin
 * công khai, guest cũng xem được khi duyệt marketplace.
 * </p>
 *
 * <p>
 * Controller chỉ trả {@link ReputationResponse} (DTO) chứ không đẩy entity
 * {@link UserReputationScore} ra view, bảo vệ các trường nội bộ
 * ({@code version},
 * {@code cancelledListings}, {@code disputesOpenedAgainst}).
 * </p>
 *
 * <p>
 * <b>Lưu ý từ Task C8:</b> Khi {@code reputationRepository.findByUserId()} trả
 * về
 * {@code Optional.empty()}, gọi
 * {@code updateReputationUseCase.getOrCreate(userId)}
 * để đảm bảo luôn có bản ghi trả về cho view.
 * </p>
 *
 * <p>
 * Module: reputation
 * </p>
 *
 * Chủ sở hữu: Người C
 */
@Controller
@RequestMapping("/reputation")
@RequiredArgsConstructor
public class ReputationController {

    private final ReputationRepository reputationRepository;

    private final UpdateReputationUseCase updateReputationUseCase;

    private final UserQueryService userQueryService;

    /**
     * Hiển thị hồ sơ uy tín công khai của một người dùng.
     *
     * <p>
     * Tuân thủ lưu ý Task C8: dùng {@code getOrCreate()} thay vì
     * {@code findByUserId()}
     * để đảm bảo luôn có bản ghi (tạo mặc định 100.00 nếu chưa tồn tại).
     * </p>
     *
     * @param userId id người dùng cần xem
     * @param model  model của view
     * @return tên view {@code reputation/detail}
     */
    @GetMapping("/{userId}")
    public String detail(@PathVariable Long userId, Model model) {
        // getOrCreate: nếu chưa có bản ghi → khởi tạo với 100.00 (lưu ý C8)
        UserReputationScore score = updateReputationUseCase.getOrCreate(userId);
        ReputationResponse response = mapToResponse(score);
        model.addAttribute("reputation", response);
        return "reputation/detail";
    }

    /**
     * Hiển thị bảng xếp hạng người bán theo điểm uy tín.
     *
     * <p>
     * Tham số {@code limit} được chặn trong khoảng [1, 100] để tránh truy vấn
     * quá nặng hoặc giá trị vô nghĩa.
     * </p>
     *
     * @param limit số người bán hiển thị, mặc định 10
     * @param model model của view
     * @return tên view {@code reputation/top}
     */
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

    /**
     * Chuyển đổi entity sang DTO để trả ra view.
     *
     * <p>
     * Chỉ copy các trường công khai; bỏ qua {@code version},
     * {@code cancelledListings},
     * {@code disputesOpenedAgainst}, {@code createdAt}, {@code lastUpdatedAt} —
     * không cần
     * hiển thị cho người xem bên ngoài.
     * </p>
     *
     * @param score entity bản ghi điểm uy tín
     * @return DTO cho view
     */
    private ReputationResponse mapToResponse(UserReputationScore score) {
        ReputationResponse response = new ReputationResponse();
        response.setUserId(score.getUserId());
        response.setScore(score.getScore());
        response.setTotalTransactions(score.getTotalTransactions());
        response.setSuccessfulTransactions(score.getSuccessfulTransactions());
        response.setDisputesLost(score.getDisputesLost());
        response.setDisputesWon(score.getDisputesWon());
        response.setLastTransactionAt(score.getLastTransactionAt());

        userQueryService.findById(score.getUserId()).ifPresent(user -> {
            response.setFullName(user.getFullName());
        });
        if (response.getFullName() == null) {
            response.setFullName("Thành viên #" + score.getUserId());
        }

        return response;
    }
}
