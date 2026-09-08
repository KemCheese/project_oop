package com.yourapp.ticketpass.shared.util;

import org.springframework.stereotype.Component;

/**
 * Tiện ích sinh, đọc và băm mã QR của vé.
 *
 * <p>Cài đặt dựa trên thư viện zxing ({@code com.google.zxing:core} để mã hoá/giải mã và
 * {@code com.google.zxing:javase} để chuyển đổi qua lại với ảnh của Java).</p>
 *
 * <p><b>Vai trò của {@code hashPayload}:</b> phương thức này trả về chuỗi SHA-256 dạng hex
 * của nội dung mã QR. Giá trị băm đó được lưu kèm vé và đặt ràng buộc duy nhất trong cơ
 * sở dữ liệu, nên nó chính là khoá chống bán trùng vé: nếu ai đó đăng bán lại đúng một
 * chiếc vé đã có trên sàn, mã băm sẽ trùng và hệ thống chặn ngay. Lưu mã băm thay vì lưu
 * nguyên nội dung QR cũng tránh lộ dữ liệu vé trong database.</p>
 *
 * <p>Module sở hữu: shared.</p>
 * <p>Chủ sở hữu theo phân công: Người C.</p>
 */
@Component
public class QrCodeGenerator {

    /**
     * Sinh ảnh PNG của mã QR từ chuỗi nội dung.
     *
     * @param payload nội dung cần mã hoá vào QR
     * @param width   chiều rộng ảnh, tính bằng điểm ảnh
     * @param height  chiều cao ảnh, tính bằng điểm ảnh
     * @return mảng byte của ảnh PNG
     */
    public byte[] generatePng(String payload, int width, int height) {
        throw new UnsupportedOperationException("TODO [Người C] Prompt 2: dung QRCodeWriter cua zxing ma hoa payload thanh BitMatrix kich thuoc width x height voi charset UTF-8 va muc sua loi phu hop, roi ghi ra mang byte PNG bang MatrixToImageWriter, nem FileStorageException hoac IllegalArgumentException khi payload rong hoac kich thuoc khong hop le");
    }

    /**
     * Đọc nội dung mã QR có trong một ảnh.
     *
     * @param imageBytes dữ liệu nhị phân của ảnh chứa mã QR
     * @return chuỗi nội dung đọc được từ mã QR
     */
    public String decodeFromImage(byte[] imageBytes) {
        throw new UnsupportedOperationException("TODO [Người C] Prompt 2: doc mang byte thanh BufferedImage, bao boi BufferedImageLuminanceSource va HybridBinarizer, dung MultiFormatReader de giai ma va tra ve noi dung; tra ve null hoac nem ngoai le co nghia khi anh khong chua ma QR doc duoc");
    }

    /**
     * Băm nội dung mã QR thành chuỗi SHA-256 dạng hex để làm khoá chống bán trùng vé.
     *
     * @param payload nội dung mã QR
     * @return chuỗi băm SHA-256 viết dưới dạng hex chữ thường
     */
    public String hashPayload(String payload) {
        throw new UnsupportedOperationException("TODO [Người C] Prompt 2: chuan hoa payload (trim, ma hoa UTF-8), tinh SHA-256 bang MessageDigest roi chuyen mang byte ket qua sang chuoi hex chu thuong dai 64 ky tu; nem IllegalArgumentException khi payload null hoac rong");
    }
}
