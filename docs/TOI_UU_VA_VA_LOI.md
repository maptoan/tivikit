# Báo cáo Rà soát, Vá lỗi & Tối ưu hoá Toàn diện TiviKit

Tài liệu này ghi nhận toàn bộ kết quả rà soát của Hội đồng Chuyên gia và chi tiết kỹ thuật các bản sửa đổi, tối ưu hoá được triển khai trên 11 module của kho mã nguồn **TiviKit**.

---

## 1. Tổng quan Đợt Rà soát

- **Phạm vi:** Toàn bộ 11 Gradle module (`:app`, `:khoa`, `:launcher`, `:cast`, `:mapvoice`, `:hengio`, `:iptv`, `:kenh`, `:teleremote`, `:go`, `:trentv`).
- **Tổng số tập tin sửa đổi:** **73 tập tin** (+890 / -302 dòng mã nguồn).
- **Trạng thái kiểm thử:** 100% bài kiểm thử Unit Test (JUnit 4) vượt qua thành công, bao gồm các ca kiểm thử mới được bổ sung cho từng hạng mục vá lỗi.

---

## 2. Chi tiết 4 Giai đoạn Triển khai

### Giai đoạn 1: Gia cố Lõi ADB, Khôi phục & Danh mục Debloat (`:app`, `:go`)

| Tập tin sửa đổi | Vấn đề được phát hiện | Giải pháp kỹ thuật đã triển khai |
| :--- | :--- | :--- |
| `app/.../adb/KetNoiAdb.kt` | Trả về chuỗi rỗng khi luồng ADB đóng sớm trước dấu kết thúc; rò rỉ socket khi truyền file APK lớn; giữ cache công cụ cũ sau khi đổi TV. | - Theo dõi `@Volatile var daThayDau`, ném `IOException` khi luồng đóng trước dấu kết thúc để kích hoạt cơ chế `ketNoiLai()`.<br>- Bọc đóng `stream.close()` trong khối `finally` cho `caiApk`, `doVaoLenh`, `dayLen`, `ghiNhanh`.<br>- Reset `coHeadC = null; coDd = null` khi kết nối lại. |
| `app/.../adb/ThaoTacTv.kt` | Khi đổi Home hoặc khởi động lại dịch vụ phím, hàm xóa trắng toàn bộ trợ năng (`''`), làm vô hiệu hóa luôn cả dịch vụ Khóa trẻ em và Hẹn giờ; rủi ro tranh chấp file khi gia hạn watchdog. | - Viết lại regex chỉ lọc bỏ `$pkgHome` khỏi `enabled_accessibility_services`, bảo toàn các dịch vụ trợ năng khác.<br>- Ghi file gia hạn qua `.tmp` và `mv -f` nguyên tử.<br>- Bổ sung fallback `settings get secure android_id` khi đọc serial rỗng trên box nội địa. |
| `app/.../dulieu/KeHoach.kt` | Thiếu danh sách bảo vệ các gói dịch vụ phần cứng thiết yếu của dòng **Amazon Fire OS**; regex máy chiếu bắt nhầm trình duyệt Firefox Focus. | - Đưa toàn bộ các gói lõi Fire OS (`com.amazon.tv.launcher`, `com.amazon.firehomestarter`, `com.amazon.tv.settings.v2`, `com.amazon.imp`, `com.amazon.dcp`, `com.amazon.ssm`...) vào `GIU_CHINH_XAC`.<br>- Tinh chỉnh `GIU_MAY_CHIEU` nhận diện `geometr|ladder` và loại trừ rõ ràng `org.mozilla.focus`. |
| `app/.../dulieu/DongLenh.kt` | Chưa mở bọc các lệnh shell lồng nhau như `eval`, `sh -ec`, `su --command`; xử lý sai khi cờ `--user=0` nằm trước tên lệnh con; đường dẫn chứa `//` hoặc `..` vượt qua kiểm tra an toàn. | - Bổ sung hàm bóc tách `eval`, `sh -ec`, `su --command`, chuẩn hoá đường dẫn bằng `chuanDuongDan` trước khi đánh giá `rm -rf`. |
| `app/.../dulieu/DuLieu.kt` | Nhận diện sai mẫu TV khi hãng dùng thương hiệu con (Redmi/POCO của Xiaomi; Bravia của Sony; hãng 2 ký tự như LG); nhận serial giả từ chip Allwinner/Rockchip. | - Thêm bảng ánh xạ `tenHangChuan` (`mi/redmi/poco` -> `xiaomi`, `bravia` -> `sony`).<br>- Lọc bỏ các serial giả dạng `0123456789ABCDEF` trong `ThongTinTv.tuGetprop`. |
| `app/.../dulieu/GoiApk.kt` & `GoiY.kt` | Split APK có gạch ngang (`arm64-v8a`) không khớp; tìm nhầm bản `x86` thay vì `x86_64` cho VLC; so sánh sai phiên bản có hậu tố ngày build 8 số. | - Chuẩn hóa tên ABI `.replace('-', '_')`.<br>- Sửa ánh xạ `x86_64` cho VLC và cắt bỏ hậu tố ngày build `>= 19000101`. |

---

### Giai đoạn 2: Vá Lỗ hổng Bảo mật Dịch vụ TV & Khóa Trẻ Em (`:khoa`, `:cast`, `:teleremote`, `:mapvoice`, `:hengio`)

| Tập tin sửa đổi | Vấn đề được phát hiện | Giải pháp kỹ thuật đã triển khai |
| :--- | :--- | :--- |
| `khoa/.../KhoaDichVu.kt` | Trẻ em có thể lách cơ chế khóa nhập sai 5 lần bằng cách bấm phím thoát hoặc mở app khác trong 1,2 giây chờ; tồn tại khoảng trống 15 giây không khóa sau khi phụ huynh mở khóa tạm. | - Ghi nhận số lần sai ngay lập tức khi lớp phủ bị đóng hoặc đổi trạng thái trước khi hết thời gian chờ.<br>- Reset `appTren = ""` và kích hoạt quét lại sau 350ms khi thoát màn hình phụ huynh.<br>- Gắn cờ `FLAG_SECURE` chống chụp màn hình/ghi video chứa mã OTP. |
| `khoa/.../XacThuc.kt` & `XacNhan.kt` | Lỗi Fail-open khi dịch vụ trợ năng bị ngắt; đồng hồ TV nhảy lùi thời gian sau khi kết nối NTP làm sai lệch thời gian chặn. | - Chuyển sang cơ chế **Fail-closed**: Từ chối truy cập ngay nếu Khóa trẻ em đang bật mà dịch vụ nền chưa phản hồi.<br>- Tự động cắt ngắn thời gian chặn nếu đồng hồ hệ thống bị nhảy lệch quá 60 phút. |
| `cast/.../DienThoai.kt` & `ManCast.kt` | Lỗ hổng cho phép máy khách trong mạng gửi lệnh `next`/`previous` chen ngang mà không qua kiểm tra quyền; lộ mã ghép nối 12 số trên màn hình khi đang bật Khóa trẻ em. | - Kiểm tra mọi thiết bị đang nối đều có trạng thái `Xet.PHAT` mới cho phép nhận lệnh chuyển bài.<br>- Che mã ghép nối thành `•••• •••• ••••` trên màn hình TV cho đến khi phụ huynh xác nhận mã. |
| `teleremote/.../TeleRemoteDichVu.kt` | Rò rỉ bộ nhớ `Bitmap` khi nén ảnh chụp màn hình gửi Telegram; người dùng Telegram có thể chụp trộm màn hình lúc trẻ em đang nhập mã OTP. | - Gọi `recycle()` ngay cho các Bitmap trung gian sau khi nén JPEG.<br>- Chặn chụp và quay video màn hình nếu cửa sổ trên cùng thuộc gói `vn.tivikit.khoa`. |
| `mapvoice/.../Nghe.kt` & `hengio/.../BangHenGio.kt` | Rơi vào vòng lặp kết nối ADB vô tận khi TV chưa bấm cho phép; thiếu kiểm tra mã phụ huynh khi sửa lịch hẹn giờ một lần. | - Giới hạn thử lại tối đa 2 lần cách nhau 20 giây khi gặp mã từ chối kết nối.<br>- Yêu cầu xác nhận mã phụ huynh (`quaMa`) cho mọi thay đổi lịch hẹn giờ. |

---

### Giai đoạn 3: Tối ưu Hiệu năng, Bộ nhớ Launcher & Media (`:launcher`, `:iptv`, `:kenh`)

| Tập tin sửa đổi | Vấn đề được phát hiện | Giải pháp kỹ thuật đã triển khai |
| :--- | :--- | :--- |
| `launcher/.../GiaoDien.kt` & `DieuKhien.kt` | Hiện tượng giật khung hình (thrashing) do liên tục nạp và giải phóng Bitmap icon khi cuộn danh sách thư viện app; mất tiêu điểm D-pad khi di chuyển ứng dụng ở đầu thanh Dock. | - Đặt `LaunchedEffect` theo cặp `(app.goi, app.capNhat)` và duy trì ngưỡng cache tối thiểu 42 ảnh khi đang mở thư viện.<br>- Giữ focus D-pad cho ô đang di chuyển thông qua chỉ mục vị trí Dock. |
| `launcher/.../Kinh.kt` & `LamMo.kt` | Tốn gấp đôi bộ nhớ RAM do giải phóng ảnh nền ở định dạng ARGB_8888; tính toán thừa kênh Alpha cho nền mờ toàn màn hình. | - Đổi cấu hình giải mã ảnh nền sang `Bitmap.Config.RGB_565` (tiết kiệm 50% RAM đồ họa).<br>- Tối ưu hóa thuật toán Stack Blur bỏ qua tính Alpha, gán trực tiếp kênh opaque `0xFF000000`. |
| `launcher/.../DangPhat.kt` | Quét thống kê `UsageStatsManager` theo từng ứng dụng đơn lẻ gây nghẽn IPC liên tiến trình trên các TV Box chip yếu (Amlogic S905W, MediaTek). | - Gom truy vấn `queryEvents` thành 1 lần duy nhất cho toàn bộ tập hợp ứng dụng trong mỗi chu kỳ quét. |
| `iptv/.../Kho.kt` & `PhatKenh.kt` | Nguy cơ tràn bộ nhớ (`OutOfMemoryError`) khi tải danh sách M3U dung lượng lớn; khởi tạo lại `ExoPlayer` mỗi lần đổi kênh gây trễ 1-2 giây. | - Giới hạn kích thước file M3U tối đa 15 MB, đọc theo bộ đệm 8 KB.<br>- Tái sử dụng instance `ExoPlayer` hiện có khi chuyển kênh, giảm độ trễ chuyển kênh xuống dưới 0.5 giây. |

---

### Giai đoạn 4: Giao diện Trung tâm, Scrcpy, LG webOS & Mã trên TV (`:app`, `:trentv`)

| Tập tin sửa đổi | Vấn đề được phát hiện | Giải pháp kỹ thuật đã triển khai |
| :--- | :--- | :--- |
| `app/.../adb/GiaoThucScrcpy.kt` & `ManHinhTv.kt` | Crash ứng dụng (`NegativeArraySizeException`) khi nhận dữ liệu rác từ cổng Scrcpy; rò rỉ luồng video và decoder phần cứng khi TV ngắt kết nối đột ngột. | - Kiểm tra kích thước gói `co in 0..(16 * 1024 * 1024)` trước khi cấp phát mảng byte.<br>- Đảm bảo giải phóng `MediaCodec.release()` và đóng `AdbStream` an toàn trong khối `finally`. |
| `app/.../ui/UngDung.kt` & `ManLauncher.kt` | Giải mã ảnh nền và icon bằng `BitmapFactory` trực tiếp trên Main Thread (UI Thread) gây hiện tượng khựng giao diện (jank). | - Chuyển toàn bộ tác vụ giải mã sang `Dispatchers.IO` kết hợp `produceState` bất đồng bộ. |
| `app/.../lg/KetNoiLg.kt` & `SshLg.kt` | Rò rỉ kết nối HTTP ConnectionPool của OkHttp khi ngắt kết nối TV LG; đọc luồng SSH theo byte làm vỡ ký tự tiếng Việt UTF-8 đa byte. | - Bổ sung `http.connectionPool.evictAll()` khi đóng phiên.<br>- Đọc luồng SSH bằng `InputStreamReader` mã hoá UTF-8 với bộ đệm `CharArray(4096)`. |
| `app/.../adb/TrenTv.kt` | Lỗi nuốt ký tự hoặc văng ứng dụng khi chuỗi Unicode thoát `\uXXXX` bị lỗi hoặc dở dang. | - Kiểm tra chặt chẽ độ dài chuỗi và kiểm tra ký tự Hex hợp lệ trước khi giải mã ký tự Unicode. |

---

### Giai đoạn 5: Gia cố Phòng thủ Chiều sâu (Defense-in-Depth) cho MTVkit 1.27.0

| Tập tin sửa đổi | Vấn đề được phát hiện | Giải pháp kỹ thuật đã triển khai |
| :--- | :--- | :--- |
| `*/src/main/AndroidManifest.xml`, `MoTuDong.kt`, `PhatKenh.kt` | Các Activity nội bộ (`XacNhan`, `KhoaNgay`, `MoTuDong`, `PhatKenh`) đặt `exported="true"` không kèm quyền hệ thống, cho phép app bên thứ 3 trên TV bắn `Intent` giả mạo. | - Khai báo quyền chữ ký số `vn.tivikit.permission.NOI_BO` (`protectionLevel="signature"`) đồng bộ trên 8 module liên thông và gắn cứng lên cả 4 Activity nội bộ, kèm kiểm tra `launchedFromPackage` trên Android 14+. |
| `app/.../dulieu/GoiY.kt` (`TaiLink`), `BoCaiMau.kt`, `PhienLamViec.kt` | Trình tải APK tự theo redirect `301/302` chưa chặn hạ cấp `HTTPS → HTTP` trên mirror cộng đồng và chưa áp trần kích thước tải. | - Bắt buộc `HTTPS` cho toàn bộ kho gợi ý `GoiY`, cấm tuyệt đối redirect hạ cấp `HTTPS → HTTP`, chặn IP nội bộ/DNS rebinding và giới hạn kích thước gói tải ở mức 1,5 GB. |
| `iptv/.../DanhSach.kt`, `Kho.kt`, `app/.../Iptv.kt`, `mapvoice/.../TuChon.kt` | Danh sách M3U từ nguồn ngoài có thể cài cắm URL `http://127.0.0.1` / `[::1]` / `169.254.x.x` hoặc `file://` gây lỗi SSRF thăm dò dịch vụ nội bộ trên TV. | - Lọc bỏ mọi địa chỉ loopback (`127.0.0.0/8`, `::1`, dạng số nguyên/hex), link-local (`169.254.0.0/16`) và giao thức ngoài `http/https` ngay khi phân tích M3U lẫn trên từng bước redirect (vẫn giữ tương thích NAS mạng nhà `192.168.x.x`). |
| `app/.../lg/KetNoiLg.kt`, `SshLg.kt`, `ui/LgTivi.kt` | Chấp nhận mù mọi chứng chỉ tự ký WSS cổng 3001 và SSH Host Key cổng 22 của TV LG, có nguy cơ bị MITM trên mạng LAN không tin cậy. | - Triển khai cơ chế **TOFU (Trust-On-First-Use)**: ghim dấu vân tay SHA-256 của chứng chỉ TLS và khóa SSH ngay lần ghép đôi đầu tiên (trước bước gửi mật khẩu root) và cảnh báo nếu phát hiện tráo khóa. |
| `MaHoaKho.kt` (`:khoa`, `:teleremote`, `:app`), `khoa/Kho.kt`, `teleremote/CauHinh.kt` | Bot Token Telegram, khóa TOTP Khóa trẻ em và khóa/mật khẩu TV LG lưu dạng rõ trong `SharedPreferences`. | - Mã hoá bằng **AES-256-GCM qua phần cứng `AndroidKeyStore`** (tiền tố `enc1:`, IV ngẫu nhiên 12 byte), tự động nâng cấp mượt từ dữ liệu cũ và giữ nguyên cơ chế *fail-closed* của Khóa trẻ em. |

