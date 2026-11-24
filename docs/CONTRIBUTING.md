# Hướng dẫn Đóng góp Phát triển MTVkit

Chào mừng bạn tham gia phát triển dự án **MTVkit**! Tài liệu này quy định các chuẩn mực về mã nguồn, quy ước đặt tên, quy trình kiểm thử và cách thức gửi đóng góp (Pull Request).

---

## 1. Phong cách Lập trình & Quy ước Dự án

Dự án có những quy ước thiết kế đặc thù được kế thừa và chuẩn hoá:

### 1.1. Quy ước Đặt tên Tiếng Việt Thuần túy (Vietnamese Idiomatic Naming)
Khác với nhiều dự án quốc tế, hệ thống mã nguồn của MTVkit sử dụng **tiếng Việt không dấu chuẩn ngữ nghĩa** cho tên hàm, tên biến nội bộ và tên lớp tiện ích trong các module lõi:
- Ví dụ: `KetNoiAdb.kt`, `ThaoTacTv.kt`, `GiuKetNoi.kt`, `KhoaDichVu.kt`, `XacThuc.kt`, `MaHoaKho.kt`.
- Tên hàm: `chayShell()`, `caiApkHome()`, `docHoacHetGio()`, `datDnsAnToan()`.
- Tên biến: `daThayDau`, `coHeadC`, `soLanThuLai`.
> **Quy tắc:** Hãy duy trì sự nhất quán này khi bổ sung các tính năng mới trong các module hiện có để bảo đảm sự đồng bộ của codebase.

### 1.2. Tính Bất biến và Coroutines
- Ưu tiên sử dụng `data class`, `val` thay vì `var`.
- Đối với các tác vụ I/O mạng hoặc tệp tin (như ADB, WebSocket, giải mã Bitmap): **Tuyệt đối không chạy trên Main Thread**, luôn sử dụng `withContext(Dispatchers.IO)`.
- Tránh sử dụng `Thread.sleep()` trong coroutines, thay thế bằng `delay()`.

### 1.3. An toàn Bộ nhớ và Tài nguyên
- Mọi luồng nhập xuất (`InputStream`, `OutputStream`, `AdbStream`, `ZipFile`) bắt buộc phải được đóng bằng cấu trúc `.use { ... }` hoặc đặt lệnh đóng trong khối `finally`.
- Đối với `Bitmap` sau khi nén hoặc vẽ xong, hãy gọi `.recycle()` nếu không còn sử dụng để tránh lỗi Out-of-Memory trên các TV Box cấu hình yếu (RAM 1GB - 2GB).

---

## 2. Quy trình Kiểm thử (Unit Tests)

Mỗi thay đổi về logic dữ liệu hoặc bảo mật (`:app`, `:khoa`, `:launcher`, `:cast`, `:iptv`, `:mapvoice`, `:teleremote`) bắt buộc phải đi kèm hoặc chạy qua bộ kiểm thử Unit Test tương ứng trong `src/test/`:
- Kiểm thử lệnh ADB: `DongLenhTest.kt`
- Kiểm thử danh mục gói: `KeHoachTest.kt`
- Kiểm thử phân loại APK: `GoiApkTest.kt`
- Kiểm thử DNS riêng: `DnsRiengTest.kt`
- Kiểm thử mã OTP Khóa trẻ em: `MaOtpTest.kt`
- Kiểm thử 5 lớp bảo mật chiều sâu & mã hoá `AndroidKeyStore`: `BaoMatTest.kt`, `khoa/MaHoaKhoTest.kt`, `teleremote/MaHoaKhoTest.kt`
- Kiểm thử chống SSRF & luồng IPTV / Map Voice: `DanhSachTest.kt`, `TuChonNguonTest.kt`

Chạy kiểm thử trước khi commit:
```bash
./gradlew testReleaseUnitTest
```

---

## 3. Quy trình Đóng góp & Commit

1. **Fork repository** về tài khoản cá nhân: [https://github.com/maptoan/tivikit](https://github.com/maptoan/tivikit).
2. Tạo nhánh tính năng mới từ `main`:
   ```bash
   git checkout -b feature/ten-tinh-nang
   ```
3. Tuân thủ định dạng commit Conventional Commits:
   - `feat: ...` bổ sung tính năng mới.
   - `fix: ...` vá lỗi hệ thống.
   - `sec: ...` gia cố bảo mật.
   - `perf: ...` cải thiện hiệu năng / bộ nhớ.
   - `docs: ...` cập nhật tài liệu hướng dẫn.
4. Kiểm tra mã nguồn, chạy kiểm thử và gửi **Pull Request (PR)** về nhánh `main` kèm mô tả chi tiết các thiết bị đã thử nghiệm thành công.
