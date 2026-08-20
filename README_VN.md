# MiharuNiwa — TKB

**Ứng dụng Android cá nhân offline-first để xem thời khóa biểu và bảng điểm.**

---

## Tổng quan (AI viết)

MiharuNiwa — TKB là ứng dụng Android gốc cho sinh viên trường Cao đẳng Đồng Khởi xem thời khóa biểu hàng tuần
(TKB = *thời khóa biểu*) và bảng điểm học phần. Ứng dụng cào dữ liệu từ một trang WordPress
ngay trên thiết bị, lưu đệm toàn bộ để thời khóa biểu và PDF vẫn xem được khi offline, và có
thể tùy chọn dùng Gemini API để tự động đọc PDF thời khóa biểu (cho thông báo môn học) hoặc bảng điểm.

Ứng dụng, giao diện và comment trong code đều viết bằng tiếng Việt.

## Vì sao dự án này tồn tại

Đây là dự án cá nhân để giải quyết vấn đề thực tế: web trường thiết kế quá ngu và chậm, thời khóa biểu và bảng điểm đều là file PDF và dùng iframe dẫn link từ Google Drive, hình ảnh cho ra trên web không thể nào nát hơn, muốn nét phải mở qua Google Drive.

Quá mệt với cái quy trình phức tạp chỉ để xem và chụp thời khóa biểu, thế là app này ra đời, có thể xem thời khóa biểu mới ngay trên app Android native, không cần mở web, đảm bảo mượt hơn web, nhưng load thì vẫn chậm, giới hạn web trường nó thế rồi. Ngoài ra còn có bảng điểm, sổ tay sinh viên và các biểu mẫu của trường.

Đây là dự án cá nhân, hiện chỉ 1 người dùng, không thương mại, thằng tạo ra nó cũng chỉ dùng nó đến giữa 2027. Tốt nghiệp xong cũng cho cút luôn.

Gần như toàn bộ code đều được viết bằng AI, [cụ thể](#phát-triển-có-hỗ-trợ-ai-ai-viết).

Tới đây thôi, khúc dưới AI viết hết, đọc cũng được mà không đọc cũng được, chả sao.

## Tính năng (AI viết)

- **Cào dữ liệu lịch học** — dùng Jsoup tách link các tuần học (`.wp-block-post-title a`) rồi
  nhét hết vô database Room cục bộ, thế là xong.
- **Offline-first** — tuần, chi tiết lịch, PDF, điểm số đều cache hết. Mở app là thấy dữ liệu
  ngay, không cần mạng; có mạng thì nó tự đi làm mới, lúc nào không biết cũng được.
- **Ghim lịch học** — ghim đúng cái lịch mình học (ví dụ *CS1 / CS2* + hệ) lên đầu màn hình
  chính, một chạm là mở, đỡ phải mò.
- **Trình xem PDF có zoom** — PDF trên Google Drive được tải về xem thẳng trong app, zoom đa
  chạm mượt, có cả quán tính.
- **Cắt & xuất ảnh** — kéo chọn đúng vùng cần (ví dụ chỉ phần lớp mình học) rồi lưu PNG nét
  căng, không vỡ ảnh như chụp màn hình web qua Storage Access Framework.
- **Thông báo cập nhật ngầm** — worker âm thầm chạy, chỉ thứ Sáu với thứ Bảy mới đi kiểm tra
  file ID Google Drive đổi chưa, có đổi thì thả thông báo, không có thì im.
- **Bảng điểm & học bạ** — xem bảng điểm theo lớp và tổng quan sinh viên; lười thì bật Gemini
  API cho nó tự đọc PDF điểm hộ.
- **Biểu mẫu** — tải và xem mấy cái biểu mẫu hành chính PDF ngay trong app.
- **Widget màn hình chính** — widget Glance liếc qua là biết lịch hôm nào, khỏi mở app.
- **Nhắc lớp học** — đặt báo thức riêng cho lớp sáng/chiều của mình, khỏi sợ quên.

## Kiến trúc (AI viết)

Compose một Activity duy nhất, không dùng framework DI nào hết — `MainActivity` tự dựng đồ
thể đối tượng bằng tay: `AppPreferences`, `AppDatabase` (Room), `ScheduleRepository`,
`OkHttpClient` với `PdfDownloader` — rồi nhét hết vô composable `TkbApp`.

```
MainActivity  ── dựng ──►  AppPreferences / AppDatabase / ScheduleRepository / PdfDownloader
        │
        └──►  TkbApp (NavHost + HorizontalPager) ──►  các màn hình trong ui/screens/
                        │
        data/ = Room + DataStore + cào Jsoup + bóc tách PDF bằng Gemini
        widget/ = widget màn hình chính Glance + cập nhật hằng ngày
```

- **Tầng dữ liệu** (`data/`): entity/DAO Room phục vụ cào và lưu offline, `AppPreferences`
  (DataStore) giữ hết cấu hình người dùng, `ScheduleRepository` lo toàn bộ logic cào/đệm
  offline, `GeminiClient` thì nhờ AI đọc PDF điểm.
- **Tầng UI** (`ui/`): theme Material 3 dark-only tự viết tay, màu dồn hết trong
  `ui/theme/Color.kt`, màn hình nằm trong `ui/screens/`.
- **Chạy ngầm** (`data/` + `widget/`): worker WorkManager lo chuyện kiểm tra cập nhật với
  widget, `AlarmManager` lo chuyện nhắc lớp học.

Muốn biết đầy đủ thì đọc `CLAUDE.md`, phần đó viết cho người nào muốn nhúng tay vào code sau này.

## Tech Stack (AI viết)

- Kotlin, Jetpack Compose (Material 3), Navigation Compose
- Room, DataStore Preferences, WorkManager
- Jsoup (cào HTML), OkHttp + Retrofit + Moshi
- Google GenAI SDK (Gemini API)
- Glance (app widget), Coil, `net.engawapg.lib:zoomable`, `vanniktech/android-image-cropper`

## Phát triển có hỗ trợ AI (AI viết)

Nói thẳng: repo này AI viết gần hết. AI tạo mới lẫn refactor phần lớn Kotlin, và cũng tham
gia bàn bạc kiến trúc đàng hoàng. Còn con người (tức tao) lo phần quan trọng hơn: quyết định
app phải làm gì, chọn luồng chạy, thẩm định kiến trúc, test trên máy thật, gạt bỏ code AI
viết sai — chuyện này xảy ra thường xuyên — chốt UX và thứ tự ưu tiên, rồi mài giũa dần theo
kiểu dùng thực tế.

Dùng AI thì ghi rõ là dùng AI (Gemini & Claude trong Antigravity), việc gì phải giấu.

## Hạn chế (AI viết)

- **Cào dữ liệu dễ vỡ** — app bám chết cứng vào cấu trúc HTML của web trường
  (`.wp-block-post-title`, `.wp-block-post-content p`). Trường đổi giao diện cái là tèo,
  phải chờ cập nhật app mới xem lại được.
- **Không có backend** — mọi thứ cào ngay trên máy, server mà giới hạn tốc độ hay dựng
  Cloudflare lên là chịu phép.
- **Chỉ có dark theme** — không light mode, không dynamic color, thích thì dùng, không thì thôi.
- **Chỉ dùng cho đúng một nơi** — app tinh chỉnh cho đúng cái trang WordPress của trường này,
  nơi khác dùng không được.
- **Chả có test tự động nào hết** — Robolectric với Roborazzi cài vô cho có rồi để đó ngủ
  luôn; từ lúc khai sinh tới giờ toàn test tay trên máy thật. Code sai thì người dùng lãnh
  đủ trước, coi như là tester bất đắc dĩ.

## Cách build (AI viết)

Cần Android Studio (hoặc Android SDK + JDK). Script tự set
`JAVA_HOME=~/.local/java` cho Linux — ai dùng Windows/macOS thì tự sửa [build.sh](./build.sh).
Nó sẽ clean, build APK debug, rồi cài qua adb và tự mở app luôn trên máy đang cắm.

```bash
# Build + cài + mở luôn trên máy đang cắm cáp
./build.sh

# Hoặc chạy gradle tay
./gradlew assembleDebug
```

## Giấy phép (AI viết)

[MIT License](./LICENSE) — lấy làm gì cũng được, trừ kiện tao.
