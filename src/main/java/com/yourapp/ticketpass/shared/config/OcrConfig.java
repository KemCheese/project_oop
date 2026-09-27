package com.yourapp.ticketpass.shared.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * [DEPRECATED] Cấu hình cho khâu nhận dạng chữ trên ảnh vé (OCR).
 * Module nay da bi loai bo trong kien truc moi cua san marketplace.
 *
 * <p>Truoc day duoc dung de cau hinh Tesseract OCR.</p>
 *
 * <p>Lớp này được đăng ký qua {@code @ConfigurationPropertiesScan} khai báo ở
 * {@code TicketPassApplication}.</p>
 *
 * <p>Module sở hữu: shared.</p>
 * <p>Chủ sở hữu theo phân công: Người C.</p>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.ocr")
public class OcrConfig {

    /** Tên engine OCR sử dụng, ví dụ {@code TESSERACT}. */
    private String engine;

    /** Đường dẫn tới thư mục chứa dữ liệu huấn luyện của Tesseract. */
    private String tessdataPath;

    /** Mã ngôn ngữ nhận dạng, ví dụ {@code vie+eng} cho cả tiếng Việt và tiếng Anh. */
    private String language;

    /** Ngưỡng độ tin cậy tối thiểu (phần trăm) để chấp nhận kết quả đọc được. */
    private int minConfidence;

    /** Bật/tắt toàn bộ khâu OCR. Tắt khi máy chưa cài Tesseract. */
    private boolean enabled;
}
