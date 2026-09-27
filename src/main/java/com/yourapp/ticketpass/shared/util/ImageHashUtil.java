package com.yourapp.ticketpass.shared.util;

import org.springframework.stereotype.Component;

/**
 * Tiện ích băm ảnh, phục vụ việc phát hiện ảnh vé bị dùng lại hoặc bị chỉnh sửa.
 *
 * <p>Hai kiểu băm dùng cho hai mục đích khác nhau:</p>
 * <ul>
 *   <li>{@code sha256Hex} là băm mật mã: chỉ khớp khi hai tệp giống nhau đến từng byte.
 *       Dùng để bắt trường hợp tải lên đúng nguyên tệp ảnh cũ.</li>
 *   <li>{@code perceptualHash} là băm tri giác: mã hoá "hình dáng" của ảnh chứ không phải
 *       byte. Hai ảnh nhìn giống nhau sẽ cho mã băm gần nhau kể cả khi bị nén lại, đổi
 *       kích thước, chỉnh sáng hay dán đè một mẩu chữ. Đây là công cụ để phát hiện ảnh vé
 *       bị chỉnh sửa nhẹ nhằm bán lại vé cũ - thứ mà SHA-256 hoàn toàn bỏ lọt.</li>
 * </ul>
 *
 * <p>Độ giống nhau giữa hai mã băm tri giác được đo bằng {@code hammingDistance}: khoảng
 * cách càng nhỏ thì hai ảnh càng giống. Cong cu nay duoc giu lai cho viec doi chieu sau nay.</p>
 *
 * <p>Module sở hữu: shared.</p>
 * <p>Chủ sở hữu theo phân công: Người C.</p>
 */
@Component
public class ImageHashUtil {

    /**
     * Tính mã băm SHA-256 của dữ liệu ảnh.
     *
     * @param imageBytes dữ liệu nhị phân của ảnh
     * @return chuỗi băm SHA-256 dạng hex chữ thường
     */
    public String sha256Hex(byte[] imageBytes) {
        throw new UnsupportedOperationException("TODO [Người C] Prompt 2: tinh SHA-256 cua mang byte anh bang MessageDigest roi chuyen sang chuoi hex chu thuong dai 64 ky tu; nem IllegalArgumentException khi imageBytes null hoac rong");
    }

    /**
     * Tính mã băm tri giác (perceptual hash) của ảnh để so sánh mức độ giống nhau về thị giác.
     *
     * @param imageBytes dữ liệu nhị phân của ảnh
     * @return chuỗi băm tri giác dạng hex
     */
    public String perceptualHash(byte[] imageBytes) {
        throw new UnsupportedOperationException("TODO [Người C] Prompt 2: cai dat average hash - doc byte thanh BufferedImage, thu nho ve 8x8, chuyen sang thang do xam, tinh do sang trung binh, moi diem anh sang hon trung binh la bit 1 nguoc lai la 0, ghep 64 bit thanh chuoi hex 16 ky tu; nem IllegalArgumentException khi khong doc duoc anh");
    }

    /**
     * Đếm số bit khác nhau giữa hai mã băm tri giác.
     *
     * @param hashA mã băm thứ nhất
     * @param hashB mã băm thứ hai
     * @return số bit khác nhau; càng nhỏ thì hai ảnh càng giống
     */
    public int hammingDistance(String hashA, String hashB) {
        throw new UnsupportedOperationException("TODO [Người C] Prompt 2: kiem tra hai chuoi hex khong null va cung do dai, chuyen tung ky tu hex sang 4 bit, dem so bit khac nhau giua hai chuoi (XOR roi dem bit 1) va tra ve tong; nem IllegalArgumentException khi hai chuoi lech do dai hoac chua ky tu khong phai hex");
    }
}
