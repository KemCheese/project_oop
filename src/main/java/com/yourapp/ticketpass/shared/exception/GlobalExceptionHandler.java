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
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public Object handleResourceNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        log.warn("Khong tim thay tai nguyen tai URI [{}]: {}", getUri(request), ex.getMessage());
        return buildResponse(request, HttpStatus.NOT_FOUND, ex.getMessage(), null);
    }

    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public Object handleNoResourceFound(Exception ex, HttpServletRequest request) {
        log.warn("Khong tim thay duong dan tai URI [{}]: {}", getUri(request), ex.getMessage());
        return buildResponse(request, HttpStatus.NOT_FOUND,
                "Đường dẫn hoặc tài nguyên bạn yêu cầu không tồn tại.", null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public Object handleMethodNotSupported(HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        log.warn("Phuong thuc HTTP khong ho tro tai URI [{}]: {}", getUri(request), ex.getMessage());
        return buildResponse(request, HttpStatus.METHOD_NOT_ALLOWED,
                "Phương thức HTTP không được hỗ trợ cho đường dẫn này.", null);
    }

    @ExceptionHandler(BusinessRuleViolationException.class)
    public Object handleBusinessRuleViolation(BusinessRuleViolationException ex, HttpServletRequest request) {
        log.warn("Vi pham quy tac nghiep vu tai URI [{}]: {}", getUri(request), ex.getMessage());
        return buildResponse(request, HttpStatus.BAD_REQUEST, ex.getMessage(), null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Object handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<String> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(this::describeFieldError)
                .collect(Collectors.toList());
        String detail = String.join("; ", fieldErrors);
        log.warn("Du lieu JSON gui len khong hop le tai URI [{}]: {}", getUri(request), detail);
        return buildResponse(request, HttpStatus.BAD_REQUEST, "Dữ liệu gửi lên không hợp lệ: " + detail, fieldErrors);
    }

    @ExceptionHandler(BindException.class)
    public Object handleBindException(BindException ex, HttpServletRequest request) {
        List<String> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(this::describeFieldError)
                .collect(Collectors.toList());
        String detail = String.join("; ", fieldErrors);
        log.warn("Du lieu Form gui len khong hop le tai URI [{}]: {}", getUri(request), detail);
        return buildResponse(request, HttpStatus.BAD_REQUEST, "Tham số gửi lên không đúng định dạng: " + detail, fieldErrors);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Object handleMaxUploadSizeExceeded(MaxUploadSizeExceededException ex, HttpServletRequest request) {
        String msg = "Dung lượng tệp tin tải lên vượt quá giới hạn tối đa cho phép (tối đa 10MB).";
        log.warn("Vuot qua dung luong tai len tai URI [{}]: {}", getUri(request), ex.getMessage());
        return buildResponse(request, HttpStatus.BAD_REQUEST, msg, null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public Object handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        String msg = "Bạn không có quyền truy cập vào chức năng hoặc tài nguyên này.";
        log.warn("Tu choi truy cap tai URI [{}]: {}", getUri(request), ex.getMessage());
        return buildResponse(request, HttpStatus.FORBIDDEN, msg, null);
    }

    @ExceptionHandler(FileStorageException.class)
    public Object handleFileStorage(FileStorageException ex, HttpServletRequest request) {
        log.error("Loi kho luu tru tep tin tai URI [{}]: {}", getUri(request), ex.getMessage(), ex);
        return buildResponse(request, HttpStatus.INTERNAL_SERVER_ERROR,
                "Không thể xử lý tệp đính kèm do sự cố lưu trữ. Vui lòng thử lại sau.", null);
    }

    @ExceptionHandler(Exception.class)
    public Object handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Loi he thong khong mong doi tai URI [{}]: {}", getUri(request), ex.getMessage(), ex);
        return buildResponse(request, HttpStatus.INTERNAL_SERVER_ERROR,
                "Hệ thống đang gặp sự cố kỹ thuật. Vui lòng thử lại sau ít phút.", null);
    }

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
