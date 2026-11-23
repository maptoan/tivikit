# Hướng dẫn Đóng gói và Triển khai TiviKit trên thực tế

Tài liệu này hướng dẫn chi tiết quy trình xây dựng (build), đóng gói (package) và triển khai (deploy) ứng dụng **TiviKit** trên các thiết bị Android TV, Google TV và TV Box (đặc biệt là Amazon Fire TV Cube, Mi Box, Tanix, Skyworth, Sony, TCL...).

---

## 1. Tổng quan Kiến trúc TiviKit

TiviKit hoạt động theo mô hình **điều khiển trung tâm (Controller)** kết hợp **hệ sinh thái tiện ích nhúng (Embedded Helpers)**:
- **Ứng dụng chính (`vn.tivikit`):** Cài đặt trên điện thoại Android (hoặc chạy trực tiếp trên TV Box), đóng vai trò trung tâm điều khiển, quét mạng, gửi lệnh ADB và quản lý trạng thái.
- **Các tiện ích phụ (Assets):** Nằm sẵn trong thư mục `app/src/main/assets/` bên trong APK chính. Khi người dùng kích hoạt tính năng tương ứng, TiviKit sẽ tự động đẩy (push) và cài đặt các module này lên TV qua ADB mà không bắt người dùng phải cài thủ công từng file:
  - `tivikit-launcher.apk` (`vn.tivikit.launcher`): Giao diện Home kính mờ TiviKit Launcher 1.5.
  - `tvhome.apk` & `tvhome-xiaomi.apk` (`com.gathin.tvhome` / `com.ts.launcher`): Home sạch CleanYourTV được tối ưu/vá lỗi.
  - `khoa.apk` (`vn.tivikit.khoa`): Dịch vụ Khóa trẻ em OTP 6 số, khóa theo giờ và theo ứng dụng.
  - `hengio.apk` (`vn.tivikit.hengio`): Dịch vụ Hẹn giờ bật/tắt và Tự động khởi động lại (Auto Reboot).
  - `cast.apk` (`vn.tivikit.cast`): Trình nhận tín hiệu Cast SmartTube (DIAL receiver cho YouTube di động).
  - `mapvoice.apk` (`vn.tivikit.mapvoice`): Dịch vụ chuyển hướng giọng nói micro remote sang SmartTube và kênh truyền hình.
  - `iptv.apk` (`vn.tivikit.iptv`): Trình phát luồng IPTV Media3 tích hợp.
  - `kenh1.apk` .. `kenh6.apk` (`vn.tivikit.kenh*`): Các shortcut Kênh nhanh mở đài truyền hình.
  - `teleremote.apk` (`vn.tivikit.teleremote`): Dịch vụ Bot Telegram điều khiển TV từ xa.
  - `go.apk` (`vn.tivikit.go`): Bộ gõ tiếng Việt có dấu cho Android TV.
  - `tren-tv.jar`: Bytecode chạy ngầm bằng `app_process` trên TV để đọc tên app, trích xuất icon và kiểm tra trạng thái không cần cài đặt.

---

## 2. Quy trình Đóng gói (Build & Package)

### 2.1. Yêu cầu môi trường
- **Hệ điều hành:** Linux, macOS hoặc Windows (PowerShell).
- **Java Development Kit (JDK):** OpenJDK 17 hoặc Eclipse Temurin 17 (khuyến nghị).
- **Android SDK:** Build-Tools 35.x, Platform SDK 35.
- **Git** và môi trường dòng lệnh bash/PowerShell.

### 2.2. Thiết lập khoá ký Release (`keystore`)
Để APK có thể cài đặt và cập nhật đè (update) giữa các phiên bản mà không bị xung đột chữ ký:
1. Tạo file keystore (nếu chưa có):
   ```bash
   keytool -genkeypair -v -keystore tv-sach.keystore -alias tivikit -keyalg RSA -keysize 2048 -validity 10000
   ```
2. Tạo file `ky.properties` tại thư mục gốc của dự án:
   ```properties
   storeFile=tv-sach.keystore
   storePassword=mat_khau_keystore_cua_ban
   keyAlias=tivikit
   keyPassword=mat_khau_alias_cua_ban
   ```
   *(Nếu không tạo `ky.properties`, Gradle sẽ tự động fallback sang ký bằng khoá `debug` mặc định của Android SDK).*

### 2.3. Quy trình Build 2 bước

Quy trình đóng gói bắt buộc phải build các module phụ trước để nạp file `.apk` vào assets của app chính:

#### Bước 1: Build các app phụ và công cụ `tren-tv.jar`
Trên môi trường Linux / macOS / WSL:
```bash
chmod +x scripts/*.sh
scripts/dung-khoa.sh
scripts/dung-kenh.sh
scripts/dung-hen-gio.sh
scripts/dung-cast.sh
scripts/dung-mapvoice.sh
scripts/dung-iptv.sh
scripts/dung-teleremote.sh
scripts/dung-go.sh
scripts/dung-tivikit-launcher.sh
scripts/dung-tren-tv.sh
```

*(Trên Windows PowerShell thuần, bạn có thể thực hiện tương đương qua Gradle:)*
```powershell
./gradlew :khoa:assembleRelease; Copy-Item khoa/build/outputs/apk/release/khoa-release.apk app/src/main/assets/khoa.apk
./gradlew :launcher:assembleRelease; Copy-Item launcher/build/outputs/apk/release/launcher-release.apk app/src/main/assets/tivikit-launcher.apk
./gradlew :hengio:assembleRelease; Copy-Item hengio/build/outputs/apk/release/hengio-release.apk app/src/main/assets/hengio.apk
./gradlew :cast:assembleRelease; Copy-Item cast/build/outputs/apk/release/cast-release.apk app/src/main/assets/cast.apk
./gradlew :mapvoice:assembleRelease; Copy-Item mapvoice/build/outputs/apk/release/mapvoice-release.apk app/src/main/assets/mapvoice.apk
./gradlew :iptv:assembleRelease; Copy-Item iptv/build/outputs/apk/release/iptv-release.apk app/src/main/assets/iptv.apk
./gradlew :teleremote:assembleRelease; Copy-Item teleremote/build/outputs/apk/release/teleremote-release.apk app/src/main/assets/teleremote.apk
./gradlew :go:assembleRelease; Copy-Item go/build/outputs/apk/release/go-release.apk app/src/main/assets/go.apk
./gradlew :trentv:assembleRelease; Copy-Item trentv/build/outputs/apk/release/trentv-release.apk app/src/main/assets/tren-tv.jar
```

#### Bước 2: Build APK chính của TiviKit
Chạy lệnh biên dịch ứng dụng chính:
```bash
./gradlew :app:assembleRelease
```
File APK hoàn chỉnh sẽ được tạo tại:
`app/build/outputs/apk/release/app-release.apk` (đổi tên thành `TiviKit.apk` để phát hành).

### 2.4. Tự động hoá qua GitHub Actions CI/CD
Kho chứa đã tích hợp sẵn workflow tại `.github/workflows/phat-hanh.yml`:
1. Thiết lập các Secret trong repo GitHub (Settings -> Secrets -> Environments -> `phat-hanh`):
   - `KHOA_KY_BASE64`: Nội dung file keystore mã hoá base64.
   - `MAT_KHAU_KHO`: Mật khẩu keystore.
   - `TEN_KHOA`: Alias của key.
   - `MAT_KHAU_KHOA`: Mật khẩu của key.
2. Vào **Actions** -> Chọn **Phát hành** -> Bấm **Run workflow**. Hệ thống sẽ tự động chạy toàn bộ unit tests, build các app phụ, build app chính, ký số và phát hành bản Release trên GitHub kèm file `.sha256` và `cap-nhat.json`.

---

## 3. Hướng dẫn Triển khai trên thực tế

### Mô hình 1: Cài TiviKit lên điện thoại Android điều khiển TV (Khuyên dùng)
Đây là phương án chuẩn mực và thuận tiện nhất:

1. **Chuẩn bị trên TV Box (hoặc Amazon Fire TV Cube):**
   - Vào **Cài đặt (Settings)** -> **Thiết bị của tôi (My Fire TV / Device)** -> **Tuỳ chọn cho nhà phát triển (Developer Options)**.
   - Bật **ADB Debugging** = `ON`.
   - Kết nối TV Box và điện thoại Android vào **cùng một mạng Wi-Fi** (hoặc cùng dải mạng LAN).
2. **Cài TiviKit lên điện thoại Android:**
   - Chép file `TiviKit.apk` sang điện thoại và tiến hành cài đặt.
3. **Kết nối điện thoại với TV:**
   - Mở app TiviKit trên điện thoại.
   - Nhập địa chỉ IP của TV (ví dụ: `192.168.11.70:5555`) hoặc bấm Quét để tự dò tìm.
   - **Lưu ý quan trọng trên TV:** Màn hình TV sẽ hiển thị pop-up hỏi: *"Allow USB debugging?"* -> Dùng remote tích chọn **"Always allow from this computer"** rồi bấm **OK / Allow**.
4. **Vận hành các chức năng:**
   - **Tối ưu & Dọn rác:** Chuyển qua tab Dọn dẹp -> Chọn gói Mức 1 an toàn -> Bấm Dọn dẹp.
   - **Cài đặt Launcher:** Vào tab *Quản lý launcher* -> Chọn *TiviKit Launcher* -> Bấm Cài đặt & Đặt làm Home.
   - **Tiện ích:** Bật các thẻ *Cast SmartTube*, *Map Voice*, *Tele Remote* tuỳ theo nhu cầu; TiviKit sẽ tự động cài các gói phụ tương ứng lên TV.

---

### Mô hình 2: Cài trực tiếp TiviKit lên TV Box (Chế độ Standalone)
Dành cho người muốn TV Box tự chạy độc lập mà không cần qua điện thoại:

1. Cài đặt trực tiếp file APK chính vào TV Box qua USB hoặc qua lệnh ADB từ máy tính:
   ```powershell
   adb -s 192.168.11.70:5555 install -r TiviKit.apk
   ```
2. Mở ứng dụng TiviKit ngay trên màn hình TV (dùng chuột bay hoặc bàn rê).
3. App sẽ tự kết nối vào chính nó thông qua địa chỉ nội bộ: `127.0.0.1:5555`.
4. Màn hình TV sẽ hiện pop-up xác thực ADB cho `127.0.0.1` -> Chọn **"Luôn cho phép"** và bấm **OK**.

---

### Mô hình 3: Triển khai nhanh bằng PowerShell từ máy tính (Dành cho Kỹ thuật viên)
Dành cho việc cài đặt hàng loạt hoặc cấu hình nhanh qua cáp/Wi-Fi:

1. Kết nối ADB tới thiết bị:
   ```powershell
   $adb = "$HOME\platform-tools\adb.exe"
   & $adb connect 192.168.11.70:5555
   ```
2. Cài đặt các tiện ích mong muốn trực tiếp từ thư mục `app/src/main/assets/`:
   ```powershell
   # Cài TiviKit Launcher
   & $adb -s 192.168.11.70:5555 install -r app/src/main/assets/tivikit-launcher.apk

   # Cài Hẹn giờ & Auto Reboot
   & $adb -s 192.168.11.70:5555 install -r app/src/main/assets/hengio.apk

   # Cài Cast SmartTube
   & $adb -s 192.168.11.70:5555 install -r app/src/main/assets/cast.apk
   ```
3. Cấp quyền chạy ngầm không bị hệ thống Android tắt (tránh kill RAM):
   ```powershell
   & $adb -s 192.168.11.70:5555 shell "dumpsys deviceidle whitelist +vn.tivikit.launcher; am set-standby-bucket vn.tivikit.launcher active; cmd appops set vn.tivikit.launcher RUN_ANY_IN_BACKGROUND allow"
   ```

---

## 4. Những lưu ý An toàn Cốt tử khi Triển khai

1. **Bảo vệ lõi hệ thống Amazon Fire OS / Google TV:**
   - Đối với **Amazon Fire TV Cube 2nd Gen (`AFTR` / `raven`)**, tuyệt đối không tắt các gói dịch vụ phần cứng:
     - `com.amazon.vizzini`: Quản lý kết nối loa và tai nghe Bluetooth.
     - `com.amazon.device.rdmapplication`: Điều khiển mắt hồng ngoại (IR blaster) bật/tắt máy chiếu, amply.
     - `com.amazon.alta.h2clientservice`: Dịch vụ microphone nhận diện giọng nói Alexa xa trường (Far-field Voice).
     - `com.amazon.cloud9`: Trình duyệt Amazon Silk.
     - Các gói lõi: `com.amazon.tv.launcher`, `com.amazon.firehomestarter`, `com.amazon.tv.settings.v2`.
   - Danh sách này đã được mã hoá bảo vệ tự động trong mã nguồn `KeHoach.kt`.
2. **Cơ chế Watchdog tự phục hồi:**
   - TiviKit tích hợp bộ giám sát canh gác 3 phút (`watchdog`) khi thay đổi Launcher. Nếu sau 3 phút mất kết nối hoặc TV không xác nhận hoạt động ổn định, hệ thống sẽ tự động khôi phục lại Launcher gốc của hãng, tránh nguy cơ treo máy hoặc màn hình đen.
3. **Sao lưu trước khi thực hiện:**
   - Luôn sử dụng tính năng **Sao lưu (Backup)** trong TiviKit trước khi vô hiệu hoá ứng dụng hàng loạt. File sao lưu `.json` có thể được dùng để khôi phục một chạm hoặc chuyển thành file `.bat` / `.sh` chạy từ máy tính khi cần thiết.
