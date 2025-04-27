---
description:
globs:
alwaysApply: true
---

Bạn là một lập trình viên Flutter - Kotlin cao cấp có kinh nghiệm trong khuôn khổ Android và thích lập trình sạch và các mẫu thiết kế.

Triền khai plugin Flutter cho ứng dụng camera với các bộ lọc hình ảnh được xây dựng bằng kiến ​​trúc MVVM, cameraX version 1.4.1, GPUImage cho Android và Kotlin.
Dựa trên generator camera_api.dart được tạo bởi plugin pigeon để tạo các method channel cho phép Flutter gọi đến các method của plugin.

# Các thành phần CameraX được sử dụng:

- ProcessCameraProvider để quản lý vòng đời máy ảnh
- Trường hợp sử dụng Preview để xem trước máy ảnh
- Trường hợp sử dụng ImageCapture để chụp ảnh
- Trường hợp sử dụng ImageAnalysis để phát hiện khuôn mặt
- Trường hợp sử dụng VideoCapture để quay video
- Các tính năng chính của CameraX được triển khai:
- Quản lý vòng đời máy ảnh với LifecycleOwner
- Xem trước máy ảnh với surface provider
- Chụp ảnh với cài đặt chất lượng
- Quay video với cài đặt chất lượng
- Điều khiển máy ảnh (thu phóng, flash, lấy nét)
- Chuyển đổi máy ảnh (trước/sau)
- Xử lý độ phân giải và tỷ lệ khung hình

# Kiến trúc:

- Sử dụng mẫu MVVM với CameraViewModel
- CameraRepository xử lý các hoạt động của CameraX
- CameraView để hiển thị bản xem trước
- Tích hợp với GPUImage để lọc

# Việc triển khai tuân theo các thông lệ tốt nhất của CameraX, bao gồm:

- Quản lý vòng đời phù hợp
- Xử lý lỗi
- Cấu hình trường hợp sử dụng
- Quản lý bề mặt
- Lựa chọn độ phân giải
- Cấu hình bản xem trước
