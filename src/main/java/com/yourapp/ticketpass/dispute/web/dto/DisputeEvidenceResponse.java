package com.yourapp.ticketpass.dispute.web.dto;

import java.time.Instant;

import com.yourapp.ticketpass.dispute.domain.DisputeEvidenceType;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Thông tin một mẩu bằng chứng để hiển thị trên giao diện.
 *
 * <p>DTO này phơi ra {@code publicUrl} thay vì {@code storageKey}: đường dẫn được dựng lúc chạy
 * bằng {@code FileStorageService.buildPublicUrl(storageKey)}. Không để lộ {@code storageKey}
 * ra giao diện, vì đó là chi tiết nội bộ của kho lưu trữ và có thể đổi khi chuyển sang
 * S3/MinIO.</p>
 *
 * <p>Module: dispute</p>
 *
 * <p>Chủ sở hữu: Người B</p>
 */
@Getter
@Setter
@NoArgsConstructor
public class DisputeEvidenceResponse {

    /** ID của bằng chứng. */
    private Long id;

    /** Loại bằng chứng, quyết định cách giao diện hiển thị. */
    private DisputeEvidenceType evidenceType;

    /** Tên tệp gốc để hiển thị và tải xuống. */
    private String originalFilename;

    /** Đường dẫn công khai để xem tệp, dựng từ {@code storageKey}. */
    private String publicUrl;

    /** Ghi chú của người tải lên. */
    private String note;

    /** Thời điểm tệp được tải lên. */
    private Instant uploadedAt;
}
