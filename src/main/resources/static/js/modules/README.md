# JavaScript modules

`main.js` chỉ giữ phần khởi tạo Vue, dữ liệu dùng chung, computed, watcher và vòng đời trang.
Các hàm xử lý được tách vào các file dưới đây và được ghép lại khi trang tải.

| File | Phạm vi chỉnh sửa |
| --- | --- |
| `wizard.js` | Wizard tạo nhạc theo từng bước và dữ liệu brief. |
| `navigation.js` | Điều hướng, khám phá, trang chi tiết và các thao tác hiển thị chung. |
| `library.js` | Thư viện, yêu thích, playlist, album và bộ sưu tập công khai. |
| `music.js` | Tạo nhạc, theo dõi tiến trình, phát nhạc, thích nhạc và tag. |
| `account.js` | Đăng nhập, hồ sơ, gói sáng tạo và lịch sử thanh toán. |
| `social.js` | Bình luận, theo dõi, thông báo, bạn bè và thao tác xã hội. |
| `chat.js` | WebSocket chat, tin nhắn và chia sẻ bài hát. |

## Quy ước

1. Sửa hàm ở đúng module có phạm vi liên quan.
2. Các hàm trong module là `methods` của cùng một Vue app, vì vậy vẫn dùng `this.tenHam()` và `this.duLieu` như trước.
3. Không tạo thêm `new Vue(...)` trong các file module.
4. Nếu thêm module mới, khai báo vào `window.MusicAIModules`, thêm thẻ script trước `main.js` trong `templates/layout.html`, rồi thêm module vào phần `methods` của `main.js`.
5. Sau khi sửa JavaScript, chạy `mvn test`; khi kiểm tra giao diện, khởi động lại Spring Boot và nhấn `Ctrl + F5` trên trình duyệt để nhận file JS mới.
