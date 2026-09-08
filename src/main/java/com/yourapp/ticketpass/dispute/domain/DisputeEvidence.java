package com.yourapp.ticketpass.dispute.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Một mẩu bằng chứng đính kèm khiếu nại: ảnh vé, video quét vé bị từ chối, tệp PDF,
 * lịch sử trò chuyện hoặc ghi chú văn bản.
 *
 * <p><b>Nguyên tắc kiến trúc số 1 — áp dụng cả trong cùng module:</b> {@code disputeId} là ID
 * thuần {@code Long}, KHÔNG dùng {@code @ManyToOne} tới {@link Dispute} dù hai entity nằm cùng
 * một module. Giữ nhất quán một cách tham chiếu duy nhất trên toàn hệ thống giúp không bao giờ
 * phải phân biệt "quan hệ nội bộ thì được dùng, quan hệ liên module thì không", và loại bỏ hẳn
 * rủi ro tải chậm (lazy loading) ngoài ý muốn.</p>
 *
 * <p><b>Nguyên tắc 3 — lưu trữ tệp:</b> entity này chỉ lưu {@code storageKey} do
 * {@code FileStorageService} cấp, KHÔNG lưu đường dẫn tuyệt đối trên máy chủ và không lưu nội
 * dung nhị phân của tệp trong cơ sở dữ liệu. Đường dẫn hiển thị được dựng lúc chạy bằng
 * {@code FileStorageService.buildPublicUrl(storageKey)}.</p>
 *
 * <p>Bản ghi bằng chứng là bất biến sau khi tạo (chỉ thêm hoặc xoá, không sửa nội dung), nên
 * entity không cần {@code updatedAt} và không cần {@code @Version}.</p>
 *
 * <p>Module: dispute</p>
 *
 * <p>Chủ sở hữu: Người B</p>
 */
@Entity
@Table(name = "dispute_evidences")
@Getter
@Setter
@NoArgsConstructor
public class DisputeEvidence {

    /** Khoá chính, sinh tự động bởi cơ sở dữ liệu. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** ID của khiếu nại mà bằng chứng này thuộc về (ID thuần, không phải quan hệ JPA). */
    @Column(name = "dispute_id", nullable = false)
    private Long disputeId;

    /** ID người đã tải bằng chứng lên: có thể là người mua, người bán, hoặc admin. */
    @Column(name = "uploaded_by_user_id")
    private Long uploadedByUserId;

    /** Loại bằng chứng, quyết định cách giao diện hiển thị. */
    @Enumerated(EnumType.STRING)
    @Column(name = "evidence_type", length = 40)
    private DisputeEvidenceType evidenceType;

    /** Khoá tệp do {@code FileStorageService} cấp; trung lập với công nghệ lưu trữ. */
    @Column(name = "storage_key", length = 500)
    private String storageKey;

    /** Tên tệp gốc người dùng tải lên, dùng để hiển thị và tải xuống. */
    @Column(name = "original_filename", length = 255)
    private String originalFilename;

    /** Kiểu MIME của tệp, ví dụ {@code "image/jpeg"}. */
    @Column(name = "content_type", length = 100)
    private String contentType;

    /** Kích thước tệp tính bằng byte. */
    @Column(name = "size_bytes")
    private Long sizeBytes;

    /** Ghi chú của người tải lên, giải thích bằng chứng này chứng minh điều gì. */
    @Column(name = "note", length = 500)
    private String note;

    /** Thời điểm tệp được tải lên. */
    @Column(name = "uploaded_at")
    private Instant uploadedAt;

    /** Thời điểm bản ghi được tạo, gán tự động trong {@link #onCreate()}. */
    @Column(name = "created_at")
    private Instant createdAt;

    /** Gán {@code createdAt} ngay trước khi bản ghi được chèn mới. */
    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }
}
