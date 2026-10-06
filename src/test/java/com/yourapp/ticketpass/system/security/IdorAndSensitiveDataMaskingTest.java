package com.yourapp.ticketpass.system.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.test.context.support.WithMockUser;

import com.yourapp.ticketpass.system.base.AbstractSystemIntegrationTest;
import com.yourapp.ticketpass.user.domain.User;
import com.yourapp.ticketpass.user.domain.UserRole;
import com.yourapp.ticketpass.user.web.dto.UserProfileResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Phi Chức Năng - Bảo Mật 2: Chống IDOR, Che Dữ Liệu Ngân Hàng (Data Masking) & Ẩn Doanh Số GMV")
class IdorAndSensitiveDataMaskingTest extends AbstractSystemIntegrationTest {

    @Test
    @WithMockUser(username = "victim_user@test.com", roles = "USER")
    @DisplayName("Chống IDOR & Data Masking: User chỉ xem hồ sơ của mình từ Principal, STK ngân hàng bị che chỉ hiện 4 số cuối")
    void shouldPreventIdorAndMaskBankAccountInProfile() throws Exception {
        // Arrange: Tạo User nạn nhân
        User victim = createAndSaveUser("victim_user@test.com", "Password123@", UserRole.USER);
        victim.setBankAccountNumber("190367891234");
        userRepository.save(victim);

        // Act & Assert: Gọi GET /profile
        mockMvc.perform(get("/profile"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("profile"))
                .andExpect(model().attributeExists("maskedBankAccount"))
                .andExpect(result -> {
                    UserProfileResponse profile = (UserProfileResponse) result.getModelAndView().getModel().get("profile");
                    assertThat(profile.getEmail()).isEqualTo("victim_user@test.com");

                    // Kiểm tra Data Masking 4 số cuối trong model maskedBankAccount
                    String maskedBankAccount = (String) result.getModelAndView().getModel().get("maskedBankAccount");
                    assertThat(maskedBankAccount).isNotNull();
                    assertThat(maskedBankAccount).endsWith("1234");
                    assertThat(maskedBankAccount).doesNotContain("19036789");
                });
    }

    @Test
    @DisplayName("Bảo vệ dữ liệu nhạy cảm (PRIV-01): Xem điểm uy tín công khai GET /reputation/{id} không làm rò rỉ doanh số GMV")
    void shouldNotExposeSensitiveGmvInPublicReputationView() throws Exception {
        // Arrange
        User seller = createAndSaveUser("public_seller@test.com", "Password123@", UserRole.USER);

        // Act & Assert
        mockMvc.perform(get("/reputation/" + seller.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("totalGmv"))))
                .andExpect(content().string(not(containsString("gmv"))));
    }
}
