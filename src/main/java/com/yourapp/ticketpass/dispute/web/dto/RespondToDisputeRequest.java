package com.yourapp.ticketpass.dispute.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Dữ liệu người bán gửi lên khi giải trình một khiếu nại.
 *
 * <p>ID khiếu nại nằm ở đường dẫn ({@code /disputes/{id}/respond}), còn ID người bán được lấy
 * từ phiên đăng nhập, nên DTO chỉ chứa nội dung giải trình.</p>
 *
 * <p>Module: dispute</p>
 *
 * <p>Chủ sở hữu: Người B</p>
 */
@Getter
@Setter
@NoArgsConstructor
public class RespondToDisputeRequest {

    /** Nội dung giải trình của người bán. */
    @NotBlank
    @Size(max = 2000)
    private String response;
}
