package com.yourapp.ticketpass.shared.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cấu hình cho khâu nhận dạng chữ trên ảnh vé (OCR), đọc từ tiền tố {@code app.ocr}
 * trong {@code application.properties}.
 *
 * <p>Được module verification dùng khi đối chiếu thông tin người bán khai báo với chữ
 * đọc được từ ảnh vé. Nhờ đưa ra file cấu hình, có thể tắt OCR hoặc đổi ngôn ngữ, đổi
 * ngưỡng tin cậy mà không phải biên dịch lại ứng dụng.</p>
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
