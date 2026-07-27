# Khuyến nghị model miễn phí tạo cả nhạc và lời

## Chọn ACE-Step 1.5

ACE-Step 1.5 là lựa chọn phù hợp nhất cho đồ án:

- Mã nguồn/model mở và chạy local, không cần trả phí API theo lượt.
- Có thể tạo bài hoàn chỉnh gồm nhạc cụ, giọng hát và cấu trúc bài.
- Nhận `caption`, `lyrics`, ngôn ngữ giọng hát và cờ `instrumental`.
- Hỗ trợ tiếng Việt và nhiều ngôn ngữ.
- Có thể chạy trên GPU phổ thông bằng chế độ nhẹ; Google Colab miễn phí có thể dùng khi được cấp GPU.

“Miễn phí model” không đồng nghĩa Google Colab đảm bảo GPU miễn phí liên tục. Colab có thể giới hạn
phiên, ngắt kết nối hoặc không cấp GPU. Vì đây là đồ án demo, nên giữ sẵn vài file audio mẫu để dự
phòng khi Colab không có GPU.

## Cách ghép với dự án

Không cần thay Spring Boot:

1. Thay model trong notebook/FastAPI Colab hiện tại bằng ACE-Step 1.5.
2. Mở rộng form tạo nhạc thêm ô `lyrics` và lựa chọn “AI tự viết lời”.
3. Gửi từ Spring Boot sang Colab: `caption`, `lyrics`, `instrumental`, `vocal_language=vi`,
   `duration`.
4. Colab trả file WAV/MP3 và phần lời cuối cùng.
5. Spring Boot tiếp tục lưu file vào `uploads/audio`, chỉ lưu URL và lyrics trong SQL Server.

Nên triển khai hai chế độ:

- **Tôi nhập lời**: kết quả dễ kiểm soát, dễ trình bày.
- **AI gợi ý lời**: dùng songwriter component của ACE-Step rồi cho người dùng xem/sửa trước khi tạo audio.

Không chọn dịch vụ thương mại yêu cầu API trả phí cho phiên bản đầu. YuE cũng tạo full-song từ lời,
nhưng giấy phép model có giới hạn phi thương mại và quy trình chạy nặng/phức tạp hơn, nên không phù
hợp bằng ACE-Step 1.5 cho mục tiêu đơn giản, dễ demo.
