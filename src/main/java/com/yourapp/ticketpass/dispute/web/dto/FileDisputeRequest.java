package com.yourapp.ticketpass.dispute.web.dto;

import com.yourapp.ticketpass.dispute.domain.DisputeReason;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Dữ liệu người mua gửi lên khi mở khiếu nại.
 *
 * <p>ID người mua KHÔNG nằm trong DTO này: nó được lấy từ phiên đăng nhập ở controller, nếu
 * không thì một người có thể mở khiếu nại nhân danh người khác.</p>
 *
 * <p>Tệp bằng chứng được gửi kèm dưới dạng {@code MultipartFile[]} riêng ở tham số của
 * controller, không nằm trong DTO này.</p>
 *
 * <p>Module: dispute</p>
 *
 * <p>Chủ sở hữu: Người B</p>
 */
@Getter
@Setter
@NoArgsConstructor
public class FileDisputeRequest {

    /** ID vé bị khiếu nại. */
    @NotNull
    private Long ticketId;

    /** Lý do khiếu nại, người mua chọn từ danh sách có sẵn. */
    @NotNull
    private DisputeReason reason;

    /** Mô tả chi tiết vấn đề; bắt buộc để admin có căn cứ xem xét. */
    @NotBlank
    @Size(max = 2000)
    private String description;
}
