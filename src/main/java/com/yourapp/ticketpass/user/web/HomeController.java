package com.yourapp.ticketpass.user.web;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Controller trang chủ của sàn.
 *
 * <p>Đây là route duy nhất được hiện thực đầy đủ ở bước scaffold để ứng dụng có điểm vào
 * chạy được ngay.</p>
 *
 * <p><b>Lưu ý cho người làm Prompt 2:</b> method {@link #home()} trả về tên view
 * {@code "index"} nhưng file {@code src/main/resources/templates/index.html} <b>chưa được
 * tạo</b> ở bước scaffold (nằm ngoài phạm vi Prompt 1). Vì vậy khi chạy ứng dụng và mở
 * {@code GET /} sẽ gặp lỗi Thymeleaf kiểu "Error resolving template [index]" - đây là lỗi
 * đã được biết trước, không phải bug. Prompt 2 sẽ bổ sung {@code index.html} cùng các view
 * còn thiếu ({@code user/profile}, {@code ticket/*}, {@code dispute/*}, {@code admin/*},
 * {@code reputation/*}).</p>
 *
 * <p>Module: user</p>
 *
 * Chủ sở hữu: Người C
 */
@Controller
@RequiredArgsConstructor
public class HomeController {

    /**
     * Trang chủ của sàn pass vé.
     *
     * @return tên view {@code index}
     */
    @GetMapping("/")
    public String home() {
        return "index";
    }
}
