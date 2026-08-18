# Tài liệu Tính năng & Kiến trúc Dự án (Miharuniwa TKB)

Tài liệu này tổng hợp toàn bộ các tính năng, sơ đồ luồng dữ liệu, điểm mạnh/yếu và bản đồ file nguồn của ứng dụng để thuận tiện cho việc tra cứu và bảo trì nhanh.

---

## 1. Bản đồ File nguồn (Source Code Map)

### 📌 Phần lõi cấu hình & Điều hướng (Core & Navigation)
*   **[MainActivity.kt](app/src/main/java/com/miharuniwa/tkb/MainActivity.kt)**: Điểm khởi tạo ứng dụng, khởi dựng Room Database, OkHttpClient và lập lịch Worker chạy ngầm.
*   **[TkbApp.kt](app/src/main/java/com/miharuniwa/tkb/TkbApp.kt)**: Khởi tạo Jetpack Compose Navigation và định nghĩa các luồng chuyển màn hình (`main` ↔ `detail` ↔ `pdf`).

### 📦 Xử lý Dữ liệu & Lưu trữ (Data Layer)
*   **[AppDatabase.kt](app/src/main/java/com/miharuniwa/tkb/data/AppDatabase.kt)**: Cấu hình Room DB, quản lý các Entity (`WeekItemEntity`, `ScheduleDetailEntity`) và DAO truy vấn.
*   **[AppPreferences.kt](app/src/main/java/com/miharuniwa/tkb/data/AppPreferences.kt)**: Quản lý cài đặt cấu hình qua Jetpack DataStore (Root URL nguồn, Trạng thái bật/tắt thông báo, các Hệ học đang theo dõi).
*   **[ScheduleRepository.kt](app/src/main/java/com/miharuniwa/tkb/data/ScheduleRepository.kt)**: Scraping dữ liệu từ web bằng Jsoup. Xử lý logic offline-first và tính toán động nhãn thời gian tương đối (`[Tuần hiện tại]`, `[Tuần tiếp]`, `[Tuần trước]`).
*   **[ScheduleUpdateWorker.kt](app/src/main/java/com/miharuniwa/tkb/data/ScheduleUpdateWorker.kt)**: Sử dụng WorkManager chạy ngầm định kỳ mỗi giờ (tối ưu hóa chỉ quét vào Thứ 6 & Thứ 7) để phát hiện tuần mới hoặc sự thay đổi của file Drive đã lưu, gửi thông báo hệ thống ngay lập tức.
*   **[PdfDownloader.kt](app/src/main/java/com/miharuniwa/tkb/data/PdfDownloader.kt)**: Tải và cache file PDF từ link Google Drive.
*   **[ImageSaver.kt](app/src/main/java/com/miharuniwa/tkb/data/ImageSaver.kt)**: Lưu trữ ảnh thời khóa biểu đã cắt vào bộ nhớ máy thông qua Storage Access Framework (SAF).

### 🎨 Giao diện Người dùng (UI Layer - Jetpack Compose)
*   **[MainScreen.kt](app/src/main/java/com/miharuniwa/tkb/ui/screens/MainScreen.kt)**: Màn hình chính chứa danh sách các tuần thời khóa biểu và khu vực ghim nhanh (Pin) lịch học yêu thích lên đầu trang.
*   **[DetailScreen.kt](app/src/main/java/com/miharuniwa/tkb/ui/screens/DetailScreen.kt)**: Màn hình hiển thị danh sách các phân hệ học theo Cơ sở (CS1, CS2), hỗ trợ lọc và ghim lịch.
*   **[PdfViewerScreen.kt](app/src/main/java/com/miharuniwa/tkb/ui/screens/PdfViewerScreen.kt)**: Trình đọc file PDF thời khóa biểu với cơ chế zoom nâng cao và tính năng cắt ảnh (Crop) thời khóa biểu.
*   **[SettingsBottomSheet.kt](app/src/main/java/com/miharuniwa/tkb/ui/screens/SettingsBottomSheet.kt)**: Sheet cấu hình các tùy chọn (Root URL, theo dõi hệ học, bật/tắt nhận thông báo).
*   **[ImageCropper.kt](app/src/main/java/com/miharuniwa/tkb/ui/components/ImageCropper.kt)**: Công cụ cắt ảnh tương tác trực quan cao, cho phép di chuyển và chọn vùng cắt để lưu trữ.

---

## 2. Các Tính năng Chính & Luồng hoạt động

### 1️⃣ Tự động cào dữ liệu (Jsoup Web Scraping)
*   **Mô tả**: Tự động parse cấu trúc HTML của website đích (dựa trên nền tảng WordPress) để bóc tách thông tin các tuần học.
*   **Luồng hoạt động**: Quét class `.wp-block-post-title a` để lấy danh sách tuần -> Khi ấn vào chi tiết, quét `.wp-block-post-content p` và thẻ `iframe` để lấy mã file PDF Google Drive của từng hệ học theo các Cơ sở (CS1/CS2).

### 2️⃣ Ghim Lịch học nhanh (Pin Schedule)
*   **Mô tả**: Cho phép ghim trực tiếp một phân hệ (ví dụ: `Hệ A - CS1`) ra ngay màn hình chính. 
*   **Đặc điểm**: Mở app lên là lịch ghim hiển thị ngay ở trên cùng, giúp sinh viên vào thẳng thời khóa biểu của mình chỉ bằng 1 lượt chạm mà không cần tìm kiếm lại.

### 3️⃣ Xem PDF Offline & Zoom mượt mà
*   **Mô tả**: Tải và hiển thị file PDF thời khóa biểu trực quan.
*   **Đặc điểm**: Tích hợp thư viện `engawapg/zoomable` giúp thao tác zoom đa điểm, vuốt chuyển vùng mượt mà, hỗ trợ quán tính tự nhiên. File PDF sau khi tải lần đầu sẽ được cache lại để hiển thị offline.

### 4️⃣ Cắt & Xuất ảnh Thời khóa biểu (Crop & Export)
*   **Mô tả**: Cho phép chọn một vùng cụ thể trên thời khóa biểu (ví dụ: thời khóa biểu của riêng lớp mình) để lưu thành dạng ảnh PNG.
*   **Đặc điểm**: Lưu trữ thông qua Storage Access Framework (SAF) giúp lưu trực tiếp vào thư mục Downloads/Pictures tùy chọn của người dùng. Hình ảnh xuất ra có độ phân giải gốc cực nét.

### 5️⃣ Theo dõi Cập nhật ngầm (Background Update Alerts)
*   **Mô tả**: Kiểm tra cập nhật lịch từ nhà trường tự động khi thiết bị kết nối mạng.
*   **Thông minh**: 
    *   Chỉ chạy kiểm tra định kỳ vào Thứ 6 và Thứ 7 (ngày thường cập nhật lịch).
    *   So sánh File ID của Google Drive từ web với Database. Nếu phát hiện File ID thay đổi (giáo viên sửa hoặc up đè file mới lên Drive), hệ thống sẽ gửi thông báo đẩy để nhắc sinh viên xem lại lịch mới.

---

## 3. Đánh giá Điểm mạnh & Điểm yếu

### 💪 Điểm mạnh (Pros)
1.  **Trải nghiệm người dùng tốt**: Chế độ Dark Mode mặc định dịu mắt, tính năng ghim lịch rất thực tế, thao tác xem PDF & Crop mượt mà không có độ trễ.
2.  **Tối ưu pin & tài nguyên**: Thiết kế Worker chạy ngầm thông minh (chỉ chạy mạnh vào Thứ 6/Thứ 7) giúp giảm thiểu hao tổn pin tối đa.
3.  **Hỗ trợ Offline**: Lưu trữ cục bộ dữ liệu tuần/chi tiết lịch bằng Room DB và cache PDF, cho phép xem lại lịch học cũ hoặc lịch đã tải mà không cần kết nối mạng.

### ⚠️ Điểm yếu (Cons)
1.  **Phụ thuộc chặt vào cấu trúc HTML**: Vì cào dữ liệu trực tiếp trên ứng dụng client thông qua Jsoup, nếu quản trị viên website trường đổi cấu trúc thẻ HTML hoặc class CSS của bài viết (`.wp-block-post-title`, `.wp-block-post-content p`), tính năng cào dữ liệu của ứng dụng sẽ ngừng hoạt động cho đến khi cập nhật ứng dụng bản mới.
2.  **Thiếu máy chủ trung gian (Backend)**: Do Client tự thực hiện tác vụ Scraping, nếu trang web trường có cơ chế chặn IP (Rate-limiting / Cloudflare) thì ứng dụng dễ bị từ chối kết nối.
