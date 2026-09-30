package com.yourapp.ticketpass.shared.exception;

import com.yourapp.ticketpass.shared.storage.FileStorageException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.ModelAndView;

/**
 * Xử lý ngoại lệ tập trung toàn hệ thống (Global Exception Handler).
 *
 * <p>Hoạt động theo cơ chế <b>Hybrid (Đa kênh)</b>:
 * <ul>
 *   <li>Nếu client gửi yêu cầu dạng API / AJAX (chứa header {@code Accept: application/json},
 *       {@code Content-Type: application/json} hoặc {@code X-Requested-With: XMLHttpRequest}):
 *       Trả về đối tượng {@link ResponseEntity}&lt;{@link ErrorResponse}&gt; dạng JSON chuẩn hóa.</li>
 *   <li>Nếu client duyệt web thông thường từ trình duyệt: Trả về {@link ModelAndView} render
 *       view {@code error/error} với mã trạng thái HTTP chuẩn và thông điệp lỗi thân thiện.
 *       (Tận dụng template sẵn có của Dev A, tôn trọng ranh giới phân công module).</li>
 * </ul>
 * </p>
 *
 * <p>Quy ước ghi log:
 * <ul>
 *   <li>Lỗi do người dùng hoặc vi phạm nghiệp vụ (4xx): Ghi mức {@code WARN}.</li>
 *   <li>Lỗi bảo mật (403): Ghi mức {@code WARN} kèm địa chỉ IP / URI để kiểm toán.</li>
 *   <li>Sự cố hạ tầng hoặc lỗi hệ thống không mong đợi (5xx): Ghi mức {@code ERROR} kèm stack trace để truy vết.</li>
 * </ul>
 * </p>
 *
 * <p>Module sở hữu: shared.</p>
 * <p>Chủ sở hữu: Người C.</p>
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Xử lý ngoại lệ khi không tìm thấy tài nguyên (HTTP 404).
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public Object handleResourceNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        log.warn("Khong tim thay tai nguyen tai URI [{}]: {}", getUri(request), ex.getMessage());
        return buildResponse(request, HttpStatus.NOT_FOUND, ex.getMessage(), null);
    }

    /**
     * Xử lý ngoại lệ khi thao tác vi phạm quy tắc nghiệp vụ sàn vé (HTTP 400).
     */
    @ExceptionHandler(BusinessRuleViolationException.class)
    public Object handleBusinessRuleViolation(BusinessRuleViolationException ex, HttpServletRequest request) {
        log.warn("Vi pham quy tac nghiep vu tai URI [{}]: {}", getUri(request), ex.getMessage());
        return buildResponse(request, HttpStatus.BAD_REQUEST, ex.getMessage(), null);
    }

    /**
     * Xử lý lỗi kiểm thực dữ liệu JSON/DTO (@Valid trên request body) (HTTP 400).
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Object handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<String> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(this::describeFieldError)
                .collect(Collectors.toList());
        String detail = String.join("; ", fieldErrors);
        log.warn("Du lieu JSON gui len khong hop le tai URI [{}]: {}", getUri(request), detail);
        return buildResponse(request, HttpStatus.BAD_REQUEST, "Dữ liệu gửi lên không hợp lệ: " + detail, fieldErrors);
    }

    /**
     * Xử lý lỗi liên kết dữ liệu Form / Query Params (BindException) (HTTP 400).
     */
    @ExceptionHandler(BindException.class)
    public Object handleBindException(BindException ex, HttpServletRequest request) {
        List<String> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(this::describeFieldError)
                .collect(Collectors.toList());
        String detail = String.join("; ", fieldErrors);
        log.warn("Du lieu Form gui len khong hop le tai URI [{}]: {}", getUri(request), detail);
        return buildResponse(request, HttpStatus.BAD_REQUEST, "Tham số gửi lên không đúng định dạng: " + detail, fieldErrors);
    }

    /**
     * Xử lý lỗi khi dung lượng tệp tin tải lên vượt quá giới hạn cấu hình (HTTP 400).
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Object handleMaxUploadSizeExceeded(MaxUploadSizeExceededException ex, HttpServletRequest request) {
        String msg = "Dung lượng tệp tin tải lên vượt quá giới hạn tối đa cho phép (tối đa 10MB).";
        log.warn("Vuot qua dung luong tai len tai URI [{}]: {}", getUri(request), ex.getMessage());
        return buildResponse(request, HttpStatus.BAD_REQUEST, msg, null);
    }

    /**
     * Xử lý ngoại lệ từ chối quyền truy cập của Spring Security (HTTP 403).
     */
    @ExceptionHandler(AccessDeniedException.class)
    public Object handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        String msg = "Bạn không có quyền truy cập vào chức năng hoặc tài nguyên này.";
        log.warn("Tu choi truy cap tai URI [{}]: {}", getUri(request), ex.getMessage());
        return buildResponse(request, HttpStatus.FORBIDDEN, msg, null);
    }

    /**
     * Xử lý sự cố hạ tầng lưu trữ tệp tin (HTTP 500).
     */
    @ExceptionHandler(FileStorageException.class)
    public Object handleFileStorage(FileStorageException ex, HttpServletRequest request) {
        log.error("Loi kho luu tru tep tin tai URI [{}]: {}", getUri(request), ex.getMessage(), ex);
        return buildResponse(request, HttpStatus.INTERNAL_SERVER_ERROR,
                "Không thể xử lý tệp đính kèm do sự cố lưu trữ. Vui lòng thử lại sau.", null);
    }

    /**
     * Lưới an toàn cuối cùng cho mọi ngoại lệ chưa được bắt (HTTP 500).
     * Tuyệt đối không phơi bày stack trace hay câu lệnh SQL nhạy cảm ra ngoài client.
     */
    @ExceptionHandler(Exception.class)
    public Object handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Loi he thong khong mong doi tai URI [{}]: {}", getUri(request), ex.getMessage(), ex);
        return buildResponse(request, HttpStatus.INTERNAL_SERVER_ERROR,
                "Hệ thống đang gặp sự cố kỹ thuật. Vui lòng thử lại sau ít phút.", null);
    }

    /**
     * Nhận diện yêu cầu từ client có phải là yêu cầu API / AJAX hay không.
     */
    private boolean isJsonRequest(HttpServletRequest request) {
        if (request == null) {
            return false;
        }
        String accept = request.getHeader("Accept");
        if (accept != null && accept.contains("application/json")) {
            return true;
        }
        String contentType = request.getContentType();
        if (contentType != null && contentType.contains("application/json")) {
            return true;
        }
        String xRequestedWith = request.getHeader("X-Requested-With");
        return "XMLHttpRequest".equalsIgnoreCase(xRequestedWith);
    }

    /**
     * Dựng phản hồi đa kênh: JSON cho API và ModelAndView cho trình duyệt.
     */
    private Object buildResponse(HttpServletRequest request, HttpStatus status, String message, List<String> fieldErrors) {
        if (isJsonRequest(request)) {
            ErrorResponse errorResponse = ErrorResponse.builder()
                    .status(status.value())
                    .error(status.getReasonPhrase())
                    .message(message)
                    .path(getUri(request))
                    .timestamp(Instant.now())
                    .fieldErrors(fieldErrors != null && !fieldErrors.isEmpty() ? fieldErrors : null)
                    .build();
            return ResponseEntity.status(status).body(errorResponse);
        }

        ModelAndView modelAndView = new ModelAndView("error/error");
        modelAndView.setStatus(status);
        modelAndView.addObject("statusCode", status.value());
        modelAndView.addObject("errorMessage", message);
        if (fieldErrors != null && !fieldErrors.isEmpty()) {
            modelAndView.addObject("fieldErrors", fieldErrors);
        }
        return modelAndView;
    }

    private String getUri(HttpServletRequest request) {
        return request != null ? request.getRequestURI() : "N/A";
    }

    private String describeFieldError(FieldError fieldError) {
        return fieldError.getField() + ": " + fieldError.getDefaultMessage();
    }
}
