package com.yourapp.ticketpass.shared.exception;

import com.yourapp.ticketpass.shared.storage.FileStorageException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;

import java.util.stream.Collectors;

/**
 * Nơi bắt tập trung mọi ngoại lệ chưa được xử lý của tầng web và biến chúng thành trang
 * lỗi thân thiện cho người dùng.
 *
 * <p>Nhờ lớp này, các controller và service không phải rải {@code try/catch} khắp nơi -
 * chúng chỉ việc ném ngoại lệ có ngữ nghĩa rõ ràng
 * ({@link ResourceNotFoundException}, {@link BusinessRuleViolationException},
 * {@link FileStorageException}) rồi để tầng này quyết định mã HTTP và giao diện hiển thị.</p>
 *
 * <p>Đây là mã "keo dán" hạ tầng, không phải logic nghiệp vụ, nên được viết đầy đủ ngay từ
 * bước dựng khung. Quy ước ghi log: lỗi do người dùng gây ra ghi mức {@code warn}, sự cố
 * hệ thống ghi mức {@code error} kèm stack trace để truy vết.</p>
 *
 * <p>Tên view trả về: {@code error/404}, {@code error/400} cho nhóm lỗi phía người dùng
 * và {@code error/error} cho sự cố hệ thống.</p>
 *
 * <p>Module sở hữu: shared.</p>
 * <p>Chủ sở hữu theo phân công: Người C.</p>
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Không tìm thấy bản ghi được yêu cầu. */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ModelAndView handleResourceNotFound(ResourceNotFoundException ex) {
        log.warn("Khong tim thay tai nguyen: {}", ex.getMessage());
        return buildView("error/404", HttpStatus.NOT_FOUND, ex.getMessage());
    }

    /** Hành động vi phạm quy tắc nghiệp vụ. */
    @ExceptionHandler(BusinessRuleViolationException.class)
    public ModelAndView handleBusinessRuleViolation(BusinessRuleViolationException ex) {
        log.warn("Vi pham quy tac nghiep vu: {}", ex.getMessage());
        return buildView("error/400", HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    /** Thao tác với kho tệp thất bại - đây là sự cố hạ tầng, không phải lỗi người dùng. */
    @ExceptionHandler(FileStorageException.class)
    public ModelAndView handleFileStorage(FileStorageException ex) {
        log.error("Loi kho luu tru tep", ex);
        return buildView("error/error", HttpStatus.INTERNAL_SERVER_ERROR,
                "Khong xu ly duoc tep dinh kem. Vui long thu lai.");
    }

    /** Dữ liệu form/DTO không qua được Bean Validation. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ModelAndView handleValidation(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(this::describeFieldError)
                .collect(Collectors.joining("; "));
        log.warn("Du lieu gui len khong hop le: {}", detail);
        return buildView("error/400", HttpStatus.BAD_REQUEST, detail);
    }

    /** Lưới an toàn cuối cùng cho mọi ngoại lệ chưa lường trước. */
    @ExceptionHandler(Exception.class)
    public ModelAndView handleUnexpected(Exception ex) {
        log.error("Loi khong mong doi", ex);
        return buildView("error/error", HttpStatus.INTERNAL_SERVER_ERROR,
                "He thong gap su co. Vui long thu lai sau.");
    }

    private String describeFieldError(FieldError fieldError) {
        return fieldError.getField() + ": " + fieldError.getDefaultMessage();
    }

    private ModelAndView buildView(String viewName, HttpStatus status, String message) {
        ModelAndView modelAndView = new ModelAndView(viewName);
        modelAndView.setStatus(status);
        modelAndView.addObject("statusCode", status.value());
        modelAndView.addObject("errorMessage", message);
        return modelAndView;
    }
}
