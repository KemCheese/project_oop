package com.yourapp.ticketpass.user.web;

import com.yourapp.ticketpass.user.application.RegisterUserUseCase;
import com.yourapp.ticketpass.user.application.UserProfileService;
import com.yourapp.ticketpass.user.web.dto.RegisterRequest;
import com.yourapp.ticketpass.user.web.dto.UpdateBankAccountRequest;
import com.yourapp.ticketpass.user.web.dto.UpdateProfileRequest;
import jakarta.validation.Valid;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

/**
 * Controller phục vụ các màn hình đăng ký, đăng nhập và hồ sơ cá nhân.
 *
 * <p>Bảng route:</p>
 * <pre>
 * GET  /login            -&gt; view "auth/login"     (chi hien thi form dang nhap)
 * GET  /register         -&gt; view "auth/register"
 * POST /register         -&gt; xu ly RegisterRequest da validate
 * GET  /profile          -&gt; view "user/profile"
 * POST /profile          -&gt; cap nhat UpdateProfileRequest
 * POST /profile/avatar   -&gt; tai anh dai dien (MultipartFile)
 * POST /profile/bank     -&gt; cap nhat UpdateBankAccountRequest
 * </pre>
 *
 * <p><b>Lưu ý quan trọng:</b> {@code POST /login} <b>KHÔNG</b> được khai báo ở đây. Việc xác
 * thực do filter của Spring Security (cấu hình {@code formLogin} trong
 * {@code shared.config.SecurityConfig}) đảm nhiệm; controller chỉ trả về trang chứa form.
 * Tương tự, {@code POST /logout} cũng do Spring Security xử lý.</p>
 *
 * <p>Tầng {@code web} là nơi lắp thêm {@code reputationScore}/{@code totalTransactions} từ
 * module {@code reputation} vào {@code UserProfileResponse} trước khi đẩy ra view.</p>
 *
 * <p>Module: user</p>
 *
 * Chủ sở hữu: Người C
 */
@Controller
@RequiredArgsConstructor
public class UserController {

    private final RegisterUserUseCase registerUserUseCase;

    private final UserProfileService userProfileService;

    /**
     * Hiển thị form đăng nhập.
     *
     * @param model model của view
     * @return tên view {@code auth/login}
     */
    @GetMapping("/login")
    public String loginForm(Model model) {
        return "auth/login";
    }

    /**
     * Hiển thị form đăng ký.
     *
     * @param model model của view
     * @return tên view {@code auth/register}
     */
    @GetMapping("/register")
    public String registerForm(Model model) {
        model.addAttribute("registerRequest", new RegisterRequest());
        return "auth/register";
    }

    /**
     * Xử lý submit form đăng ký.
     *
     * @param request       dữ liệu đăng ký
     * @param bindingResult kết quả Bean Validation
     * @return redirect sang trang đăng nhập khi thành công, hoặc trả lại form khi có lỗi
     */
    @PostMapping("/register")
    public String register(@Valid RegisterRequest request, BindingResult bindingResult) {
        throw new UnsupportedOperationException(
                "TODO [Người C] Prompt 2: nếu bindingResult.hasErrors() thì trả lại view "
                        + "\"auth/register\"; ngược lại gọi registerUserUseCase.register(request), "
                        + "bắt BusinessRuleViolationException (email trùng, mật khẩu không khớp) để "
                        + "đẩy lỗi vào bindingResult, thành công thì redirect:/login?registered.");
    }

    /**
     * Hiển thị trang hồ sơ cá nhân.
     *
     * @param principal người dùng đang đăng nhập
     * @param model     model của view
     * @return tên view {@code user/profile}
     */
    @GetMapping("/profile")
    public String profile(Principal principal, Model model) {
        throw new UnsupportedOperationException(
                "TODO [Người C] Prompt 2: lấy email từ principal.getName(), tra userId qua "
                        + "UserQueryService, gọi userProfileService.getProfile(userId), lắp thêm "
                        + "reputationScore/totalTransactions từ module reputation, đưa vào model rồi "
                        + "trả về view \"user/profile\" (view do Prompt 2 tạo).");
    }

    /**
     * Cập nhật thông tin cá nhân.
     *
     * @param request       dữ liệu mới
     * @param bindingResult kết quả Bean Validation
     * @param principal     người dùng đang đăng nhập
     * @return redirect về trang hồ sơ
     */
    @PostMapping("/profile")
    public String updateProfile(@Valid UpdateProfileRequest request, BindingResult bindingResult, Principal principal) {
        throw new UnsupportedOperationException(
                "TODO [Người C] Prompt 2: nếu có lỗi validate thì trả lại view \"user/profile\"; "
                        + "ngược lại tra userId từ principal, gọi "
                        + "userProfileService.updateProfile(userId, request) rồi redirect:/profile?updated.");
    }

    /**
     * Tải lên ảnh đại diện mới.
     *
     * @param avatar    tệp ảnh người dùng chọn
     * @param principal người dùng đang đăng nhập
     * @return redirect về trang hồ sơ
     */
    @PostMapping("/profile/avatar")
    public String updateAvatar(@RequestParam("avatar") MultipartFile avatar, Principal principal) {
        throw new UnsupportedOperationException(
                "TODO [Người C] Prompt 2: kiểm tra avatar không rỗng, tra userId từ principal, gọi "
                        + "userProfileService.updateAvatar(userId, avatar.getOriginalFilename(), "
                        + "avatar.getContentType(), avatar.getSize(), avatar.getInputStream()) trong "
                        + "try-with-resources, xử lý IOException thành thông báo lỗi rồi redirect:/profile.");
    }

    /**
     * Cập nhật tài khoản ngân hàng nhận tiền.
     *
     * @param request       thông tin ngân hàng
     * @param bindingResult kết quả Bean Validation
     * @param principal     người dùng đang đăng nhập
     * @return redirect về trang hồ sơ
     */
    @PostMapping("/profile/bank")
    public String updateBankAccount(@Valid UpdateBankAccountRequest request, BindingResult bindingResult, Principal principal) {
        throw new UnsupportedOperationException(
                "TODO [Người C] Prompt 2: nếu có lỗi validate thì trả lại view \"user/profile\"; "
                        + "ngược lại tra userId từ principal, gọi userProfileService.updateBankAccount("
                        + "userId, request.getBankName(), request.getBankAccountNumber(), "
                        + "request.getBankAccountHolder()) rồi redirect:/profile?bankUpdated.");
    }
}
