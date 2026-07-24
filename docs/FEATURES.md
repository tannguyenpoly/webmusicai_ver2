# Danh sách chức năng WebMusicAI

## Người dùng và bảo mật

- Đăng ký, đăng nhập, đăng xuất.
- Đăng nhập Google OAuth2.
- Quên/đặt lại mật khẩu qua OTP email.
- JWT dùng được bằng cookie HttpOnly trên trình duyệt và Bearer Token trong Postman.
- Vai trò USER/ADMIN; đổi mật khẩu làm token cũ mất hiệu lực.
- Cập nhật hồ sơ, ảnh đại diện, tìm người dùng.

## Nhạc AI

- Nhập prompt, tiêu đề, chọn nhạc không lời và gửi job sang API AI ở Colab.
- Trừ một token, theo dõi `PENDING -> COMPLETED/FAILED/CANCELLED`.
- Dừng job đang chờ/chạy và hoàn đúng một token.
- Chống trừ âm token và chống hoàn token lặp bằng khóa giao dịch.
- Remix từ một bài có sẵn.
- Lưu audio thành file ngoài SQL Server; phát, tải về, đổi ảnh bìa.
- Đổi tiêu đề, prompt, công khai/riêng tư, xóa bài.
- Like/unlike, bình luận, trả lời/sửa/xóa bình luận, đếm lượt nghe.
- Lọc/sắp xếp thư viện và khám phá nhạc công khai.

## Mạng xã hội

- Follow/unfollow và đếm follower/following.
- Người theo dõi nhận thông báo khi tác giả phát hành bài công khai.
- Danh sách thông báo, số chưa đọc, đọc một/đọc tất cả.
- Gửi/chấp nhận/hủy lời mời kết bạn và xem danh sách bạn bè.
- Trạng thái online bằng WebSocket/heartbeat, hiển thị thời gian hoạt động gần nhất.
- Chat riêng thời gian thực qua STOMP/WebSocket, lịch sử chat, chưa đọc và chia sẻ bài qua chat.

## Playlist, album và phân loại

- Playlist mặc định riêng tư; chủ sở hữu đổi công khai/riêng tư.
- Thêm/xóa bài trong playlist; người khác chỉ xem playlist công khai.
- CRUD album và thêm/xóa bài trong album.
- Thể loại và gắn thể loại cho bài hát.
- Danh sách phát tạm lưu trong sessionStorage.

## Thanh toán

- Danh sách gói token.
- Tạo đơn VNPAY sandbox hoặc SePay/VietQR.
- SePay nhận chuyển khoản thật qua webhook HTTPS.
- Kiểm tra API key, mã đơn, đúng số tiền và mã giao dịch duy nhất trước khi cộng token.
- Khóa giao diện QR trong lúc chờ; người dùng có nút hủy rõ ràng.
- Đơn chờ quá 15 phút tự hết hạn; lịch sử người dùng chỉ hiển thị giao dịch thành công.
- Cộng đúng token, nâng cấp gói có thời hạn và gửi email chúc mừng sau thanh toán.

## Quản trị và báo cáo

- Quản lý người dùng, bài hát, thể loại, gói token và đơn hàng.
- Thống kê tổng quan, doanh thu, bài được thích nhiều, người dùng nổi bật và tăng trưởng.

## Vận hành và kiểm thử

- Dữ liệu mẫu, audio mẫu và script migration/repair SQL Server.
- Postman collection kiểm thử các API chính và các trường hợp bị từ chối.
- Job cleanup phục hồi tác vụ tạo nhạc quá hạn và xóa bản ghi lỗi cũ.
