package com.yourapp.ticketpass.shared.exception;

import com.yourapp.ticketpass.shared.storage.FileStorageException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Kiểm thử GlobalExceptionHandler (Xử lý ngoại lệ tập trung Hybrid)")
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler exceptionHandler;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        exceptionHandler = new GlobalExceptionHandler();
        request = new MockHttpServletRequest();
        request.setRequestURI("/test-endpoint");
    }

    @Test
    @DisplayName("Browser request khi gặp ResourceNotFoundException: trả về view error/error với status 404")
    void handleResourceNotFound_browserRequest() {
        request.addHeader("Accept", "text/html,application/xhtml+xml");
        ResourceNotFoundException ex = new ResourceNotFoundException("Vé", "id=123");

        Object response = exceptionHandler.handleResourceNotFound(ex, request);

        assertThat(response).isInstanceOf(ModelAndView.class);
        ModelAndView mav = (ModelAndView) response;
        assertThat(mav.getViewName()).isEqualTo("error/error");
        assertThat(mav.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(mav.getModel().get("statusCode")).isEqualTo(404);
        assertThat(mav.getModel().get("errorMessage")).isEqualTo(ex.getMessage());
    }

    @Test
    @DisplayName("JSON API request khi gặp ResourceNotFoundException: trả về JSON ErrorResponse status 404")
    void handleResourceNotFound_jsonRequest() {
        request.addHeader("Accept", "application/json");
        ResourceNotFoundException ex = new ResourceNotFoundException("Vé", "id=123");

        Object response = exceptionHandler.handleResourceNotFound(ex, request);

        assertThat(response).isInstanceOf(ResponseEntity.class);
        @SuppressWarnings("unchecked")
        ResponseEntity<ErrorResponse> res = (ResponseEntity<ErrorResponse>) response;
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(res.getBody()).isNotNull();
        assertThat(res.getBody().getStatus()).isEqualTo(404);
        assertThat(res.getBody().getError()).isEqualTo("Not Found");
        assertThat(res.getBody().getMessage()).isEqualTo(ex.getMessage());
        assertThat(res.getBody().getPath()).isEqualTo("/test-endpoint");
        assertThat(res.getBody().getTimestamp()).isNotNull();
    }

    @Test
    @DisplayName("Browser request khi vi phạm BusinessRuleViolationException: trả về status 400")
    void handleBusinessRuleViolation_browserRequest() {
        request.addHeader("Accept", "text/html");
        BusinessRuleViolationException ex = new BusinessRuleViolationException("Email đã được đăng ký");

        Object response = exceptionHandler.handleBusinessRuleViolation(ex, request);

        assertThat(response).isInstanceOf(ModelAndView.class);
        ModelAndView mav = (ModelAndView) response;
        assertThat(mav.getViewName()).isEqualTo("error/error");
        assertThat(mav.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(mav.getModel().get("statusCode")).isEqualTo(400);
        assertThat(mav.getModel().get("errorMessage")).isEqualTo("Email đã được đăng ký");
    }

    @Test
    @DisplayName("JSON request khi gặp BusinessRuleViolationException: trả về ErrorResponse 400")
    void handleBusinessRuleViolation_jsonRequest() {
        request.addHeader("Accept", "application/json");
        BusinessRuleViolationException ex = new BusinessRuleViolationException("Số dư cọc không đủ");

        Object response = exceptionHandler.handleBusinessRuleViolation(ex, request);

        assertThat(response).isInstanceOf(ResponseEntity.class);
        @SuppressWarnings("unchecked")
        ResponseEntity<ErrorResponse> res = (ResponseEntity<ErrorResponse>) response;
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(res.getBody()).isNotNull();
        assertThat(res.getBody().getStatus()).isEqualTo(400);
        assertThat(res.getBody().getMessage()).isEqualTo("Số dư cọc không đủ");
    }

    @Test
    @DisplayName("JSON request gặp MethodArgumentNotValidException: gom đủ fieldErrors")
    void handleMethodArgumentNotValid_jsonRequest() {
        request.addHeader("Accept", "application/json");
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "target");
        bindingResult.addError(new FieldError("target", "email", "Email không đúng định dạng"));
        bindingResult.addError(new FieldError("target", "fullName", "Họ tên không được để trống"));

        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(null, bindingResult);

        Object response = exceptionHandler.handleMethodArgumentNotValid(ex, request);

        assertThat(response).isInstanceOf(ResponseEntity.class);
        @SuppressWarnings("unchecked")
        ResponseEntity<ErrorResponse> res = (ResponseEntity<ErrorResponse>) response;
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(res.getBody()).isNotNull();
        assertThat(res.getBody().getFieldErrors()).hasSize(2);
        assertThat(res.getBody().getFieldErrors()).contains(
                "email: Email không đúng định dạng",
                "fullName: Họ tên không được để trống"
        );
    }

    @Test
    @DisplayName("Browser request gặp BindException: trả về 400 kèm fieldErrors trong Model")
    void handleBindException_browserRequest() {
        request.addHeader("Accept", "text/html");
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "target");
        bindingResult.addError(new FieldError("target", "limit", "Phải là số nguyên"));

        BindException ex = new BindException(bindingResult);

        Object response = exceptionHandler.handleBindException(ex, request);

        assertThat(response).isInstanceOf(ModelAndView.class);
        ModelAndView mav = (ModelAndView) response;
        assertThat(mav.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(mav.getModel().get("fieldErrors")).isNotNull();
    }

    @Test
    @DisplayName("Xử lý MaxUploadSizeExceededException: trả về thông điệp giới hạn 10MB")
    void handleMaxUploadSizeExceeded() {
        request.addHeader("Accept", "application/json");
        MaxUploadSizeExceededException ex = new MaxUploadSizeExceededException(10 * 1024 * 1024);

        Object response = exceptionHandler.handleMaxUploadSizeExceeded(ex, request);

        assertThat(response).isInstanceOf(ResponseEntity.class);
        @SuppressWarnings("unchecked")
        ResponseEntity<ErrorResponse> res = (ResponseEntity<ErrorResponse>) response;
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(res.getBody()).isNotNull();
        assertThat(res.getBody().getMessage()).contains("10MB");
    }

    @Test
    @DisplayName("Xử lý AccessDeniedException: trả về HTTP 403 Forbidden")
    void handleAccessDenied() {
        request.addHeader("Accept", "application/json");
        AccessDeniedException ex = new AccessDeniedException("Forbidden");

        Object response = exceptionHandler.handleAccessDenied(ex, request);

        assertThat(response).isInstanceOf(ResponseEntity.class);
        @SuppressWarnings("unchecked")
        ResponseEntity<ErrorResponse> res = (ResponseEntity<ErrorResponse>) response;
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(res.getBody()).isNotNull();
        assertThat(res.getBody().getStatus()).isEqualTo(403);
        assertThat(res.getBody().getMessage()).contains("không có quyền truy cập");
    }

    @Test
    @DisplayName("Xử lý FileStorageException: trả về HTTP 500 lỗi hạ tầng lưu trữ")
    void handleFileStorage() {
        request.addHeader("Accept", "application/json");
        FileStorageException ex = new FileStorageException("Disk full");

        Object response = exceptionHandler.handleFileStorage(ex, request);

        assertThat(response).isInstanceOf(ResponseEntity.class);
        @SuppressWarnings("unchecked")
        ResponseEntity<ErrorResponse> res = (ResponseEntity<ErrorResponse>) response;
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(res.getBody()).isNotNull();
        assertThat(res.getBody().getMessage()).contains("sự cố lưu trữ");
    }

    @Test
    @DisplayName("Xử lý NoResourceFoundException (route không tồn tại): trả về HTTP 404 thay vì 500")
    void handleNoResourceFound_returns404() {
        request.addHeader("Accept", "application/json");
        NoResourceFoundException ex = new NoResourceFoundException(HttpMethod.POST, "/admin/users/1/suspend");

        Object response = exceptionHandler.handleNoResourceFound(ex, request);

        assertThat(response).isInstanceOf(ResponseEntity.class);
        @SuppressWarnings("unchecked")
        ResponseEntity<ErrorResponse> res = (ResponseEntity<ErrorResponse>) response;
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(res.getBody()).isNotNull();
        assertThat(res.getBody().getStatus()).isEqualTo(404);
    }

    @Test
    @DisplayName("Xử lý HttpRequestMethodNotSupportedException: trả về HTTP 405 Method Not Allowed")
    void handleMethodNotSupported_returns405() {
        request.addHeader("Accept", "application/json");
        HttpRequestMethodNotSupportedException ex = new HttpRequestMethodNotSupportedException("DELETE");

        Object response = exceptionHandler.handleMethodNotSupported(ex, request);

        assertThat(response).isInstanceOf(ResponseEntity.class);
        @SuppressWarnings("unchecked")
        ResponseEntity<ErrorResponse> res = (ResponseEntity<ErrorResponse>) response;
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(res.getBody()).isNotNull();
        assertThat(res.getBody().getStatus()).isEqualTo(405);
    }

    @Test
    @DisplayName("Xử lý Exception chưa lường trước: trả về HTTP 500 thông điệp chung thân thiện")
    void handleUnexpected() {
        request.addHeader("Accept", "text/html");
        RuntimeException ex = new RuntimeException("NullPointerException at line 999");

        Object response = exceptionHandler.handleUnexpected(ex, request);

        assertThat(response).isInstanceOf(ModelAndView.class);
        ModelAndView mav = (ModelAndView) response;
        assertThat(mav.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat((String) mav.getModel().get("errorMessage")).doesNotContain("NullPointerException");
        assertThat(mav.getModel().get("errorMessage")).isEqualTo("Hệ thống đang gặp sự cố kỹ thuật. Vui lòng thử lại sau ít phút.");
    }
}
