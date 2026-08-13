# ACE-Step 1.5 nhẹ trên Colab: GPU nếu có, CPU khi hết hạn mức

Tài liệu này dùng để chạy ACE-Step 1.5 mà không bắt buộc Colab phải cấp GPU.
Nếu có GPU, chương trình tự dùng CUDA. Nếu không có GPU, chương trình chuyển sang CPU.

## Khả năng của cấu hình nhẹ

- Tạo nhạc không lời.
- Tạo nhạc có giọng hát từ lời người dùng nhập.
- Chọn bản thử 30 giây hoặc bản đầy đủ 180 giây.
- Gợi ý giọng nam hoặc nữ trong `Music Caption`.
- Chỉ tạo một kết quả mỗi lần (`Batch Size = 1`).
- Không dùng 5Hz LM để giảm RAM/VRAM và tránh lỗi hết bộ nhớ.

> CPU chỉ là phương án dự phòng. Nó vẫn có thể tạo nhạc nhưng chậm hơn GPU rất nhiều,
> nhất là bài 180 giây. Không đóng Colab hoặc dừng ô máy chủ khi đang tạo nhạc.

## Bước 1: tạo notebook mới

1. Mở Google Colab và tạo một notebook trống.
2. Nếu muốn thử GPU: chọn `Thời gian chạy` → `Thay đổi loại thời gian chạy` → `T4 GPU`.
3. Nếu Colab báo hết hạn mức GPU, chọn `CPU` và vẫn tiếp tục chạy hai ô bên dưới.
4. Không chạy đồng thời nhiều ô máy chủ.

## Ô 1 — cài đặt và lưu model vào Google Drive

Chỉ cần chạy lại ô này khi Colab tạo runtime mới. Model đã lưu trong Drive sẽ được dùng lại,
không phải tải lại toàn bộ.

```python
from google.colab import drive
from pathlib import Path
import os
import shutil
import subprocess
import sys

drive.mount('/content/drive')

repo = Path('/content/ACE-Step-1.5')
drive_root = Path('/content/drive/MyDrive/MusicAI_ACE_Step')
drive_checkpoints = drive_root / 'checkpoints'
uv_cache = drive_root / 'uv-cache'

drive_checkpoints.mkdir(parents=True, exist_ok=True)
uv_cache.mkdir(parents=True, exist_ok=True)
os.environ['UV_CACHE_DIR'] = str(uv_cache)
os.environ['ACESTEP_CHECKPOINTS_DIR'] = str(drive_checkpoints)

if not (repo / '.git').exists():
    if repo.exists():
        shutil.rmtree(repo)
    subprocess.run([
        'git', 'clone', '--depth', '1',
        'https://github.com/ace-step/ACE-Step-1.5.git',
        str(repo)
    ], check=True)
else:
    print('Mã nguồn đã có trong runtime, bỏ qua git clone.')

subprocess.run([
    sys.executable, '-m', 'pip', 'install', '-q', 'uv'
], check=True)

os.chdir(repo)
subprocess.run(['uv', 'sync'], check=True)

# Dùng thư mục model trên Drive thay cho thư mục tạm của Colab.
local_checkpoints = repo / 'checkpoints'
if local_checkpoints.is_symlink():
    local_checkpoints.unlink()
elif local_checkpoints.exists():
    for child in list(local_checkpoints.iterdir()):
        target = drive_checkpoints / child.name
        if not target.exists():
            shutil.move(str(child), str(target))
        elif child.is_dir():
            shutil.rmtree(child)
        else:
            child.unlink()
    shutil.rmtree(local_checkpoints)

local_checkpoints.symlink_to(drive_checkpoints, target_is_directory=True)

# Tải model lõi. Lệnh tự bỏ qua những tệp đã có trên Drive.
subprocess.run(['uv', 'run', 'acestep-download'], check=True)

gpu = subprocess.run(
    ['nvidia-smi', '--query-gpu=name,memory.total', '--format=csv,noheader'],
    capture_output=True,
    text=True
)

if gpu.returncode == 0:
    print('SẴN SÀNG — thiết bị:', gpu.stdout.strip())
else:
    print('SẴN SÀNG — không có GPU, Ô 2 sẽ chạy bằng CPU.')

print('Tiếp tục chạy Ô 2.')
```

Nếu ô này vừa cài hoặc cập nhật nhiều thư viện và Colab yêu cầu khởi động lại, chọn
`Thời gian chạy` → `Khởi động lại phiên`, sau đó chạy lại Ô 1. Khi thấy `SẴN SÀNG` mới chạy Ô 2.

## Ô 2 — khởi động ACE-Step nhẹ

Giữ ô này chạy trong toàn bộ thời gian tạo nhạc. Chương trình tự chọn CUDA hoặc CPU.

```python
%cd /content/ACE-Step-1.5

import os
import subprocess

gpu = subprocess.run(['nvidia-smi'], capture_output=True)
device = 'cuda' if gpu.returncode == 0 else 'cpu'

os.environ['MPLBACKEND'] = 'Agg'
os.environ['ACESTEP_CHECKPOINTS_DIR'] = '/content/drive/MyDrive/MusicAI_ACE_Step/checkpoints'
os.environ['ACESTEP_DEVICE'] = device
os.environ['ACESTEP_LM_DEVICE'] = device
os.environ['ACESTEP_CONFIG_PATH'] = 'acestep-v15-turbo'
os.environ['ACESTEP_INIT_LLM'] = 'false'
os.environ['ACESTEP_LM_BACKEND'] = 'pt'
os.environ['ACESTEP_API_WORKERS'] = '1'
os.environ['ACESTEP_QUEUE_WORKERS'] = '1'
os.environ['ACESTEP_DTYPE'] = 'float32' if device == 'cpu' else 'float16'

if device == 'cuda':
    os.environ['PYTORCH_CUDA_ALLOC_CONF'] = 'expandable_segments:True'
    os.environ['ACESTEP_OFFLOAD_TO_CPU'] = 'true'
    print('Đang chạy bằng GPU CUDA với CPU offload.')
else:
    os.environ['ACESTEP_OFFLOAD_TO_CPU'] = 'false'
    print('Đang chạy bằng CPU. Bài 180 giây sẽ mất nhiều thời gian.')

command = [
    'uv', 'run', 'acestep',
    '--share',
    '--backend', 'pt',
    '--init_service', 'true',
    '--init_llm', 'false',
    '--config_path', 'acestep-v15-turbo',
    '--batch_size', '1',
    '--enable-api'
]

subprocess.run(command, check=True)
```

Chờ đến khi xuất hiện:

```text
Running on public URL: https://...gradio.live
```

Mở URL đó để kiểm thử. URL thay đổi sau mỗi lần khởi động lại runtime.

## Cấu hình tạo nhạc 30 giây có lời

Trong giao diện ACE-Step:

```text
Generation Mode: Custom
Model: acestep-v15-turbo
Instrumental: bỏ chọn
Audio Duration: 30
Batch Size: 1
Thinking: bỏ chọn
AutoGen: bỏ chọn
Inference Steps: 4 trước; nếu kết quả kém thì tăng lên 8
Vocal Language: vi hoặc auto
```

`Music Caption` mẫu giọng nữ:

```text
Vietnamese acoustic pop, clear young female vocal, vocals begin immediately,
warm guitar, gentle piano, short intro, catchy chorus
```

`Lyrics` nên ngắn để giọng hát xuất hiện ngay trong bản 30 giây:

```text
[Verse]
Ngày mới lên qua ô cửa nhỏ
Mang theo hy vọng trong tim

[Chorus]
Mình cùng đi qua bao giông gió
Giữ mãi thanh âm bình yên
```

Muốn giọng nam, đổi `female vocal` thành `male vocal`. Đây là hướng dẫn theo mô tả nên model
có thể không tuân thủ giới tính giọng hát tuyệt đối trong mọi lần tạo.

## Cấu hình bản đầy đủ 180 giây có lời

Giữ nguyên cấu hình trên và đổi:

```text
Audio Duration: 180
Inference Steps: 8
Batch Size: 1
```

Lời nên có cấu trúc:

```text
[Intro]

[Verse 1]
...

[Pre-Chorus]
...

[Chorus]
...

[Verse 2]
...

[Chorus]
...

[Bridge]
...

[Final Chorus]
...

[Outro]
```

Không dùng lời quá ngắn cho bài 180 giây; nếu không, model có thể lặp lời hoặc tạo nhiều đoạn nhạc không hát.

## Tạo nhạc không lời

```text
Instrumental: chọn
Lyrics: [Instrumental]
Audio Duration: 30 hoặc 180
Batch Size: 1
Thinking: bỏ chọn
```

## Ba lựa chọn lời của Wizard MusicAI

1. **Không lời:** gửi `instrumental=true` và `lyrics=[Instrumental]`.
2. **Người dùng nhập lời:** gửi nguyên nội dung có các thẻ `[Verse]`, `[Chorus]` sang ACE-Step.
3. **AI gợi ý lời:** Spring Boot hoặc một dịch vụ viết lời nhẹ tạo lời trước, sau đó mới gửi sang
   ACE-Step. Cấu hình này tắt 5Hz LM nên ACE-Step không tự viết lời.

## Khi CPU quá chậm hoặc hết RAM

1. Chỉ thử 30 giây trước.
2. Giữ `Batch Size = 1`.
3. Giữ `Thinking`, `AutoGen` và 5Hz LM ở trạng thái tắt.
4. Đóng các tab hoặc ứng dụng không cần thiết.
5. Không gửi yêu cầu thứ hai khi yêu cầu trước chưa hoàn thành.
6. Nếu 180 giây không hoàn thành, dùng máy thành viên có GPU NVIDIA và giữ đúng cấu hình nhẹ trên.

CPU fallback giúp hệ thống vẫn có thể chạy mà không thuê GPU, nhưng không bảo đảm đủ nhanh để tạo
bài 180 giây trực tiếp trong 15 phút bảo vệ. Nhóm vẫn nên chuẩn bị một số file MP3 đã tạo thành công.
