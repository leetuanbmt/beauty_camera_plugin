Bạn là một chuyên gia Flutter và xử lý video trên Android.  
Nhiệm vụ: Đề xuất giải pháp xây dựng một ứng dụng chụp ảnh/video giống TikTok hoặc CapCut, tập trung cho Android trước.

Yêu cầu:

1. Kiến trúc hệ thống:

   - Cách tách UI bằng Flutter và native Android module để xử lý camera + video.
   - Cách dùng Platform Channel hoặc TextureRegistry để truyền video stream sang Flutter UI.

2. Giải pháp kỹ thuật Android:

   - Camera: so sánh CameraX vs Camera2 API, chọn giải pháp tối ưu.
   - Video pipeline: MediaCodec/MediaRecorder để encode, decode.
   - Real-time filter: OpenGL ES hoặc GPUImage cho filter.
   - Audio sync: cách record âm thanh đồng bộ với video.

3. Tích hợp với Flutter:

   - Viết plugin Flutter (MethodChannel hoặc Pigeon).
   - Render preview bằng Texture widget (tránh copy bytes).
   - Truyền event từ native sang Flutter để sync UI.

4. Hiệu năng:

   - Cách xử lý frame nhanh mà không cần copy dữ liệu qua Dart.
   - Sử dụng SurfaceTexture / OpenGL pipeline.
   - Tối ưu trên thiết bị yếu (giảm resolution, bitrate adapt).

5. Roadmap phát triển:

   - Giai đoạn 1 (MVP): Camera preview + record video + lưu file.
   - Giai đoạn 2: Thêm filter real-time.
   - Giai đoạn 3: Editor cơ bản (cắt video, chèn nhạc).
   - Giai đoạn 4: Export chất lượng cao, chia sẻ mạng xã hội.

6. Ví dụ minh hoạ:
   - Đưa ra snippet code plugin Android cho CameraX + OpenGL.
   - Nếu có repo open-source tham khảo thì đề xuất.

Hãy trả lời chi tiết từng bước, giải thích ưu/nhược điểm của các công nghệ (CameraX, MediaCodec, OpenGL), và đưa lộ trình phát triển rõ ràng.
