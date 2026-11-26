# TicketPass - Sàn Giao Dịch Vé Điện Tử (Ticket Marketplace)

TicketPass là một web đóng vai trò **sàn trung gian giao dịch chuyển nhượng (pass lại) vé điện tử** theo mô hình marketplace dành cho ba loại vé chính: **vé xem phim**, **vé concert / âm nhạc**, và **vé xe khách**.

Dự án giải quyết bài toán nhức nhối của việc pass vé tự phát trên các hội nhóm Facebook và Zalo: người mua phải chuyển tiền trước khi có bất kỳ bằng chứng nào về chất lượng vé, người bán sau khi nhận tiền có thể biến mất hoặc chuyển vé đã sử dụng, và không có bất kỳ cơ chế nào đứng giữa để bảo vệ quyền lợi hai bên. 

---


## 1. Cài đặt môi trường trên Windows

### 1.1 Cài đặt JDK 17
1. Tải bộ cài đặt: [Eclipse Temurin 17 (LTS)](https://adoptium.net/temurin/releases/?version=17) — chọn gói `.msi` (Windows x64).
2. Chạy file cài đặt với các thiết lập mặc định.
3. Đặt biến môi trường hệ thống `JAVA_HOME`:
   - Mở cửa sổ **System Properties** → **Environment Variables**.
   - Tại mục **System variables**, nhấn **New**:
     - `Variable name`: `JAVA_HOME`
     - `Variable value`: `C:\Program Files\Eclipse Adoptium\jdk-17.x.x-hotspot` (trỏ tới thư mục gốc của JDK, **không** trỏ vào `bin`).
   - Chọn biến `Path` → **Edit** → thêm dòng `%JAVA_HOME%\bin`.
4. Mở cửa sổ **PowerShell mới** và kiểm tra:
   ```powershell
   java -version
   $env:JAVA_HOME
   ```

### 1.2 Cài đặt MySQL 8
1. Cài đặt MySQL Server 8.x thông qua [MySQL Installer](https://dev.mysql.com/downloads/installer/). Ghi nhớ mật khẩu tài khoản `root`.
2. Đảm bảo dịch vụ MySQL đang chạy (kiểm tra trong `services.msc`).


### 1.3 Tạo `application-local.properties`
Cấu hình để setup từng máy:
1. Tạo file `src/main/resources/application-local.properties` :
   ```properties
   # Cấu hình tài khoản MySQL
   spring.datasource.username=root
   spring.datasource.password=mat_khau_mysql_may_ban
   ```
2. Ứng dụng sẽ dùng các thiết lập này khi chạy với profile `local`.

---

## 2. Khởi chạy ứng dụng

Mở **PowerShell** tại thư mục gốc 

### 2.1 Chạy web
```powershell
# Biên dịch và tải dependencies
.\mvnw clean install

# Khởi chạy Spring Boot với profile local
.\mvnw spring-boot:run "-Dspring-boot.run.profiles=local"
```
Truy cập ứng dụng tại trình duyệt: **http://localhost:8080**
