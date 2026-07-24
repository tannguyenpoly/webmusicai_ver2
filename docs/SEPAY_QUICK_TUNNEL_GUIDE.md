# Thiết lập SePay + Cloudflare Quick Tunnel để demo chuyển khoản thật

## 1. Mỗi thành phần có ý nghĩa gì?

- **Tài khoản ngân hàng cá nhân**: nơi nhận tiền thật.
- **SePay**: đọc biến động số dư và gửi giao dịch mới sang WebMusicAI.
- **Webhook**: địa chỉ HTTP để SePay chủ động báo cho WebMusicAI rằng đã có tiền vào.
- **Cloudflare Quick Tunnel**: tạo URL HTTPS công khai tạm thời trỏ về Spring Boot đang chạy trên máy.
- **API key webhook**: mật khẩu dùng chung giữa SePay và WebMusicAI, ngăn người lạ tự gọi webhook.
- **Mã `SPxxxxxxxxxx`**: mã riêng của đơn hàng, được đặt trong nội dung chuyển khoản để đối chiếu.

Luồng hoàn chỉnh:

1. WebMusicAI tạo đơn `PENDING`, mã `SP...`, đúng số tiền và mã VietQR.
2. Người dùng quét QR và chuyển tiền thật vào tài khoản cá nhân.
3. Ngân hàng báo biến động số dư cho SePay.
4. SePay gửi HTTP `POST` đến URL Quick Tunnel.
5. Cloudflare chuyển request về `localhost:8080/api/orders/sepay-ipn`.
6. WebMusicAI kiểm tra API key, tiền vào, mã đơn, số tiền và mã giao dịch chưa dùng.
7. Đơn chuyển sang `SUCCESS` và token được cộng đúng một lần.

## 2. Chuẩn bị một lần

1. Đăng ký/đăng nhập SePay.
2. Trong SePay, thêm tài khoản ngân hàng cá nhân sẽ nhận tiền và bật đồng bộ giao dịch tiền vào.
3. Cài `cloudflared` bản Windows 64-bit từ trang tải chính thức của Cloudflare.
4. Mở PowerShell mới và kiểm tra:

```powershell
cloudflared --version
```

Máy đang phát triển dự án hiện chưa cài `cloudflared`.

## 3. Mở ứng dụng đúng bằng Java 17

Mở PowerShell tại thư mục dự án:

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-17'
$env:Path="$env:JAVA_HOME\bin;$env:Path"

$env:SEPAY_BANK_CODE='MB'
$env:SEPAY_BANK_ACCOUNT='0123456789'
$env:SEPAY_BANK_ACCOUNT_NAME='NGUYEN VAN A'
$env:SEPAY_WEBHOOK_API_KEY='thay-bang-chuoi-ngau-nhien-dai-kho-doan'
$env:COOKIE_SECURE='true'

mvn spring-boot:run
```

Ý nghĩa:

- `SEPAY_BANK_CODE`: mã ngân hàng dùng để tạo VietQR, ví dụ `MB`, `VCB`, `ACB`.
- `SEPAY_BANK_ACCOUNT`: số tài khoản nhận tiền thật.
- `SEPAY_BANK_ACCOUNT_NAME`: tên chủ tài khoản hiển thị trên QR.
- `SEPAY_WEBHOOK_API_KEY`: phải giống hệt API key nhập trong cấu hình webhook của SePay.
- `COOKIE_SECURE=true`: chỉ gửi cookie đăng nhập qua HTTPS khi truy cập bằng URL tunnel.

Các biến này chỉ tồn tại trong cửa sổ PowerShell hiện tại; đóng cửa sổ là mất. Không ghi số tài
khoản, API key hoặc mật khẩu thật vào GitHub.

Kiểm tra ứng dụng:

```powershell
Invoke-WebRequest http://localhost:8080
```

Kết quả mong đợi là HTTP `200`.

## 4. Mở Quick Tunnel

Giữ cửa sổ Spring Boot đang chạy. Mở PowerShell thứ hai:

```powershell
cloudflared tunnel --url http://localhost:8080
```

Cloudflare in ra URL dạng:

```text
https://random-words.trycloudflare.com
```

Không đóng cửa sổ này trong lúc demo. Mỗi lần dừng/chạy lại, URL thường thay đổi.

## 5. Tạo webhook trong SePay

Trong SePay:

1. Vào **Webhooks**.
2. Chọn **Thêm webhook**.
3. Tên: `WebMusicAI Demo`.
4. Sự kiện: **Có tiền vào**.
5. URL:

```text
https://random-words.trycloudflare.com/api/orders/sepay-ipn
```

6. Chọn đúng tài khoản ngân hàng đã liên kết.
7. Có thể lọc tiền tố mã thanh toán là `SP`.
8. Phương thức xác thực: **API Key**.
9. API key: đúng giá trị của `SEPAY_WEBHOOK_API_KEY`.
10. Kiểu nội dung: JSON, sau đó lưu và bật webhook.

SePay sẽ gửi header:

```text
Authorization: Apikey <SEPAY_WEBHOOK_API_KEY>
```

Không nhập cả chữ `Apikey` vào biến môi trường; ứng dụng tự ghép phần này khi kiểm tra.

## 6. Kiểm tra trước khi chuyển tiền

1. Mở URL Quick Tunnel trên trình duyệt.
2. Đăng nhập WebMusicAI.
3. Vào **Nạp Token**.
4. Chọn SePay và tạo đơn.
5. Xác nhận giao diện có QR, số tiền và mã đơn bắt đầu bằng `SP`.
6. Trong SePay, dùng chức năng **Gửi thử** của webhook.
7. Xem nhật ký webhook: endpoint phải trả HTTP `200` và JSON `{"success": true}`.

Payload gửi thử không nên cộng token nếu mã đơn/số tiền không trùng đơn thật đang `PENDING`.

## 7. Demo chuyển khoản thật

1. Tạo một đơn mới ngay trước khi demo.
2. Quét đúng VietQR do ứng dụng hiển thị; không sửa nội dung `SP...`.
3. Chuyển đúng số tiền.
4. Chờ SePay gọi webhook.
5. Giao diện thăm dò trạng thái mỗi 3 giây; đơn chuyển `PENDING -> SUCCESS` và số token tăng.
6. Mở bảng `Payment_Logs` nếu cần chứng minh mã giao dịch ngân hàng được lưu duy nhất.

Không bấm/gọi webhook giả lập để biến đơn thành thành công trong phần trình bày “tiền thật”.

## 8. Khi không hoạt động

- `cloudflared` không chạy: URL công khai không truy cập được.
- Trả `401`: API key trong SePay khác `SEPAY_WEBHOOK_API_KEY`.
- Trả `503`: server chưa nhận biến `SEPAY_WEBHOOK_API_KEY`.
- Trả `400`: mã đơn không tồn tại, sai số tiền, đơn đã xử lý, hoặc mã giao dịch bị trùng.
- Webhook `200` nhưng đơn vẫn `PENDING`: payload không phải tiền vào hoặc nội dung không có mã `SP...`.
- Đăng nhập qua tunnel không giữ cookie: kiểm tra ứng dụng được khởi động với `COOKIE_SECURE=true`
  và người dùng đang mở URL `https://...trycloudflare.com`, không phải HTTP.
- URL tunnel đổi: sửa lại URL webhook trong SePay.

Quick Tunnel phù hợp demo và phát triển, không có cam kết uptime. Không cần tên miền, hosting hay
tài khoản Cloudflare, nhưng máy chạy Spring Boot và `cloudflared` phải bật, có Internet trong suốt buổi demo.
