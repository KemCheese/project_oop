package com.yourapp.ticketpass.dispute.domain;

/**
 * Loại bằng chứng đính kèm một khiếu nại.
 *
 * <p>Loại bằng chứng quyết định cách giao diện hiển thị tệp cho admin: ảnh xem trực tiếp,
 * video nhúng trình phát, PDF mở trong khung xem, còn {@code TEXT_NOTE} không có tệp kèm theo
 * mà chỉ có nội dung trong trường ghi chú.</p>
 *
 * <p>Module: dispute</p>
 *
 * <p>Chủ sở hữu: Người B</p>
 */
public enum DisputeEvidenceType {

    /** Ảnh chụp: ảnh vé, ảnh màn hình ứng dụng, ảnh mã QR không quét được. */
    IMAGE,

    /** Video: quay lại quá trình quét vé bị từ chối ở cửa vào. */
    VIDEO,

    /** Tệp PDF: vé điện tử gốc, biên nhận, xác nhận của nhà phát hành. */
    PDF,

    /** Lịch sử trò chuyện giữa người mua và người bán, tải lên dưới dạng tệp. */
    CHAT_LOG,

    /** Ghi chú thuần văn bản, không có tệp đính kèm. */
    TEXT_NOTE
}
