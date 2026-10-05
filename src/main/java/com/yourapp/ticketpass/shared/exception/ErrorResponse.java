package com.yourapp.ticketpass.shared.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Cấu trúc dữ liệu phản hồi lỗi JSON chuẩn hóa cho toàn sàn TicketPass.
 *
 * <p>Được trả về khi các yêu cầu API, AJAX hoặc các webhook callback gặp lỗi,
 * giúp các bên gọi dễ dàng phân tích nguyên nhân mà không bị nhận về mã HTML của trang web.</p>
 *
 * <p>Module sở hữu: shared.</p>
 * <p>Chủ sở hữu: Người C.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    /** Mã trạng thái HTTP (ví dụ: 400, 403, 404, 500). */
    private int status;

    /** Tên định danh chuẩn của lỗi HTTP (ví dụ: "Bad Request", "Not Found"). */
    private String error;

    /** Thông điệp lỗi chi tiết, thân thiện với người dùng/lập trình viên. */
    private String message;

    /** Đường dẫn API/URL xảy ra lỗi. */
    private String path;

    /** Thời điểm phát sinh lỗi. */
    private Instant timestamp;

    /** Danh sách các lỗi chi tiết theo từng trường (nếu là lỗi kiểm thực dữ liệu Bean Validation). */
    private List<String> fieldErrors;
}
