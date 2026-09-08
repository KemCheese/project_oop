package com.yourapp.ticketpass.user.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Tài khoản người dùng của sàn pass vé.
 *
 * <p>Entity này thuộc quyền sở hữu của module {@code user}. Theo nguyên tắc kiến trúc số 1,
 * các module khác (ticket, payment, dispute, verification, reputation) chỉ được tham chiếu
 * người dùng bằng ID thuần kiểu {@code Long} (ví dụ {@code sellerId}, {@code buyerId},
 * {@code userId}) và <b>KHÔNG</b> được import class này vào field entity của họ. Muốn lấy
 * thông tin người dùng, các module khác gọi
 * {@code com.yourapp.ticketpass.user.application.UserQueryService}.</p>
 *
 * <p>Entity chỉ chứa field và getter/setter, không chứa business logic. Mật khẩu luôn được
 * lưu ở dạng băm BCrypt trong {@code passwordHash}, không bao giờ lưu plaintext.
 * Ảnh đại diện chỉ lưu {@code avatarStorageKey} - khoá tệp trong
 * {@code shared.storage.FileStorageService}, không lưu đường dẫn tuyệt đối của máy chủ.</p>
 *
 * <p>Module: user</p>
 *
 * Chủ sở hữu: Người C
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Email đăng nhập, đồng thời là định danh duy nhất của tài khoản. */
    @Column(nullable = false, unique = true, length = 255)
    private String email;

    /** Chuỗi băm BCrypt của mật khẩu; độ dài 100 đủ chỗ cho tiền tố thuật toán. */
    @Column(nullable = false, length = 100)
    private String passwordHash;

    @Column(length = 150)
    private String fullName;

    @Column(length = 20)
    private String phoneNumber;

    @Enumerated(EnumType.STRING)
    @Column(length = 40)
    private UserRole role;

    @Enumerated(EnumType.STRING)
    @Column(length = 40)
    private UserStatus status;

    /** Đã xác minh email qua liên kết kích hoạt hay chưa. */
    private boolean emailVerified;

    /** Đã xác minh số điện thoại qua OTP hay chưa. */
    private boolean phoneVerified;

    /** Đã được module {@code verification} duyệt giấy tờ tuỳ thân hay chưa. */
    private boolean identityVerified;

    /** Khoá tệp ảnh đại diện trong FileStorageService. */
    @Column(length = 500)
    private String avatarStorageKey;

    /** Tên ngân hàng nhận tiền khi bán vé thành công. */
    @Column(length = 100)
    private String bankName;

    @Column(length = 50)
    private String bankAccountNumber;

    @Column(length = 150)
    private String bankAccountHolder;

    private Instant lastLoginAt;

    private Instant createdAt;

    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
