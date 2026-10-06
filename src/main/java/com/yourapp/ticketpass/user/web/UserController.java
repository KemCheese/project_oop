package com.yourapp.ticketpass.user.web;

import com.yourapp.ticketpass.reputation.application.UpdateReputationUseCase;
import com.yourapp.ticketpass.reputation.domain.UserReputationScore;
import com.yourapp.ticketpass.shared.exception.BusinessRuleViolationException;
import com.yourapp.ticketpass.shared.exception.ResourceNotFoundException;
import com.yourapp.ticketpass.user.application.RegisterUserUseCase;
import com.yourapp.ticketpass.user.application.StarredTicketUseCase;
import com.yourapp.ticketpass.user.application.UserProfileService;
import com.yourapp.ticketpass.user.application.UserQueryService;
import com.yourapp.ticketpass.user.domain.User;
import com.yourapp.ticketpass.user.web.dto.RegisterRequest;
import com.yourapp.ticketpass.user.web.dto.StarredTicketItemResponse;
import com.yourapp.ticketpass.user.web.dto.UpdateBankAccountRequest;
import com.yourapp.ticketpass.user.web.dto.UpdateProfileRequest;
import com.yourapp.ticketpass.user.web.dto.UserProfileResponse;
import jakarta.validation.Valid;
import java.io.IOException;
import java.io.InputStream;
import java.security.Principal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import com.yourapp.ticketpass.shared.security.SmartAuthenticationSuccessHandler;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.web.multipart.MultipartFile;

/**
 * Controller phục vụ các màn hình đăng ký, đăng nhập và hồ sơ cá nhân.
 *
 * <p>
 * Bảng route:
 * </p>
 * 
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
 * <p>
 * <b>Lưu ý quan trọng:</b> {@code POST /login} <b>KHÔNG</b> được khai báo ở
 * đây. Việc xác
 * thực do filter của Spring Security (cấu hình {@code formLogin} trong
 * {@code shared.config.SecurityConfig}) đảm nhiệm; controller chỉ trả về trang
 * chứa form.
 * Tương tự, {@code POST /logout} cũng do Spring Security xử lý.
 * </p>
 *
 * <p>
 * Tầng {@code web} là nơi lắp thêm
 * {@code reputationScore}/{@code totalTransactions} từ
 * module {@code reputation} vào {@code UserProfileResponse} trước khi đẩy ra
 * view.
 * </p>
 *
 * <p>
 * <b>RR-4 (IDOR Prevention):</b> Mọi endpoint profile đều lấy {@code userId} từ
 * {@code resolveUserId(principal)} — KHÔNG BAO GIỜ nhận {@code userId} từ URL
 * path hay
 * request parameter. Đảm bảo người dùng chỉ truy cập/sửa đổi hồ sơ chính mình.
 * </p>
 *
 * <p>
 * Module: user
 * </p>
 *
 * Chủ sở hữu: Người C
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class UserController {

    private final RegisterUserUseCase registerUserUseCase;

    private final UserProfileService userProfileService;

    private final UserQueryService userQueryService;

    private final UpdateReputationUseCase updateReputationUseCase;

    private final StarredTicketUseCase starredTicketUseCase;

    /**
     * Hiển thị form đăng nhập.
     *
     * @param model model của view
     * @return tên view {@code auth/login}
     */
    @GetMapping("/login")
    public String loginForm(@RequestParam(value = "redirect", required = false) String redirect,
                            HttpServletRequest request,
                            Model model) {
        saveRedirectTarget(redirect, request, model);
        return "auth/login";
    }

    public String loginForm(Model model) {
        return loginForm(null, null, model);
    }

    /**
     * Hiển thị form đăng ký.
     *
     * @param redirect URL đích muốn quay lại sau khi đăng ký thành công
     * @param request  yêu cầu HTTP chứa header Referer
     * @param model    model của view
     * @return tên view {@code auth/register}
     */
    @GetMapping("/register")
    public String registerForm(@RequestParam(value = "redirect", required = false) String redirect,
                               HttpServletRequest request,
                               Model model) {
        saveRedirectTarget(redirect, request, model);
        model.addAttribute("registerRequest", new RegisterRequest());
        return "auth/register";
    }

    public String registerForm(Model model) {
        return registerForm(null, null, model);
    }

    /**
     * Xử lý submit form đăng ký.
     *
     * <p>
     * Bắt {@code BusinessRuleViolationException} từ {@code RegisterUserUseCase} và
     * phân luồng lỗi vào đúng field tương ứng trên form.
     * Sau khi đăng ký thành công, bảo lưu tham số {@code redirect} để tự động quay lại
     * đúng trang người dùng đang xem dở sau khi đăng nhập.
     * </p>
     *
     * @param request       dữ liệu đăng ký
     * @param bindingResult kết quả Bean Validation
     * @param redirect      tham số URL chuyển hướng
     * @param httpRequest   request HTTP để lấy session attribute
     * @return redirect sang trang đăng nhập khi thành công, hoặc trả lại form khi có lỗi
     */
    @PostMapping("/register")
    public String register(@Valid RegisterRequest request,
                           BindingResult bindingResult,
                           @RequestParam(value = "redirect", required = false) String redirect,
                           HttpServletRequest httpRequest) {
        if (bindingResult.hasErrors()) {
            return "auth/register";
        }

        try {
            registerUserUseCase.register(request);
        } catch (BusinessRuleViolationException ex) {
            String message = ex.getMessage();
            if (message != null && message.contains("Email")) {
                bindingResult.rejectValue("email", "error.email", message);
            } else if (message != null && message.contains("Mật khẩu")) {
                bindingResult.rejectValue("confirmPassword", "error.confirmPassword", message);
            } else {
                bindingResult.reject("error.register", message);
            }
            return "auth/register";
        }

        String target = redirect;
        if ((target == null || target.isBlank()) && httpRequest != null) {
            HttpSession session = httpRequest.getSession(false);
            if (session != null) {
                target = (String) session.getAttribute(SmartAuthenticationSuccessHandler.REDIRECT_ATTRIBUTE);
            }
        }

        if (target != null && SmartAuthenticationSuccessHandler.isValidRedirect(target)) {
            return "redirect:/login?registered&redirect=" + URLEncoder.encode(target, StandardCharsets.UTF_8);
        }

        return "redirect:/login?registered";
    }

    public String register(RegisterRequest request, BindingResult bindingResult) {
        return register(request, bindingResult, null, null);
    }

    private void saveRedirectTarget(String redirect, HttpServletRequest request, Model model) {
        String target = redirect;
        if ((target == null || target.isBlank()) && request != null) {
            String referer = request.getHeader("Referer");
            if (referer != null && !referer.isBlank()) {
                try {
                    URI uri = URI.create(referer);
                    String path = uri.getPath();
                    if (path != null && !path.isBlank()) {
                        if (uri.getQuery() != null && !uri.getQuery().isBlank()) {
                            path += "?" + uri.getQuery();
                        }
                        if (SmartAuthenticationSuccessHandler.isValidRedirect(path)) {
                            target = path;
                        }
                    }
                } catch (Exception ignored) {
                }
            }
        }
        if (target != null && SmartAuthenticationSuccessHandler.isValidRedirect(target)) {
            if (request != null) {
                request.getSession(true).setAttribute(SmartAuthenticationSuccessHandler.REDIRECT_ATTRIBUTE, target);
            }
            if (model != null) {
                model.addAttribute("redirect", target);
            }
        }
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
        Long userId = resolveUserId(principal);
        populateProfileModel(model, userId);
        return "user/profile";
    }

    /**
     * Cập nhật thông tin cá nhân.
     *
     * @param request       dữ liệu mới
     * @param bindingResult kết quả Bean Validation
     * @param principal     người dùng đang đăng nhập
     * @param model         model của view (cần khi có lỗi validation)
     * @return redirect về trang hồ sơ
     */
    @PostMapping("/profile")
    public String updateProfile(@Valid UpdateProfileRequest request, BindingResult bindingResult,
            Principal principal, Model model) {
        Long userId = resolveUserId(principal);

        if (bindingResult.hasErrors()) {
            if (!model.containsAttribute("updateProfileRequest")) {
                model.addAttribute("updateProfileRequest", request);
            }
            populateProfileModel(model, userId);
            return "user/profile";
        }

        userProfileService.updateProfile(userId, request);
        return "redirect:/profile?updated";
    }

    /**
     * Tải lên ảnh đại diện mới.
     *
     * <p>
     * Sử dụng try-with-resources để tự động đóng {@code InputStream} an toàn.
     * Ủy quyền hoàn toàn cho {@code UserProfileService.updateAvatar()} tương tác
     * với
     * {@code FileStorageService} (Adapter Pattern).
     * </p>
     *
     * @param avatar    tệp ảnh người dùng chọn
     * @param principal người dùng đang đăng nhập
     * @return redirect về trang hồ sơ
     */
    @PostMapping("/profile/avatar")
    public String updateAvatar(@RequestParam("avatar") MultipartFile avatar, Principal principal) {
        if (avatar == null || avatar.isEmpty()) {
            return "redirect:/profile?avatarError=empty";
        }

        Long userId = resolveUserId(principal);

        try (InputStream is = avatar.getInputStream()) {
            userProfileService.updateAvatar(
                    userId,
                    avatar.getOriginalFilename(),
                    avatar.getContentType(),
                    avatar.getSize(),
                    is);
        } catch (BusinessRuleViolationException ex) {
            log.warn("Avatar upload bị từ chối cho userId={}: {}", userId, ex.getMessage());
            return "redirect:/profile?avatarError";
        } catch (IOException ex) {
            log.error("Lỗi I/O khi upload avatar cho userId={}", userId, ex);
            return "redirect:/profile?avatarError";
        }

        return "redirect:/profile?avatarUpdated";
    }

    /**
     * Cập nhật tài khoản ngân hàng nhận tiền.
     *
     * @param request       thông tin ngân hàng
     * @param bindingResult kết quả Bean Validation
     * @param principal     người dùng đang đăng nhập
     * @param model         model của view (cần khi có lỗi validation)
     * @return redirect về trang hồ sơ
     */
    @PostMapping("/profile/bank")
    public String updateBankAccount(@Valid UpdateBankAccountRequest request, BindingResult bindingResult,
            Principal principal, Model model) {
        Long userId = resolveUserId(principal);

        if (bindingResult.hasErrors()) {
            if (!model.containsAttribute("updateBankAccountRequest")) {
                model.addAttribute("updateBankAccountRequest", request);
            }
            populateProfileModel(model, userId);
            return "user/profile";
        }

        userProfileService.updateBankAccount(
                userId,
                request.getBankName(),
                request.getBankAccountNumber(),
                request.getBankAccountHolder());
        return "redirect:/profile?bankUpdated";
    }

    // ═══════════════════════════ HELPER METHODS ════════════════════════

    /**
     * Tra cứu userId từ Principal đang đăng nhập.
     *
     * <p>
     * <b>RR-4 (IDOR Prevention):</b> Đây là điểm tập trung duy nhất để lấy userId.
     * Mọi endpoint profile đều gọi method này thay vì nhận userId từ request
     * parameter.
     * </p>
     *
     * @param principal người dùng đang đăng nhập (email = principal.getName())
     * @return userId của người dùng
     * @throws ResourceNotFoundException nếu không tìm thấy user (phiên đăng nhập
     *                                   bất thường)
     */
    private Long resolveUserId(Principal principal) {
        String email = principal.getName();
        User user = userQueryService.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email=" + email));
        return user.getId();
    }

    /**
     * Nạp dữ liệu chung vào Model cho trang hồ sơ cá nhân.
     *
     * <p>
     * Thực hiện 3 việc:
     * <ol>
     * <li>Lấy {@code UserProfileResponse} từ {@code UserProfileService}.</li>
     * <li>Lắp thêm {@code reputationScore} và {@code totalTransactions} từ module
     * {@code reputation} qua {@code UpdateReputationUseCase.getOrCreate(userId)}.
     * <b>RR-2 (Side-effect trong GET):</b> {@code getOrCreate} có thể INSERT bản
     * ghi
     * mới nếu chưa tồn tại, nhưng Task C6 đã tạo sẵn khi đăng ký → nhánh tạo mới
     * gần như không xảy ra. Hành vi idempotent, chấp nhận trade-off cho đơn
     * giản.</li>
     * <li>Masking số tài khoản ngân hàng theo <b>RR-1</b> và <b>RR-5</b>.</li>
     * </ol>
     *
     * @param model  model của view
     * @param userId id người dùng đang đăng nhập
     */
    private void populateProfileModel(Model model, Long userId) {
        UserProfileResponse profile = userProfileService.getProfile(userId);

        // Lắp thêm thông tin uy tín từ module reputation (Decoupling - tầng web ghép
        // data)
        // RR-2: getOrCreate có @Transactional, có thể INSERT nếu chưa có bản ghi.
        // Task C6 đã tạo sẵn khi đăng ký → nhánh tạo mới gần như không bao giờ kích
        // hoạt.
        UserReputationScore rep = updateReputationUseCase.getOrCreate(userId);
        profile.setReputationScore(rep.getScore());
        profile.setTotalTransactions(rep.getTotalTransactions());

        // RR-1 & RR-5: Masking số tài khoản ngân hàng trước khi đẩy ra view
        String maskedBankAccount = maskBankAccount(profile.getBankAccountNumber());

        model.addAttribute("profile", profile);
        model.addAttribute("maskedBankAccount", maskedBankAccount);

        // Danh sách vé theo dõi (Watchlist / Starred tickets) của người dùng
        List<StarredTicketItemResponse> starredTickets = starredTicketUseCase.getStarredTicketsForUser(userId);
        model.addAttribute("starredTickets", starredTickets);
        model.addAttribute("starredCount", starredTickets.size());

        // Pre-fill form thông tin cá nhân nếu model chưa có (ví dụ khi GET /profile).
        // Nếu đã có (khi POST /profile bị lỗi validation), giữ nguyên request của user để hiển thị lỗi.
        if (!model.containsAttribute("updateProfileRequest")) {
            UpdateProfileRequest updateProfileRequest = new UpdateProfileRequest();
            updateProfileRequest.setFullName(profile.getFullName());
            updateProfileRequest.setPhoneNumber(profile.getPhoneNumber());
            model.addAttribute("updateProfileRequest", updateProfileRequest);
        }

        // Pre-fill form thông tin ngân hàng nếu model chưa có (ví dụ khi GET /profile).
        // RR-5: Không pre-fill bankAccountNumber, chỉ điền bankName và bankAccountHolder.
        if (!model.containsAttribute("updateBankAccountRequest")) {
            UpdateBankAccountRequest updateBankAccountRequest = new UpdateBankAccountRequest();
            updateBankAccountRequest.setBankName(profile.getBankName());
            updateBankAccountRequest.setBankAccountHolder(profile.getBankAccountHolder());
            model.addAttribute("updateBankAccountRequest", updateBankAccountRequest);
        }
    }

    /**
     * Che dấu số tài khoản ngân hàng: chỉ hiện 4 ký tự cuối, phần còn lại thay bằng
     * {@code *}.
     *
     * <p>
     * <b>RR-1 & RR-5:</b> Số tài khoản ngân hàng là dữ liệu nhạy cảm, không được
     * hiển thị
     * nguyên văn trên HTML để phòng tránh rò rỉ khi người dùng mở trên máy tính
     * công cộng
     * hoặc bị chụp ảnh màn hình.
     * </p>
     *
     * @param raw số tài khoản gốc
     * @return chuỗi đã che dấu, ví dụ {@code "****5678"}, hoặc {@code null} nếu
     *         chưa khai báo
     */
    private String maskBankAccount(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        if (raw.length() <= 4) {
            return raw;
        }
        String lastFour = raw.substring(raw.length() - 4);
        return "*".repeat(raw.length() - 4) + lastFour;
    }
}
