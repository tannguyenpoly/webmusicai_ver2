# WebMusicAI - hướng dẫn chạy và trình bày đồ án

## 1. Kiến trúc dễ trình bày

Ứng dụng giữ đúng mô hình Spring Boot MVC/REST:

- Thymeleaf dựng layout, fragment và các trang HTML phía server.
- Vue 3 được nhúng trực tiếp để xử lý trạng thái tương tác; Axios gọi REST API.
- Spring Security + JWT xác thực.
- Controller nhận/trả HTTP; Service chứa nghiệp vụ; Repository làm việc với JPA.
- SQL Server lưu dữ liệu quan hệ. File nhạc được lưu ngoài database.
- STOMP/WebSocket dùng cho chat thời gian thực.

Không nên viết lại toàn bộ thành một frontend framework riêng trước buổi bảo vệ. Cách
phân vai hiện tại đủ rõ: Thymeleaf là khung trang, Vue là phần tương tác. Việc nên làm
sau đồ án là tách `main.js` thành các module nhỏ, không phải đổi công nghệ.

## 2. Đối chiếu nội dung Java 5 và Java 6

| Nội dung lab | Thành phần trong đồ án |
|---|---|
| Java 5 Lab 1-2: Spring, Controller, request mapping | các lớp trong `controller/`, `WebController` |
| Java 5 Lab 3-4: Thymeleaf, biểu thức, form, validation, upload | `templates/`, DTO validation, upload avatar/cover |
| Java 5 Lab 5: Spring Bean, Service, DI, cookie/session | các lớp `service/`, constructor/autowired injection, JWT cookie |
| Java 5 Lab 6-7: JPA Repository, query, paging | `entity/`, `repository/`, `Pageable` |
| Java 5 Lab 8: mail, schedule, interceptor | `MailService`, `DatabaseCleanupTask`, `JwtFilter` |
| Java 6 Lab 1-2: authentication, authorization, JDBC user | `SecurityConfig`, `CustomUserDetailsService`, role USER/ADMIN |
| Java 6 Lab 3: Google OAuth2 | `OAuthController` |
| Java 6 Lab 4-5: REST API, Ajax/consumer, Postman | REST controllers, Axios, Postman collection |
| Java 6 Lab 6: JWT | `JwtService`, `JwtFilter`, `JwtCookieService` |
| Java 6 Lab 7: VueJS + Axios | `static/js/main.js` |
| Java 6 Lab 8: WebSocket/STOMP | chat và presence |

Các phần AI, thanh toán, lưu file và khóa giao dịch là phần mở rộng; chúng không thay
thế các nội dung môn học ở trên.

## 3. Chuẩn bị SQL Server

Database mới:

1. Chạy `database/setup-fresh.sql`.
2. Chạy `database/demo-data.sql` nếu cần thêm tài khoản và nhạc nghe thử.

Database đang có dữ liệu:

1. Sao lưu database.
2. Chạy duy nhất `database/upgrade-existing.sql`.
3. Chạy `database/demo-data.sql` nếu cần thêm tài khoản và nhạc nghe thử.

`setup-fresh.sql` và `upgrade-existing.sql` là hai lựa chọn khác nhau, không chạy nối tiếp.
Xem `database/README.md` để chọn đúng file.

Chạy migration trong SSMS bằng tài khoản có quyền `db_owner` hoặc quyền `ALTER`
trên database. Tài khoản ứng dụng `dtbmusic` chỉ cần quyền đọc/ghi dữ liệu và trên
máy demo hiện không có quyền đổi cấu trúc bảng, vì vậy không dùng tài khoản này để
chạy migration.

Tài khoản được tạo bởi `demo-data.sql` là `demo_lofi`, `demo_rock`, `demo_piano`;
mật khẩu chung là `123456`.

## 4. Biến môi trường

Ví dụ PowerShell cho máy demo:

```powershell
$env:DB_USERNAME='dtbmusic'
$env:DB_PASSWORD='mat-khau-sql-server'
$env:JWT_SECRET='chuoi-ngau-nhien-toi-thieu-32-ky-tu'
$env:GOOGLE_CLIENT_ID='google-client-id'
$env:GOOGLE_CLIENT_SECRET='google-client-secret'
$env:COLAB_MUSIC_API_URL='https://dia-chi-colab/generate-music'
$env:MAIL_USERNAME='tai-khoan-gmail'
$env:MAIL_PASSWORD='gmail-app-password'
mvn spring-boot:run
```

Để gửi được email, `MAIL_USERNAME` phải là địa chỉ Gmail và `MAIL_PASSWORD` phải là
App Password 16 ký tự của Google, không phải mật khẩu Gmail thường. Tài khoản Google
cần bật xác minh hai bước trước khi tạo App Password. Nếu để hai biến này trống,
tài khoản vẫn được tạo và thanh toán vẫn thành công nhưng máy chủ không thể gửi thư;
xem Console để thấy dòng `Lỗi gửi email`.

Email chào mừng được gửi cho cả đăng ký thường và tài khoản Google mới. Email chúc
mừng thanh toán được gửi sau khi SePay/VNPay đã được xác thực và cộng token thành
công. Tài khoản Google cũ sẽ được điền lại email, tên và ảnh ở lần đăng nhập tiếp theo.

Dữ liệu tài khoản nằm trong SQL Server nên vẫn còn sau khi tắt máy hoặc khởi động lại
Spring Boot. Nếu để `JWT_SECRET` trống thì chỉ phiên đăng nhập/cookie cũ hết hiệu lực
sau khi khởi động lại; tài khoản không bị xóa.

`JWT_SECRET` có thể bỏ trống khi chạy local: ứng dụng tự tạo khóa ngẫu nhiên lúc
khởi động. Khi đó mọi phiên đăng nhập sẽ hết hiệu lực sau khi restart. Đây là hành vi
phù hợp cho demo nhưng không phù hợp khi triển khai lâu dài.

Không đưa password, JWT secret, Google secret, VNPay secret hoặc số tài khoản thật
lên GitHub.

## 5. Demo SePay bằng tài khoản cá nhân, không cần mua tên miền

Có thể nhận chuyển khoản thật mà không mua tên miền/hosting. Điều kiện:

- Tài khoản ngân hàng đã được liên kết để SePay đọc biến động số dư.
- Spring Boot đang chạy.
- Webhook của SePay gọi được một URL HTTPS công khai.

Thiết lập:

```powershell
$env:SEPAY_BANK_CODE='MB'
$env:SEPAY_BANK_ACCOUNT='so-tai-khoan-ca-nhan'
$env:SEPAY_BANK_ACCOUNT_NAME='TEN CHU TAI KHOAN'
$env:SEPAY_WEBHOOK_API_KEY='mot-api-key-kho-doan'
$env:COOKIE_SECURE='true'
mvn spring-boot:run
```

Ở cửa sổ khác:

```powershell
cloudflared tunnel --url http://localhost:8080
```

Lấy URL `https://...trycloudflare.com` và cấu hình webhook:

```text
POST https://...trycloudflare.com/api/orders/sepay-ipn
Authorization: Apikey <giá trị SEPAY_WEBHOOK_API_KEY>
```

Quick Tunnel chỉ dùng để demo. URL thay đổi khi chạy lại, vì vậy phải cập nhật
webhook. Hệ thống đối chiếu mã đơn `SP...`, đúng số tiền và mã giao dịch duy nhất
trước khi cộng token. Không dùng nút giả lập thanh toán thành công khi trình bày
luồng tiền thật.

## 6. JWT và trạng thái online

- Trình duyệt nhận JWT qua cookie `HttpOnly`, JavaScript không đọc được token.
- Cookie dùng `SameSite=Lax`; khi chạy HTTPS đặt `COOKIE_SECURE=true`.
- Postman vẫn có thể lấy `token` từ response đăng nhập và gửi Bearer Token.
- Token chứa `tokenVersion`. Đổi mật khẩu hoặc khóa tài khoản làm token cũ mất hiệu lực.
- Không truyền JWT trong URL OAuth.
- Online được xác định bởi WebSocket hoặc heartbeat gần nhất, không phải chỉ vì
  user từng đăng nhập. Logout chuyển offline ngay; `last_seen_at` dùng để hiển thị
  thời điểm hoạt động cuối.

Presence hiện lưu trạng thái online trong RAM nên phù hợp một instance demo. Nếu chạy
nhiều server cần chuyển heartbeat sang Redis.

## 7. Sinh nhạc và lưu audio

Quy trình tạo nhạc:

1. Khóa dòng user trong transaction.
2. Kiểm tra và trừ đúng 1 token.
3. Tạo bản ghi song `PENDING`.
4. Đưa tác vụ AI vào thread pool có giới hạn.
5. Thành công: lưu file, cập nhật `COMPLETED`.
6. Thất bại/quá 30 phút: khóa song, chuyển `FAILED`, hoàn token đúng một lần.
7. Người dùng có thể dừng job: chuyển `CANCELLED`, hoàn token đúng một lần và bỏ qua kết quả AI trả về trễ.

Cách này ngăn hai request đồng thời cùng tiêu số token cuối và ngăn callback lỗi hoàn
token nhiều lần.

Audio mới được lưu ở `./uploads/audio`; SQL Server chỉ lưu URL ngắn. Có thể đổi thư
mục bằng `AUDIO_STORAGE_LOCATION`. Đây là phương án nhanh và nhẹ nhất cho một máy
demo. Khi triển khai thật, thay phần lưu file bằng S3, Cloudflare R2 hoặc MinIO và
dùng CDN; API và bảng `Songs` không phải đổi.

Không lưu WAV Base64 trong SQL Server vì kích thước tăng khoảng 33%, response JSON
nặng, tốn RAM và backup database chậm.

## 8. Cleanup hằng đêm

`DatabaseCleanupTask` làm hai việc:

- Mỗi 10 phút: tìm job nhạc `PENDING` quá 30 phút, chuyển `FAILED` và hoàn token;
  đồng thời đóng đơn thanh toán `PENDING` quá 15 phút thành `EXPIRED`.
- 02:00 hằng ngày: xóa job `FAILED` quá 7 ngày và file audio liên quan nếu có.

Có thể đổi lịch bằng:

```properties
music.cleanup.recover-cron=0 */10 * * * *
music.cleanup.delete-cron=0 0 2 * * *
```

Không nên xóa bài `COMPLETED`, đơn hàng hoặc transaction trong cleanup tự động.

## 9. Playlist, chat và kết bạn

- Playlist mới mặc định là riêng tư.
- Chủ playlist có thể đổi Công khai/Riêng tư ở hộp thoại playlist.
- Người khác chỉ đọc được playlist công khai.
- Chỉ chủ sở hữu được sửa, xóa hoặc thêm bài.
- Chat là mức demo: có xác thực user, DTO giới hạn 500 ký tự, lịch sử và căn trái/phải
  theo username chuẩn hóa. Chưa có mã hóa đầu-cuối, chặn người dùng, gửi file hoặc
  kiểm duyệt nội dung.
- Kết bạn hỗ trợ gửi lời mời, chấp nhận, hủy lời mời/hủy kết bạn.
- Follower nhận thông báo khi tác giả phát hành bài đã hoàn thành và đang công khai.

## 10. AI có thể tạo cả nhạc và lời

Khuyến nghị ACE-Step 1.5 vì đây là model mở, có thể chạy local/Colab và tạo bài hoàn chỉnh gồm
nhạc, vocal, cấu trúc và lời. Backend đã sẵn trường `lyrics` và lựa chọn `instrumental`; chỉ cần
thay model trong API Colab và bổ sung trường lyrics trên request/form. Xem
`docs/AI_MODEL_RECOMMENDATION.md`.

## 11. Kịch bản bảo vệ đề xuất

1. Đăng nhập thường và Google.
2. Dùng Postman chứng minh JWT, phân quyền USER/ADMIN.
3. Tạo bài nhạc và cho xem trạng thái `PENDING -> COMPLETED`.
4. Mở hai trình duyệt để chứng minh token không bị trừ âm khi gửi đồng thời.
5. Tạo playlist riêng tư, mở user khác bị 403, sau đó đổi công khai.
6. Gửi lời mời kết bạn, chat hai chiều, logout và xem thời gian offline.
7. Quét VietQR, chuyển khoản đúng nội dung và theo dõi đơn tự chuyển `SUCCESS`.
