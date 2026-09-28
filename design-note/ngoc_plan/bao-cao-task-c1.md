# Báo cáo hoàn thành Task C1 — Hiện thực lưu trữ file cục bộ (LocalFileStorageAdapter)

**Người thực hiện:** Dev C  
**Ngày hoàn thành:** 2026-09-28  
**Dự án:** TicketPass — Sàn Giao Dịch Vé Điện Tử

---

## 1. Vừa làm gì?

Hiện thực đầy đủ class `LocalFileStorageAdapter` — bản cài đặt duy nhất của interface `FileStorageService` (Adapter Pattern). Class này cung cấp khả năng lưu trữ, truy xuất, xóa, kiểm tra file và sinh URL công khai cho toàn bộ hệ thống TicketPass, phục vụ:
- Dev A: lưu ảnh vé công khai và ảnh QR ẩn
- Dev B: lưu ảnh/video bằng chứng khiếu nại
- Dev C: lưu/xóa avatar người dùng

Trước đó, file chỉ có scaffold với 5 method ném `UnsupportedOperationException`. Sau khi hoàn thành, tất cả 5 method đều hoạt động đầy đủ kèm bảo mật chống Path Traversal.

---

## 2. Làm như thế nào?

### Kiến trúc và Design Pattern

- **Adapter Pattern (Ports & Adapters):** `LocalFileStorageAdapter` implements interface `FileStorageService` (Port). Code nghiệp vụ chỉ phụ thuộc vào interface, không biết file lưu ở đĩa cục bộ hay S3. Khi cần đổi nơi lưu trữ, chỉ viết adapter mới, không sửa business logic.
- **Encapsulation:** Logic bảo mật (chống Path Traversal, chuẩn hóa tên file) được đóng gói trong 3 hàm private nội bộ: `resolveAndValidate()`, `sanitizeFilename()`, `assertNoTraversal()`. Các method public không cần biết chi tiết kiểm tra.

### Cơ chế bảo mật chống Path Traversal (2 lớp)

1. **Lớp 1 — Kiểm tra chuỗi:** Hàm `assertNoTraversal()` từ chối folder/filename chứa `..`, `/`, `\`.
2. **Lớp 2 — Kiểm tra đường dẫn:** Sau khi `resolve().normalize()`, kiểm tra `path.startsWith(basePath)` để đảm bảo đường dẫn kết quả vẫn nằm trong thư mục gốc.

### Chuẩn hóa tên file tiếng Việt

Sử dụng `java.text.Normalizer.NFD` decompose chuỗi Unicode thành ký tự gốc + dấu kết hợp, sau đó loại bỏ dấu kết hợp bằng regex `\p{InCombiningDiacriticalMarks}+`. Xử lý riêng chữ đ/Đ (Normalizer không tách được). Loại bỏ ký tự đặc biệt, chỉ giữ `[a-zA-Z0-9._-]`.

### Chống trùng tên file

Ghép UUID prefix 8 ký tự vào tên file đã chuẩn hóa, ví dụ: `a1b2c3d4-Anh_ve.jpg`.

---

## 3. Đạt được kết quả gì?

### File đã tạo/sửa

| # | File | Hành động | Mô tả |
|---|---|---|---|
| 1 | `src/main/java/.../shared/storage/adapter/LocalFileStorageAdapter.java` | **SỬA** | Hiện thực 5 method + `@PostConstruct` + 3 hàm nội bộ (334 dòng) |
| 2 | `src/test/java/.../shared/storage/adapter/LocalFileStorageAdapterTest.java` | **TẠO MỚI** | 25 test cases (310 dòng) |

### Kết quả test

```
Tests run: 25, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Chi tiết nhóm test:
- StoreTests: 8 tests (lưu thành công, tạo thư mục tự động, chống traversal folder/filename/backslash, validate folder rỗng/contentLength/contentType)
- FilenameNormalizationTests: 3 tests (tiếng Việt, ký tự đặc biệt, fallback)
- LoadTests: 3 tests (round-trip, file không tồn tại, traversal)
- DeleteTests: 3 tests (xóa thành công, file không tồn tại, traversal)
- BuildPublicUrlTests: 4 tests (format cơ bản, null, blank, encode ký tự đặc biệt)
- ExistsTests: 4 tests (tồn tại, không tồn tại, traversal, lifecycle đầy đủ)

### Không ảnh hưởng các Dev khác

- Không sửa interface `FileStorageService` (giữ nguyên chữ ký)
- Không sửa `StoredFileRef`, `FileStorageException`
- Không sửa `application.properties`
- Không chạm file của Dev A hoặc Dev B

---

## 4. Giải thích thuật ngữ

| Thuật ngữ | Giải thích |
|---|---|
| **Adapter Pattern** | Pattern cấu trúc cho phép class có interface không tương thích làm việc cùng nhau. Ở đây, `LocalFileStorageAdapter` "dịch" các thao tác Java NIO (`Files.copy`, `Files.delete`...) thành giao diện `FileStorageService` mà code nghiệp vụ hiểu. |
| **Path Traversal** | Lỗ hổng bảo mật cho phép kẻ tấn công dùng `..` trong tên file/folder để thoát khỏi thư mục cho phép và truy cập file hệ thống (ví dụ `../../etc/passwd`). |
| **Normalizer.NFD** | Phương pháp Unicode Normalization Form D — phân tách ký tự có dấu thành ký tự gốc + combining diacritical mark. Ví dụ: `ă` → `a` + `̆`. Sau đó loại bỏ dấu kết hợp để ra ký tự không dấu. |
| **@PostConstruct** | Annotation của Jakarta EE đánh dấu method được gọi tự động ngay sau khi Spring tạo bean và inject xong dependencies. Dùng để khởi tạo `basePath` và tạo thư mục gốc. |
| **@TempDir** | Annotation của JUnit 5 tự động tạo thư mục tạm riêng cho mỗi test và dọn dẹp sau khi test xong. Đảm bảo test không ghi lên ổ đĩa thật. |

---

## 5. Rủi ro phát hiện & Đề xuất

| Rủi ro | Mức độ | Đề xuất xử lý |
|---|---|---|
| `LocalFileStorageAdapter` ghi ổ đĩa cục bộ — nhiều instance không dùng chung file | Thấp (chỉ chạy 1 instance cho đồ án) | Đã có sẵn đường mở rộng: viết `S3FileStorageAdapter` implements cùng interface, không sửa code nghiệp vụ. Ghi vào mục nợ kỹ thuật. |
| Không giới hạn kích thước file tối đa ở tầng adapter | Trung bình | Nên kiểm tra `contentLength` với ngưỡng cấu hình (ví dụ 50MB) trong adapter hoặc ở tầng `application` khi gọi `store()`. Hiện tại Dev A và Dev C tự validate ở use case/service riêng. |
| Log tiếng Việt bị mojibake trên console Windows | Thấp | Do mã hóa console mặc định, không ảnh hưởng logic. Có thể thêm `-Dfile.encoding=UTF-8` khi chạy. |
| Concurrent write cùng folder | Thấp | `Files.createDirectories()` là thread-safe. `UUID` prefix chống trùng tên. Không cần lo cho quy mô đồ án. |
