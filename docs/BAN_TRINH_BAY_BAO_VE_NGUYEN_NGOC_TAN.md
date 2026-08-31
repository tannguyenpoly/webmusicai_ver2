# Bản trình bày bảo vệ — Nguyễn Ngọc Tân

> **Cách dùng:** Phần chữ in đậm là ý chính trên slide; phần còn lại là lời nói gợi ý. Không cần đọc y nguyên; hãy nói tự nhiên và thao tác trực tiếp ở phần demo.

## 1. Lời mở đầu và phần việc cá nhân (khoảng 45 giây)

"Em xin chào Hội đồng. Nhóm em thực hiện dự án **WebMusicAI — website sáng tác và chia sẻ nhạc có tích hợp AI**. Mục tiêu của hệ thống là cho phép người dùng tạo bài nhạc từ mô tả bằng ngôn ngữ tự nhiên, quản lý bài nhạc và tương tác trong một nền tảng web.

Em là **Nguyễn Ngọc Tân**. Phần việc chính của em là **tích hợp hệ thống sinh nhạc AI từ nhiều nhà cung cấp**, gồm ACE-Step Colab, AudioCraft, Suno và Music API. Em hoàn thiện luồng tạo nhạc, quản lý tác vụ sinh nhạc, phân tích nhạc tham chiếu và lịch sử phân tích. Ngoài ra em tham gia quản trị, thống kê/lọc dữ liệu, lịch sử nghe, tích hợp thanh toán SePay, OTP, chat, giao diện tạo nhạc, thư viện, hồ sơ và thanh toán.

Về phần hỗ trợ chung, em chuẩn hóa script cơ sở dữ liệu, cấu hình bảo mật và biến môi trường Google OAuth, xử lý xung đột, cập nhật Use Case, tài liệu hướng dẫn ACE-Step Colab, báo cáo Word và PowerPoint."

## 2. Giới thiệu chức năng em phụ trách (khoảng 40 giây)

"Chức năng trung tâm em trình bày là **tạo và quản lý nhạc AI**. Người dùng chọn nhà cung cấp AI, nhập prompt và tiêu đề, chọn có lời/không lời, ngôn ngữ và giọng hát. Hệ thống kiểm tra quyền theo gói, kiểm tra AI còn hoạt động, trừ token an toàn, tạo tác vụ và trả trạng thái cho giao diện.

Khi AI hoàn thành, hệ thống lưu file audio bên ngoài cơ sở dữ liệu, còn SQL Server lưu URL audio và thông tin bài hát như prompt, lyrics, ảnh bìa, model và trạng thái. Người dùng có thể phát, tải, sửa, đổi công khai/riêng tư, xóa hoặc remix bài nhạc."

## 3. Demo luồng tạo nhạc (khoảng 2–3 phút)

### Lời nói khi thao tác

"Bây giờ em sẽ demo luồng tạo nhạc. Tại trang **Tạo nhạc AI**, em chọn provider, nhập prompt, đặt tiêu đề, chọn chế độ có lời hoặc không lời và các tùy chọn giọng hát. Sau khi bấm **Tạo nhạc**, giao diện gửi request đến API của hệ thống.

Hệ thống không gọi AI ngay một cách thiếu kiểm soát. Trước hết, backend kiểm tra người dùng và gói dịch vụ; sau đó kiểm tra kết nối provider để tránh trừ token khi AI đang offline. Nếu hợp lệ, hệ thống khóa dữ liệu người dùng trong transaction, kiểm tra token, trừ đúng một token, tạo bài nhạc ở trạng thái `PENDING`, rồi đưa tác vụ vào hàng đợi xử lý bất đồng bộ.

Trong lúc chờ, giao diện định kỳ lấy trạng thái bài nhạc. Khi AI trả kết quả, hệ thống lưu audio, cập nhật bài sang `COMPLETED` và người dùng có thể nghe hoặc tải. Nếu AI lỗi, hàng đợi đầy, quá thời gian hoặc người dùng hủy, trạng thái chuyển sang `FAILED` hoặc `CANCELLED`, đồng thời hoàn lại token đúng một lần."

### Điểm cần chỉ trên màn hình

1. Trang **Tạo nhạc**: provider, prompt, tiêu đề, tùy chọn vocal/instrumental.
2. Bấm tạo và chỉ trạng thái `PENDING`/loading cùng token còn lại.
3. Khi hoàn tất: mở bài, phát audio, xem prompt/lyrics/provider.
4. Thao tác sửa tiêu đề hoặc đổi công khai/riêng tư; nếu đủ thời gian, thử remix.
5. Nếu demo được: bấm hủy một job đang chờ để chứng minh hoàn token.

## 4. Giải thích đường đi frontend → backend → AI (khoảng 1 phút)

"Về vị trí code và đường đi dữ liệu, nút **Tạo nhạc** được xử lý trong `static/js/modules/music.js`, hàm `generateMusic()`. Hàm này dùng Axios gửi `POST /api/songs/generate` cùng dữ liệu form.

Request đi vào `SongRestController`, phương thức `generateMusic()` tại endpoint `/api/songs/generate`. Controller xác thực dữ liệu và gọi `SongGenerationService` để tạo ticket/trừ token, sau đó `MusicJobService` chạy tác vụ nền.

Lớp `MusicGeneratorService` chọn nhà cung cấp thông qua `MusicProviderRegistry`. Mỗi provider như ACE-Step, AudioCraft, Suno hay Music API đều triển khai cùng một interface, nên có thể thay đổi hoặc thêm provider mà không làm thay đổi luồng nghiệp vụ chính. Khi có kết quả, `SongGenerationService.complete()` lưu file audio và cập nhật bài hát trong cơ sở dữ liệu."

**Sơ đồ nói ngắn:**

`Nút Tạo nhạc` → `music.js / Axios` → `POST /api/songs/generate` → `SongRestController` → `SongGenerationService` + `MusicJobService` → `MusicProviderRegistry` → `ACE-Step/AudioCraft/Suno/API` → `lưu audio + cập nhật Songs` → `giao diện kiểm tra trạng thái`.

## 5. Xử lý nghiệp vụ và tình huống phản biện (khoảng 1 phút)

"Điểm nghiệp vụ mà em chú ý nhất là token và xử lý đồng thời. Ví dụ, nếu người dùng chỉ còn một token nhưng gửi hai request cùng lúc, hệ thống dùng transaction và khóa dữ liệu người dùng để chỉ một request được trừ token thành công. Khi thất bại hoặc hủy, bản ghi giao dịch hoàn token giúp kiểm soát việc hoàn đúng một lần, không hoàn lặp.

Nếu Hội đồng yêu cầu đổi điều kiện nghiệp vụ, ví dụ từ lấy 5 kết quả cao nhất sang 10 kết quả cao nhất, em sẽ xác định endpoint và repository/service đang giới hạn `Pageable` hoặc `limit`, đổi tham số giới hạn từ 5 thành 10; sau đó kiểm tra lại giao diện hiển thị và test API. Em không chỉ sửa số trên giao diện vì giới hạn dữ liệu phải được đảm bảo ở backend."

## 6. Câu hỏi: đăng nhập thường và Google khác nhau thế nào?

"Đăng ký/đăng nhập thường dùng username hoặc email và password. Password được mã hóa bằng `PasswordEncoder`, không lưu dạng rõ. Khi đăng nhập thành công, backend tạo JWT và trả cho trình duyệt bằng cookie `HttpOnly`.

Đăng nhập Google dùng OAuth2. Người dùng được chuyển sang Google để xác thực; sau khi Google trả về email, tên và ảnh đại diện, `OAuthController` tìm người dùng theo email hoặc tạo tài khoản mới. Sau đó hệ thống của em cũng tạo JWT của chính ứng dụng và lưu ở cookie `HttpOnly`.

Hai cách **không lưu vào hai database hay hai bảng người dùng riêng**. Chúng cùng lưu trong bảng `Users`. Cột `authProvider` cho biết tài khoản là `LOCAL`, `GOOGLE` hoặc `BOTH`. Với tài khoản Google mới, hệ thống lưu một mật khẩu ngẫu nhiên đã mã hóa để đáp ứng ràng buộc dữ liệu; người dùng không dùng mật khẩu đó để đăng nhập. Nếu một tài khoản đã có cả hai cách đăng nhập thì `authProvider` là `BOTH`."

## 7. Câu hỏi: hệ thống có phiên làm việc không? Đổi còn 1 phút thế nào?

"Phần phiên đăng nhập của ứng dụng sử dụng **JWT stateless**, không tạo HTTP session phía server cho các request API thông thường. Spring Security được cấu hình `SessionCreationPolicy.STATELESS`. JWT có thời hạn hiện được cấu hình qua thuộc tính `jwt.expiration`, đang là `86400000` mili-giây, tức 24 giờ.

Để demo thời gian phiên chỉ còn 1 phút, em đổi cấu hình thành `jwt.expiration=60000`, khởi động lại ứng dụng và đăng nhập lại để nhận token mới. Sau 60 giây, token hết hạn; `JwtFilter` không xác thực request tiếp theo và người dùng phải đăng nhập lại. Ngoài thời hạn, token còn có `tokenVersion`; khi đổi mật khẩu hoặc khóa tài khoản, version thay đổi khiến token cũ bị vô hiệu ngay cả khi chưa hết hạn."

> Lưu ý khi demo: chỉ đặt `jwt.expiration=60000` trên máy demo hoặc profile demo; sau đó trả về cấu hình bình thường. Không đưa JWT secret, Google client secret hay khóa thanh toán lên slide/GitHub.

## 8. Kết thúc (20 giây)

"Tóm lại, phần việc của em tập trung vào việc biến chức năng tạo nhạc AI thành một luồng có thể vận hành: từ nhập yêu cầu, gọi nhiều provider, xử lý bất đồng bộ, quản lý trạng thái và token, đến lưu và quản lý kết quả. Các phần bổ sung như bảo mật OAuth/JWT, thanh toán, chat, quản trị và tài liệu giúp hệ thống hoàn chỉnh hơn. Em xin cảm ơn Hội đồng và sẵn sàng trả lời câu hỏi."

## 9. Bảng nhắc nhanh khi bị yêu cầu mở code

| Nội dung | File / vị trí cần mở | Ý cần nói |
| --- | --- | --- |
| Nút tạo nhạc gọi API | `src/main/resources/static/js/modules/music.js`, `generateMusic()` | Axios gửi `POST /api/songs/generate`. |
| Endpoint tạo nhạc | `src/main/java/com/fpoly/webmusicai/controller/SongRestController.java`, `generateMusic()` | Nhận request, kiểm tra provider/quyền, tạo ticket, chạy job nền. |
| Hủy và hoàn token | `SongRestController.java`, `cancelGeneration()`; `SongGenerationService.java`, `cancelAndRefund()` | Chuyển `CANCELLED`, hoàn một token và hủy worker. |
| Lưu kết quả AI | `SongGenerationService.java`, `complete()` | Lưu audio, lyrics, task ID/provider status rồi chuyển `COMPLETED`. |
| Chọn nhiều AI provider | `service/music/MusicProviderRegistry.java` | Registry chọn implementation theo mã provider. |
| Đăng nhập thường | `controller/AuthController.java` | Mã hóa mật khẩu, tạo JWT, ghi cookie HttpOnly. |
| Đăng nhập Google | `config/OAuthController.java` | Lấy email/profile từ Google, tìm/tạo cùng bảng Users, rồi tạo JWT. |
| Hết phiên JWT | `config/JwtService.java`, `application.properties` | `jwt.expiration`; đổi thành `60000` để demo 1 phút. |
| Kiểm tra token mỗi request | `config/JwtFilter.java` | Xác thực chữ ký, hạn dùng và `tokenVersion`. |
