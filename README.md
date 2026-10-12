# Mondiro Timer Alarm

App Android cá nhân (Z Fold 7) gồm 2 tính năng nhắc nhở theo thời gian:

1. **Nhắc mua đồ sáng cho Ba** — bật/tắt, khi bật sẽ hiện nhắc toàn màn hình mỗi ngày lúc 11:00.
2. **Nhắc hết hạn gói 3G Mobifone** — tự đọc SMS từ đầu số 9199 để lấy ngày hết hạn, nhắc trước 1 ngày (chỉ hiện màn hình, không kêu), sau đó lặp lại mỗi 6 tiếng cho tới khi phát hiện SMS xác nhận đã mua gói mới.

Giao diện tông sáng (chuẩn Mondiro), có animation nút; màn ngoài Z Fold 7 hiển thị 1 cột, màn trong 2 cột.

## Build & tự cập nhật

- Mỗi lần push, GitHub Actions build APK — vào tab **Actions** để tải file `.apk`.
- Trên nhánh `main`, nếu `versionName` (trong `app/build.gradle.kts`) chưa có release, workflow tự tạo GitHub Release `v<versionName>` kèm APK, nội dung lấy từ `RELEASE_NOTES.md`.
- App tự kiểm tra release mới khi mở, và có nút **Kiểm tra bản cập nhật** ở góc phải trên. Đang dùng bản mới nhất thì hiện chữ **latest** màu xanh lá.
- Để bản mới cài đè được bản cũ, APK phải luôn ký cùng 1 khoá: thêm 2 secret `SIGNING_KEYSTORE_BASE64` và `SIGNING_PASSWORD` trong *Settings → Secrets and variables → Actions*. Chưa có secret thì vẫn build được, nhưng không phát hành bản tự cập nhật.

Created by Mondiro
