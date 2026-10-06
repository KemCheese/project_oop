package com.yourapp.ticketpass.system.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.test.context.support.WithMockUser;

import com.yourapp.ticketpass.system.base.AbstractSystemIntegrationTest;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Phi Chức Năng - Bảo Mật 1: Phân Quyền URL & Kiểm Soát Truy Cập RBAC (Spring Security)")
class SecurityRbacAndAccessControlTest extends AbstractSystemIntegrationTest {

    @Test
    @DisplayName("Khách vãng lai xem công khai GET /tickets và GET /reputation/top mà không cần đăng nhập")
    void guestCanAccessPublicEndpoints() throws Exception {
        mockMvc.perform(get("/tickets"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/reputation/top"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Khách vãng lai cố tình gọi POST /tickets tạo vé -> Bị chuyển hướng về trang /login (SEC-02)")
    void guestCannotPostTicketsWithoutAuthentication() throws Exception {
        mockMvc.perform(post("/tickets").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    @DisplayName("Khách vãng lai truy cập /profile hoặc /admin/disputes -> Bị chuyển hướng về /login")
    void guestCannotAccessProtectedProfilesOrAdminRoutes() throws Exception {
        mockMvc.perform(get("/profile"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));

        mockMvc.perform(get("/admin/disputes"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    @WithMockUser(username = "regular_user@test.com", roles = "USER")
    @DisplayName("Người dùng có role USER cố tình truy cập tuyến quản trị /admin/disputes -> Bị từ chối 403 Forbidden")
    void userRoleIsForbiddenFromAdminEndpoints() throws Exception {
        mockMvc.perform(get("/admin/disputes"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin_user@test.com", roles = "ADMIN")
    @DisplayName("Quản trị viên có role ADMIN truy cập tuyến /admin/disputes -> Thành công 200 OK")
    void adminRoleCanAccessAdminEndpoints() throws Exception {
        mockMvc.perform(get("/admin/disputes"))
                .andExpect(status().isOk());
    }
}
