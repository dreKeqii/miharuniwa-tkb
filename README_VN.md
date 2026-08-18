# MiharuNiwa — TKB

**Ứng dụng Android offline-first để xem thời khóa biểu và bảng điểm.**

---

## Tổng quan

MiharuNiwa — TKB là ứng dụng Android gốc cho sinh viên xem thời khóa biểu hàng tuần
(TKB = *thời khóa biểu*) và bảng điểm học phần. Ứng dụng cào dữ liệu từ một trang WordPress
ngay trên thiết bị, lưu đệm toàn bộ để thời khóa biểu và PDF vẫn xem được khi offline, và có
thể tùy chọn dùng Gemini API để tự động đọc PDF thời khóa biểu / bảng điểm.

Ứng dụng, giao diện và comment trong code đều viết bằng tiếng Việt.

## Vì sao dự án này tồn tại

Đây là dự án cá nhân để giải quyết một vấn đề thực tế: cách chính thống để xem thời khóa biểu
buộc phải duyệt đi duyệt lại một trang web chậm và phụ thuộc cấu trúc. Mục tiêu là một client
Android nhanh, xem được khi offline, hiển thị cùng thông tin với ít thao tác hơn — không phải
sản phẩm thương mại.

## Tính năng

- **Cào dữ liệu lịch học** — tách cấu trúc trang lịch hàng tuần (`.wp-block-post-title a`)
  bằng Jsoup và lưu vào cơ sở dữ liệu Room cục bộ.
- **Offline-first** — tuần, chi tiết lịch, PDF và điểm đều được lưu đệm; app mở là hiện dữ liệu
  đã cache ngay, sau đó mới làm mới khi có cơ hội.
- **Ghim lịch học** — ghim một lịch học cụ thể (ví dụ *CS1 / CS2* + hệ) lên đầu màn hình chính
  để truy cập một chạm.
- **Trình xem PDF có zoom** — PDF trên Google Drive được tải về và hiển thị với zoom đa chạm và
  quán tính.
- **Cắt & xuất ảnh** — chọn một vùng của thời khóa biểu (ví dụ chỉ lớp của bạn) và lưu thành PNG
  độ phân giải gốc qua Storage Access Framework.
- **Thông báo cập nhật ngầm** — worker định kỳ kiểm tra file ID Google Drive mới/đổi (chỉ vào
  thứ Sáu và thứ Bảy) rồi đẩy thông báo khi có thay đổi.
- **Bảng điểm & học bạ** — duyệt bảng điểm theo lớp và tổng quan từng sinh viên; tùy chọn tự
  động bóc tách PDF điểm bằng Gemini API.
- **Biểu mẫu** — tải và xem các biểu mẫu hành chính dạng PDF.
- **Widget màn hình chính** — widget dựa trên Glance để xem nhanh lịch học.
- **Nhắc lớp học** — cấu hình báo thức sáng/chiều theo lớp đang theo dõi.

## Kiến trúc

Ứng dụng Compose một Activity, không dùng framework DI. `MainActivity` tự dựng đồ thị đối tượng
— `AppPreferences`, `AppDatabase` (Room), `ScheduleRepository`, `OkHttpClient`, `PdfDownloader`
— rồi truyền vào composable `TkbApp`.

```
MainActivity  ── dựng ──►  AppPreferences / AppDatabase / ScheduleRepository / PdfDownloader
        │
        └──►  TkbApp (NavHost + HorizontalPager) ──►  các màn hình trong ui/screens/
                        │
        data/ = Room + DataStore + cào Jsoup + bóc tách PDF bằng Gemini
        widget/ = widget màn hình chính Glance + cập nhật hằng ngày
```

- **Tầng dữ liệu** (`data/`): entity/DAO của Room, `AppPreferences` (DataStore) cho toàn bộ cấu
  hình người dùng, `ScheduleRepository` cho logic cào/offline, `GeminiClient` cho bóc tách PDF
  bằng AI.
- **Tầng UI** (`ui/`): theme Material 3 thuần dark viết tay (`ui/theme/Color.kt`), các màn hình
  trong `ui/screens/`.
- **Chạy ngầm** (`data/` + `widget/`): worker WorkManager cho kiểm tra cập nhật và widget,
  `AlarmManager` cho nhắc lớp học.

Xem `CLAUDE.md` để có phân tích kiến trúc đầy đủ mà người đóng góp sau này cần đọc.

## Ngăn xếp công nghệ

- Kotlin, Jetpack Compose (Material 3), Navigation Compose
- Room, DataStore Preferences, WorkManager
- Jsoup (cào HTML), OkHttp + Retrofit + Moshi
- Google GenAI SDK (Gemini API)
- Glance (app widget), Coil, `net.engawapg.lib:zoomable`, `vanniktech/android-image-cropper`
- Robolectric + Roborazzi (test đơn vị / ảnh chụp giao diện)

## Phát triển có hỗ trợ AI

Repository này được phát triển với sự hỗ trợ đáng kể từ AI. AI đã tạo và tái cấu trúc phần lớn
mã Kotlin và tham gia thảo luận kiến trúc. Con người chịu trách nhiệm xác định yêu cầu sản phẩm,
quyết định luồng hoạt động, đánh giá kiến trúc, kiểm thử trên thiết bị thật, loại bỏ những phần
cài đặt sai, quyết định UX và thứ tự ưu tiên, và cải tiến dần dựa trên trải nghiệm sử dụng thực tế.

Việc sử dụng AI được ghi nhận công khai ở đây, không che giấu.

## Hạn chế

- **Cào dữ liệu dễ vỡ** — ứng dụng phụ thuộc trực tiếp vào cấu trúc HTML của trang đích
  (`.wp-block-post-title`, `.wp-block-post-content p`). Nếu chủ trang đổi thiết kế, việc lấy lịch
  và điểm sẽ hỏng cho tới khi ứng dụng được cập nhật.
- **Không có backend** — mọi thao tác cào diễn ra phía client, nên nếu server giới hạn tốc độ
  hoặc dùng Cloudflare thì có thể bị chặn kết nối.
- **Chỉ hỗ trợ dark theme** — không có light mode hay dynamic color.
- **Dùng cho một mục đích riêng** — ứng dụng được tinh chỉnh cho một trang WordPress của một
  trường cụ thể.

## Cách build

Điều kiện tiên quyết: Android Studio (hoặc Android SDK + JDK). Build dùng `JAVA_HOME=~/.local/java`.

```bash
# Build + cài + mở trên thiết bị đang kết nối (khuyến nghị)
./build.sh

# Dùng trực tiếp gradle
./gradlew assembleDebug
./gradlew testDebugUnitTest        # test đơn vị (Robolectric/Roborazzi)
./gradlew connectedAndroidTest     # test instrumented
```

## Giấy phép

[MIT License](./LICENSE)
