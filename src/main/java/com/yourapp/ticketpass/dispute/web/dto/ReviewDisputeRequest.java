package com.yourapp.ticketpass.dispute.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Dữ liệu admin gửi lên khi ra phán quyết cho một khiếu nại.
 *
 * <p>Kết quả phán quyết (người mua thắng hay người bán thắng) được xác định bởi tuyến đường
 * ({@code /buyer-wins} hoặc {@code /seller-wins}), không nằm trong DTO — nhờ vậy hai hành động
 * có hệ quả tiền tệ khác nhau không thể bị nhầm lẫn do một trường dữ liệu gửi sai.</p>
 *
 * <p>Ghi chú phán quyết là bắt buộc: cả người mua và người bán đều đọc được ghi chú này, nên
 * mọi quyết định phải có giải thích.</p>
 *
 * <p>Module: dispute</p>
 *
 * <p>Chủ sở hữu: Người B</p>
 */
@Getter
@Setter
@NoArgsConstructor
public class ReviewDisputeRequest {

    /** Ghi chú giải thích căn cứ phán quyết của admin. */
    @NotBlank
    @Size(max = 2000)
    private String resolutionNote;
}
