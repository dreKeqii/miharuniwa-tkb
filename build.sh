#!/bin/bash

# Build, cài đặt và khởi chạy ứng dụng trên thiết bị đã kết nối qua adb.
# Đây là cách build chính thống của dự án (xem CLAUDE.md).

echo "Thiết lập môi trường..."
export JAVA_HOME=~/.local/java
export PATH=$JAVA_HOME/bin:$PATH

# Clean trước khi build để loại bỏ cache icon cũ, đảm bảo nhận icon mới.
echo "Đang dọn dẹp và Build APK (Debug)..."
./gradlew clean assembleDebug --no-daemon

if [ $? -eq 0 ]; then
    echo "Build thành công! Đang cài đặt..."
    # Lấy đường dẫn APK được tạo ra.
    APK_PATH=$(find app/build/outputs/apk/debug/ -name "*.apk" | head -n 1)

    if [ -n "$APK_PATH" ]; then
        adb install -r "$APK_PATH"
        if [ $? -eq 0 ]; then
            echo "Cài đặt thành công! Đang xác định Package Name..."

            # Tự động lấy dòng chứa applicationId hoặc namespace trong cấu hình gradle của app.
            TARGET_LINE=$(grep -E "applicationId|namespace" app/build.gradle* 2>/dev/null | head -n 1)

            # Cắt lấy chuỗi Package Name nằm trong dấu nháy.
            PACKAGE_NAME=$(echo "$TARGET_LINE" | sed -E 's/.*["'\'']([^"'\'']+)["'\''].*/\1/')

            if [ -n "$PACKAGE_NAME" ]; then
                echo "Tìm thấy Package Name: $PACKAGE_NAME"
                echo "Đang mở ứng dụng..."
                # Dùng monkey để kích hoạt thẳng LAUNCHER của package.
                adb shell monkey -p "$PACKAGE_NAME" -c android.intent.category.LAUNCHER 1 > /dev/null 2>&1
            else
                echo "Lỗi: Không tìm thấy Package Name trong build.gradle. Vui lòng mở ứng dụng thủ công."
            fi
        else
            echo "Lỗi: Không thể cài đặt APK. Vui lòng kiểm tra kết nối USB Debugging."
        fi
    else
        echo "Lỗi: Không tìm thấy file APK sau khi build."
    fi
else
    echo "Lỗi: Build thất bại. Vui lòng kiểm tra log gradle."
fi
