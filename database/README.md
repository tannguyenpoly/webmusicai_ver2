# Bộ script SQL của WebMusicAI

Trong thư mục này chỉ có ba script cần quan tâm:

| File | Khi nào chạy | Có xóa dữ liệu không? |
|---|---|---|
| `setup-fresh.sql` | Cài database mới hoặc chủ động làm lại từ đầu | Có. Xóa rồi tạo lại `MusicAI_DB` |
| `upgrade-existing.sql` | Database đã có và cần giữ tài khoản, bài hát, đơn hàng | Không |
| `demo-data.sql` | Muốn bổ sung tài khoản và bài nhạc mẫu để trình diễn | Không |

## Máy của bạn hiện tại

Vì `MusicAI_DB` đã tồn tại và ứng dụng đang chạy được, chỉ cần:

1. Sao lưu `MusicAI_DB` nếu có dữ liệu quan trọng.
2. Chạy `upgrade-existing.sql` một lần bằng tài khoản có quyền `db_owner` hoặc `ALTER`.
3. Chạy `demo-data.sql` nếu muốn thêm dữ liệu mẫu.
4. Khởi động lại Spring Boot.

Không chạy `setup-fresh.sql`, vì file này sẽ xóa toàn bộ `MusicAI_DB` hiện tại.

## Máy thành viên cài mới

1. Chạy `setup-fresh.sql`.
2. Chạy `demo-data.sql` nếu cần thêm dữ liệu mẫu.
3. Cấu hình `application.properties` hoặc biến môi trường rồi chạy Spring Boot.

Không cần chạy `upgrade-existing.sql` sau `setup-fresh.sql`, vì script cài mới đã chứa cấu trúc mới nhất.
