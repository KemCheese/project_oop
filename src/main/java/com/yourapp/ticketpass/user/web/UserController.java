package com.yourapp.ticketpass.user.web;

import com.yourapp.ticketpass.reputation.application.UpdateReputationUseCase;
import com.yourapp.ticketpass.reputation.domain.ReputationTier;
import com.yourapp.ticketpass.reputation.domain.UserReputationScore;
import com.yourapp.ticketpass.shared.exception.BusinessRuleViolationException;
import com.yourapp.ticketpass.shared.exception.ResourceNotFoundException;
import com.yourapp.ticketpass.user.application.AccountSecurityService;
import com.yourapp.ticketpass.user.application.RegisterUserUseCase;
import com.yourapp.ticketpass.user.application.StarredTicketUseCase;
import com.yourapp.ticketpass.user.application.UserProfileService;
import com.yourapp.ticketpass.user.application.UserQueryService;
import com.yourapp.ticketpass.user.domain.User;
import com.yourapp.ticketpass.user.web.dto.ChangePasswordRequest;
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
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import com.yourapp.ticketpass.shared.security.SmartAuthenticationSuccessHandler;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Controller
@RequiredArgsConstructor
public class UserController {

    private final RegisterUserUseCase registerUserUseCase;

    private final UserProfileService userProfileService;

    private final UserQueryService userQueryService;

    private final UpdateReputationUseCase updateReputationUseCase;

    private final StarredTicketUseCase starredTicketUseCase;

    private final AccountSecurityService accountSecurityService;

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

    @GetMapping("/profile")
    public String profile(Principal principal, Model model) {
        Long userId = resolveUserId(principal);
        populateProfileModel(model, userId);
        return "user/profile";
    }

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

    private Long resolveUserId(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            throw new BusinessRuleViolationException("Phiên đăng nhập không hợp lệ.");
        }
        String email = principal.getName();
        User user = userQueryService.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email=" + email));
        return user.getId();
    }

    private void populateProfileModel(Model model, Long userId) {
        UserProfileResponse profile = userProfileService.getProfile(userId);

        UserReputationScore rep = updateReputationUseCase.getOrCreate(userId);
        profile.setReputationScore(rep.getScore());
        ReputationTier tier = ReputationTier.fromScore(rep.getScore());
        profile.setReputationTierName(tier.getDisplayName());
        profile.setReputationTierColor(tier.getBadgeColor());
        profile.setReputationLimitDescription(tier.getLimitDescription());
        profile.setTotalTransactions(rep.getTotalTransactions());

        String maskedBankAccount = maskBankAccount(profile.getBankAccountNumber());

        model.addAttribute("profile", profile);
        model.addAttribute("maskedBankAccount", maskedBankAccount);

        List<StarredTicketItemResponse> starredTickets = starredTicketUseCase.getStarredTicketsForUser(userId);
        model.addAttribute("starredTickets", starredTickets);
        model.addAttribute("starredCount", starredTickets.size());

        if (!model.containsAttribute("updateProfileRequest")) {
            UpdateProfileRequest updateProfileRequest = new UpdateProfileRequest();
            updateProfileRequest.setFullName(profile.getFullName());
            updateProfileRequest.setPhoneNumber(profile.getPhoneNumber());
            model.addAttribute("updateProfileRequest", updateProfileRequest);
        }

        if (!model.containsAttribute("updateBankAccountRequest")) {
            UpdateBankAccountRequest updateBankAccountRequest = new UpdateBankAccountRequest();
            updateBankAccountRequest.setBankName(profile.getBankName());
            updateBankAccountRequest.setBankAccountHolder(profile.getBankAccountHolder());
            model.addAttribute("updateBankAccountRequest", updateBankAccountRequest);
        }

        if (!model.containsAttribute("changePasswordRequest")) {
            model.addAttribute("changePasswordRequest", new ChangePasswordRequest());
        }
    }

    @PostMapping("/profile/password")
    public String changePassword(@Valid @ModelAttribute("changePasswordRequest") ChangePasswordRequest changePasswordRequest,
                                 BindingResult bindingResult,
                                 HttpServletRequest httpRequest,
                                 Principal principal,
                                 Model model) {
        Long userId = resolveUserId(principal);
        if (bindingResult.hasErrors()) {
            populateProfileModel(model, userId);
            return "user/profile";
        }

        HttpSession currentSession = httpRequest != null ? httpRequest.getSession(false) : null;
        String currentSessionId = currentSession != null ? currentSession.getId() : null;

        try {
            accountSecurityService.changePassword(userId, changePasswordRequest, currentSessionId);
            return "redirect:/profile?passwordUpdated#password-section";
        } catch (AccountSecurityService.PasswordChangeValidationException ex) {
            bindingResult.rejectValue(ex.getFieldName(), "password.invalid", ex.getMessage());
            populateProfileModel(model, userId);
            return "user/profile";
        } catch (BusinessRuleViolationException ex) {
            model.addAttribute("passwordErrorMessage", ex.getMessage());
            populateProfileModel(model, userId);
            return "user/profile";
        }
    }

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
