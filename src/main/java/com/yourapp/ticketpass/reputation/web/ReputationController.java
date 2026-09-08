package com.yourapp.ticketpass.reputation.web;

import com.yourapp.ticketpass.reputation.application.UpdateReputationUseCase;
import com.yourapp.ticketpass.reputation.domain.ReputationRepository;
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
 * <p>Bảng route:</p>
 * <pre>
 * GET /reputation/{userId}  -&gt; ho so uy tin cong khai (view "reputation/detail")
 * GET /reputation/top       -&gt; bang xep hang seller (view "reputation/top")
 * </pre>
 *
 * <p>Hai view {@code reputation/detail} và {@code reputation/top} sẽ do Prompt 2 tạo.
 * Controller chỉ trả về {@code ReputationResponse} chứ không đẩy entity ra view.</p>
 *
 * <p>Module: reputation</p>
 *
 * Chủ sở hữu: Người C
 */
@Controller
@RequestMapping("/reputation")
@RequiredArgsConstructor
public class ReputationController {

    private final ReputationRepository reputationRepository;

    private final UpdateReputationUseCase updateReputationUseCase;

    /**
     * Hiển thị hồ sơ uy tín công khai của một người dùng.
     *
     * @param userId id người dùng cần xem
     * @param model  model của view
     * @return tên view {@code reputation/detail}
     */
    @GetMapping("/{userId}")
    public String detail(@PathVariable Long userId, Model model) {
        throw new UnsupportedOperationException(
                "TODO [Người C] Prompt 2: gọi reputationRepository.findByUserId(userId), nếu chưa có "
                        + "bản ghi thì dùng updateReputationUseCase.getOrCreate(userId) hoặc hiển thị "
                        + "điểm mặc định 100.00; map sang ReputationResponse, đưa vào model rồi trả về "
                        + "view \"reputation/detail\".");
    }

    /**
     * Hiển thị bảng xếp hạng người bán theo điểm uy tín.
     *
     * @param limit số người bán hiển thị, mặc định 10
     * @param model model của view
     * @return tên view {@code reputation/top}
     */
    @GetMapping("/top")
    public String topSellers(@RequestParam(defaultValue = "10") int limit, Model model) {
        throw new UnsupportedOperationException(
                "TODO [Người C] Prompt 2: chặn limit trong khoảng hợp lệ (1-100), gọi "
                        + "reputationRepository.findTopSellers(limit), map sang danh sách "
                        + "ReputationResponse, đưa vào model rồi trả về view \"reputation/top\".");
    }
}
