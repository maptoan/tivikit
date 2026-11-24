# Danh mục Gói Ứng dụng & Hướng dẫn Tối ưu hoá TV An toàn

Tài liệu này cung cấp hướng dẫn chi tiết về các nguyên tắc vô hiệu hoá ứng dụng rác (debloat), danh sách các gói lõi bắt buộc phải giữ lại và cấu hình tối ưu hoá an toàn cho các dòng thiết bị phổ biến, đặc biệt là **Amazon Fire TV Cube 2nd Gen**.

---

## 1. Nguyên tắc An toàn Cốt tử

Khi tối ưu hoá và dọn dẹp hệ thống TV qua ADB:
1. **Chỉ vô hiệu hoá (`pm disable-user --user 0`), tuyệt đối không gỡ (`pm uninstall`):**
   - Vô hiệu hoá giúp ứng dụng ngừng chạy ngầm, không chiếm dụng CPU và giải phóng RAM tức thì nhưng vẫn giữ lại file APK gốc trong phân vùng hệ thống (`/system`).
   - Khi có sự cố, có thể khôi phục tức thì bằng lệnh `pm enable --user 0 <package>` mà không cần flash lại ROM hoặc reset cứng thiết bị.
2. **Không đụng chạm các thành phần phần cứng:**
   - Bluetooth stack (kết nối remote, tai nghe, loa ngoài).
   - Mắt hồng ngoại / CEC HDMI (điều khiển TV, máy chiếu, soundbar).
   - Microphone / Voice recognition (nhận diện giọng nói từ remote hoặc thân máy).
   - Wi-Fi Driver & Network Configuration (tránh mất kết nối mạng và mất quyền ADB).

---

## 2. Hướng dẫn Tối ưu hoá cho Amazon Fire TV Cube 2nd Gen (`AFTR` / `raven`)

Amazon Fire TV Cube 2nd Gen chạy hệ điều hành **Fire OS 7 (dựa trên Android 9, SDK 28, chip Amlogic S922X)**. Dòng máy này tích hợp micro thu âm trường xa (Far-field microphones), loa ngoài tích hợp và mắt hồng ngoại IR phát đa hướng.

### 2.1. Cấu hình Mức 1: An toàn Tuyệt đối (Đã kiểm nghiệm thành công)

Gói 22 dịch vụ thừa không ảnh hưởng tới bất kỳ tính năng phần cứng nào:

```powershell
$pkgs = @(
  "com.amazon.tv.notificationcenter",    # Trung tâm thông báo & gợi ý mua sắm
  "com.amazon.minitv.android.app",       # Dịch vụ video MiniTV quảng cáo
  "com.amazon.csm.htmlruntime",          # Trình chạy quảng cáo HTML ngầm
  "com.amazon.dpcclient",                # Client phân phối cấu hình từ xa
  "com.amazon.tv.ffsprovisioneeclient",  # Frustration-Free Setup tự động tìm thiết bị
  "com.amazon.tv.legal.notices",         # Điều khoản pháp lý hiển thị sẵn
  "com.amazon.tv.legalresources",        # Tài nguyên pháp lý
  "com.amazon.tv.ooberesource",          # Hướng dẫn thiết lập ban đầu (OOBE)
  "com.amazon.dummy.alarmclock",         # Ứng dụng giả lập Báo thức
  "com.amazon.dummy.calendar",           # Ứng dụng giả lập Lịch
  "com.amazon.dummy.contacts",           # Ứng dụng giả lập Danh bạ
  "com.amazon.dummy.gallery",            # Ứng dụng giả lập Thư viện ảnh
  "com.amazon.dummy.music",              # Ứng dụng giả lập Nhạc
  "com.amazon.dummy.settings",           # Ứng dụng giả lập Cài đặt
  "com.android.simappdialog",            # Hộp thoại SIM (TV Box không dùng SIM)
  "com.android.traceur",                 # Bộ ghi nhật ký vết hệ thống (gây tốn ghi flash)
  "com.android.onetimeinitializer",      # Bộ khởi tạo 1 lần sau khi xuất xưởng
  "com.android.htmlviewer",              # Trình xem HTML cục bộ
  "com.android.providers.calendar",      # Provider lịch Android
  "com.android.providers.contacts",      # Provider danh bạ
  "com.android.providers.userdictionary",# Từ điển gõ bàn phím
  "com.android.wallpapercropper"         # Bộ cắt ảnh nền Android cũ
)

foreach ($p in $pkgs) {
  adb shell "pm disable-user --user 0 $p; am force-stop $p"
}
```

### 2.2. Các Gói Lõi BẮT BUỘC PHẢI GIỮ LẠI trên Fire TV Cube 2

Để giữ trọn vẹn các nhu cầu sử dụng thực tế (dùng trình duyệt Silk, điều khiển giọng nói Alexa rảnh tay, kết nối loa Bluetooth, điều khiển máy chiếu qua IR):

| Tên gói (`package`) | Vai trò phần cứng / hệ thống | Hậu quả nếu vô hiệu hoá |
| :--- | :--- | :--- |
| `com.amazon.vizzini` | Trình quản lý kết nối Bluetooth | **Mất kết nối** remote Bluetooth và không thể nghe qua loa/tai nghe Bluetooth. |
| `com.amazon.device.rdmapplication` | Điều khiển mắt hồng ngoại IR Blaster | **Không điều khiển được** máy chiếu, soundbar và amply bằng giọng nói/remote. |
| `com.amazon.alta.h2clientservice` | Dịch vụ microphone Alexa trường xa | **Mất tính năng** gọi "Alexa" rảnh tay trên thân Cube. |
| `com.amazon.cloud9` | Trình duyệt web Amazon Silk Browser | **Mất trình duyệt** Silk tích hợp của hãng. |
| `com.amazon.tv.launcher` | Giao diện Fire TV Home gốc | Cần giữ khi chưa cài launcher thay thế; MTVkit dùng cơ chế mở đè. |
| `com.amazon.firehomestarter` | Trình giám sát nút Home phần cứng | Mất phản hồi nút Home trên remote Fire TV. |
| `com.amazon.tv.settings.v2` | Trình Cài đặt màn hình TV | Không thể truy cập menu Cài đặt Wi-Fi, Hiển thị và Âm thanh. |
| `com.amazon.tv.nimh` | Dịch vụ điều khiển mạng và thiết bị | Ảnh hưởng đến khả năng duy trì kết nối mạng ổn định. |
| `com.amazon.imp` & `com.amazon.dcp` | Dịch vụ định danh và xác thực phần cứng | Gây lỗi xác thực tài khoản và crash dịch vụ ngầm. |

---

## 3. Khôi phục Hệ thống khi Cần thiết

Nếu muốn đưa toàn bộ thiết bị về lại trạng thái ban đầu:
```powershell
# Bật lại tất cả các gói đã tắt
foreach ($p in $pkgs) {
  adb shell "pm enable --user 0 $p"
}

# Đặt lại Home mặc định về Fire TV Launcher
adb shell "cmd package set-home-activity --user 0 com.amazon.tv.launcher/.ui.HomeActivity_vNext"
```
Hoặc sử dụng tính năng **Khôi phục một chạm** trực tiếp trên ứng dụng MTVkit.
