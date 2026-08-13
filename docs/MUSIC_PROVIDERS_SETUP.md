# Cấu hình 4 mô hình tạo nhạc

Ứng dụng có bốn lựa chọn trong Wizard. Không có API key nào được ghi trong mã nguồn.

| Mã | Mô hình | Cấu hình cần có | Có lời |
|---|---|---|---|
| `audiocraft` | AudioCraft trên Colab | `AUDIOCRAFT_API_URL` | Không |
| `ace-step` | ACE-Step worker Colab | `ACE_STEP_API_URL` | Có, nếu worker GPU đang chạy |
| `musicapi` | MusicAPI.ai | `MUSICAPI_API_KEY` | Có |
| `suno` | Suno API | `SUNO_API_KEY` | Có |

## Chạy demo chỉ với AudioCraft

```powershell
$env:AUDIOCRAFT_API_URL='https://your-colab-url/generate-music'
mvn spring-boot:run
```

## Bật đủ bốn lựa chọn

```powershell
$env:AUDIOCRAFT_API_URL='https://your-audiocraft-colab-url/generate-music'
$env:ACE_STEP_API_URL='https://your-ace-step-worker-url'
$env:MUSICAPI_API_KEY='your-musicapi-key'
$env:SUNO_API_KEY='your-suno-key'
mvn spring-boot:run
```

Không push PowerShell history, `application.properties` có key thật, hoặc ảnh chụp API key lên Git.

## SQL

Chỉ dùng một script: `database/setup-fresh.sql`. Script này đã bao gồm các cột cho 4 provider AI.
Vì đây là script tạo mới, khi cần áp dụng cấu trúc này nhóm hãy tạo lại database demo rồi chạy toàn bộ script.

## Hành vi khi chưa cấu hình

Người dùng vẫn thấy bốn model để hiểu khả năng. Nếu chọn model chưa có URL/key hoặc worker đang tắt, API trả về `503`; bài nhạc không được tạo và token không bị trừ.

## Giới hạn hiện tại

- AudioCraft được cố định nhạc không lời.
- ACE-Step cần worker API theo hợp đồng `GET /health`, `POST /generate`; link Gradio UI không phải API backend.
- MusicAPI.ai và Suno đang poll kết quả. Giai đoạn sau sẽ thay bằng callback/webhook để không chiếm luồng xử lý.
- Chưa áp dụng phân quyền theo gói. Giai đoạn này chỉ cho chọn model và kiểm thử kết nối.
