package com.yourapp.ticketpass.user.web.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UpdateProfileRequestTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    @Test
    @DisplayName("Hợp lệ khi có fullName và phoneNumber 10 số bắt đầu bằng 0")
    void valid_whenFullNameAndValidPhone() {
        UpdateProfileRequest req = new UpdateProfileRequest();
        req.setFullName("Hoàng Thế Ngọc");
        req.setPhoneNumber("0828618777");

        Set<ConstraintViolation<UpdateProfileRequest>> violations = validator.validate(req);
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("Hợp lệ khi có fullName và phoneNumber rỗng (cho phép người dùng chỉ cập nhật tên)")
    void valid_whenFullNameAndEmptyPhone() {
        UpdateProfileRequest req = new UpdateProfileRequest();
        req.setFullName("Hoàng Thế Ngọc");
        req.setPhoneNumber("");

        Set<ConstraintViolation<UpdateProfileRequest>> violations = validator.validate(req);
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("Hợp lệ khi có fullName và phoneNumber null")
    void valid_whenFullNameAndNullPhone() {
        UpdateProfileRequest req = new UpdateProfileRequest();
        req.setFullName("Hoàng Thế Ngọc");
        req.setPhoneNumber(null);

        Set<ConstraintViolation<UpdateProfileRequest>> violations = validator.validate(req);
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("Báo lỗi khi fullName rỗng hoặc chỉ toàn khoảng trắng")
    void invalid_whenFullNameBlank() {
        UpdateProfileRequest req = new UpdateProfileRequest();
        req.setFullName("   ");
        req.setPhoneNumber("0828618777");

        Set<ConstraintViolation<UpdateProfileRequest>> violations = validator.validate(req);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("fullName");
    }

    @Test
    @DisplayName("Báo lỗi khi phoneNumber không đúng định dạng (không bắt đầu bằng 0 hoặc sai độ dài)")
    void invalid_whenPhoneNumberInvalidFormat() {
        UpdateProfileRequest req = new UpdateProfileRequest();
        req.setFullName("Hoàng Thế Ngọc");
        req.setPhoneNumber("12345");

        Set<ConstraintViolation<UpdateProfileRequest>> violations = validator.validate(req);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("phoneNumber");
    }
}
