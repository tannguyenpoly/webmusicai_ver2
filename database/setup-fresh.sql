/*
  WEBMUSICAI - CÀI ĐẶT DATABASE MỚI

  CẢNH BÁO: Script này XÓA toàn bộ MusicAI_DB hiện có rồi tạo lại từ đầu.
  Chỉ chạy khi cài mới hoặc khi chắc chắn không cần giữ dữ liệu cũ.
  Nếu database đang có dữ liệu cần giữ, hãy chạy upgrade-existing.sql thay thế.
  Dữ liệu mẫu chỉ gồm tài khoản người dùng thật; hệ thống không có tài khoản khách.
*/

-- =============================================
-- 0. XÓA DATABASE CŨ (NẾU CÓ) ĐỂ LÀM LẠI TỪ ĐẦU
-- =============================================
USE master;
GO

IF EXISTS (SELECT name FROM sys.databases WHERE name = N'MusicAI_DB')
BEGIN
    -- Ép ngắt tất cả các kết nối đang dùng database này để tránh lỗi khi Drop
    ALTER DATABASE MusicAI_DB SET SINGLE_USER WITH ROLLBACK IMMEDIATE;
    -- Xóa database
    DROP DATABASE MusicAI_DB;
END
GO

-- =============================================
-- 1. TẠO DATABASE MỚI
-- =============================================
CREATE DATABASE MusicAI_DB;
GO

-- =============================================
-- 2. TẠO LOGIN TẠI MASTER (TÀI KHOẢN HỆ THỐNG)
-- =============================================
USE master;
GO

-- Kiểm tra nếu chưa có Login thì mới tạo
IF SUSER_ID('dtbmusic') IS NULL
BEGIN
    EXEC('CREATE LOGIN dtbmusic WITH PASSWORD = ''123456''');
END
GO

-- =============================================
-- 3. CHUYỂN SANG DATABASE VỪA TẠO: TẠO USER & PHÂN QUYỀN
-- =============================================
USE MusicAI_DB;
GO

CREATE USER dtbmusic FOR LOGIN dtbmusic;
GO

GRANT CONNECT TO dtbmusic;
ALTER ROLE db_datareader ADD MEMBER dtbmusic;
ALTER ROLE db_datawriter ADD MEMBER dtbmusic;
GO

-- =============================================
-- 4. TẠO CÁC BẢNG (TABLES)
-- =============================================

-- [1] Users 
CREATE TABLE Users (
    username VARCHAR(50) PRIMARY KEY,
    password VARCHAR(100) NOT NULL,
    fullname NVARCHAR(100) NOT NULL,
    email VARCHAR(100) NULL,
    photo VARCHAR(255) NULL,
    token_balance INT DEFAULT 0,
    enabled BIT DEFAULT 1,
    account_tier VARCHAR(20) DEFAULT 'FREE',
    pro_expired_at DATETIME NULL,
    token_version INT NOT NULL DEFAULT 0,
    last_seen_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT GETDATE(),
    auth_provider VARCHAR(20) NOT NULL DEFAULT 'LOCAL'
);
GO

-- Tối ưu lọc Admin theo ngày đăng ký, gói và số dư token.
CREATE INDEX IX_Users_AdminFilter ON Users(created_at, account_tier, token_balance);
GO

-- [2] Roles
CREATE TABLE Roles (
    id VARCHAR(20) PRIMARY KEY,
    name NVARCHAR(50) NOT NULL
);
GO

-- [3] Authorities
CREATE TABLE Authorities (
    id INT IDENTITY(1,1) PRIMARY KEY,
    username VARCHAR(50) NOT NULL,
    role_id VARCHAR(20) NOT NULL,
    FOREIGN KEY (username) REFERENCES Users(username),
    FOREIGN KEY (role_id) REFERENCES Roles(id),
    CONSTRAINT UQ_Authorities_User_Role UNIQUE (username, role_id)
);
GO

-- [4] Songs (Đã chuyển audio_url thành VARCHAR(MAX) và thêm listen_count)
CREATE TABLE Songs (
    id INT IDENTITY(1,1) PRIMARY KEY,
    title NVARCHAR(255) NOT NULL,
    prompt NVARCHAR(MAX) NOT NULL,
    audio_url VARCHAR(MAX) NULL, 
    status VARCHAR(20) NOT NULL,
    is_public BIT DEFAULT 0,
    lyrics NVARCHAR(MAX) NULL,
    model_ver VARCHAR(20) NULL,
    generation_provider VARCHAR(30) NULL,
    provider_task_id VARCHAR(120) NULL,
    provider_status VARCHAR(30) NULL,
    generation_duration_seconds INT NULL,
    vocal_mode VARCHAR(30) NULL,
    vocal_language NVARCHAR(30) NULL,
    vocal_gender VARCHAR(10) NULL,
    is_remix BIT DEFAULT 0,
    parent_id INT NULL,
    cover_url VARCHAR(500) NULL,
    listen_count INT NOT NULL DEFAULT 0,
    created_at DATETIME DEFAULT GETDATE(),
    username VARCHAR(50) NOT NULL,
    FOREIGN KEY (username) REFERENCES Users(username),
    FOREIGN KEY (parent_id) REFERENCES Songs(id)
);
GO

-- Provider AI: dùng để lọc/lịch sử tác vụ sau này; không cần script SQL thứ hai.
CREATE INDEX IX_Songs_GenerationProviderStatus
ON Songs(generation_provider, provider_status, created_at DESC);
GO

-- [5] Transactions
CREATE TABLE Transactions (
    id INT IDENTITY(1,1) PRIMARY KEY,
    username VARCHAR(50) NOT NULL,
    amount INT NOT NULL,
    description NVARCHAR(255) NULL,
    created_at DATETIME DEFAULT GETDATE(),
    FOREIGN KEY (username) REFERENCES Users(username)
);
GO

-- [6] Packages 
CREATE TABLE Packages (
    id INT IDENTITY(1,1) PRIMARY KEY,
    name NVARCHAR(100) NOT NULL,
    tokens INT NOT NULL,
    price INT NOT NULL,
    old_price INT NULL,
    badge NVARCHAR(50) NULL,
    description NVARCHAR(255) NULL,
    tier_code VARCHAR(20) NOT NULL DEFAULT 'CREATOR',
    duration_days INT NOT NULL DEFAULT 30
);
GO

-- [7] Orders
CREATE TABLE Orders (
    id INT IDENTITY(1,1) PRIMARY KEY,
    order_code VARCHAR(50) UNIQUE NOT NULL,
    total_price INT NOT NULL,
    status VARCHAR(20) NOT NULL,
    payment_method VARCHAR(20) NOT NULL DEFAULT 'VNPAY',
    created_at DATETIME DEFAULT GETDATE(),
    username VARCHAR(50) NOT NULL,
    package_id INT NOT NULL,
    FOREIGN KEY (username) REFERENCES Users(username),
    FOREIGN KEY (package_id) REFERENCES Packages(id)
);
GO

-- [8] Tags and Song_Tags
CREATE TABLE Tags (
    id INT IDENTITY(1,1) PRIMARY KEY,
    name NVARCHAR(50) NOT NULL UNIQUE
);
GO

CREATE TABLE Song_Tags (
    id INT IDENTITY(1,1) PRIMARY KEY,
    song_id INT NOT NULL,
    tag_id INT NOT NULL,
    FOREIGN KEY (song_id) REFERENCES Songs(id),
    FOREIGN KEY (tag_id) REFERENCES Tags(id),
    CONSTRAINT UQ_SongTags_Song_Tag UNIQUE (song_id, tag_id)
);
GO

-- [9] Playlists
CREATE TABLE Playlists (
    id INT IDENTITY(1,1) PRIMARY KEY,
    name NVARCHAR(100) NOT NULL,
    is_public BIT DEFAULT 0,
    created_at DATETIME DEFAULT GETDATE(),
    username VARCHAR(50) NOT NULL,
    FOREIGN KEY (username) REFERENCES Users(username)
);
GO

-- [10] Playlist_Songs
CREATE TABLE Playlist_Songs (
    id INT IDENTITY(1,1) PRIMARY KEY,
    playlist_id INT NOT NULL,
    song_id INT NOT NULL,
    sort_order INT DEFAULT 0,
    FOREIGN KEY (playlist_id) REFERENCES Playlists(id),
    FOREIGN KEY (song_id) REFERENCES Songs(id),
    CONSTRAINT UQ_PlaylistSongs_Playlist_Song UNIQUE (playlist_id, song_id)
);
GO

-- [11] Favorites
CREATE TABLE Favorites (
    id INT IDENTITY(1,1) PRIMARY KEY,
    username VARCHAR(50) NOT NULL,
    song_id INT NOT NULL,
    created_at DATETIME DEFAULT GETDATE(),
    FOREIGN KEY (username) REFERENCES Users(username),
    FOREIGN KEY (song_id) REFERENCES Songs(id),
    CONSTRAINT UQ_Favorites_User_Song UNIQUE (username, song_id)
);
GO

-- [12] Song_Listen_History: nhật ký ngầm để tính xu hướng, không hiển thị trong thư viện.
CREATE TABLE Song_Listen_History (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    song_id INT NOT NULL,
    username VARCHAR(50) NULL,
    listened_at DATETIME NOT NULL DEFAULT GETDATE(),
    FOREIGN KEY (song_id) REFERENCES Songs(id),
    FOREIGN KEY (username) REFERENCES Users(username)
);
GO
CREATE INDEX IX_SongListenHistory_Trend ON Song_Listen_History(listened_at, song_id);
GO

-- [13] Song_Comments (Đã tích hợp cấu trúc phân cấp parent_id sạch sẽ ngay từ đầu)
CREATE TABLE Song_Comments (
    id INT IDENTITY(1,1) PRIMARY KEY,
    song_id INT NOT NULL,
    username VARCHAR(50) NOT NULL,
    content NVARCHAR(500) NOT NULL,
    parent_id INT NULL,
    created_at DATETIME DEFAULT GETDATE(),
    FOREIGN KEY (song_id) REFERENCES Songs(id),
    FOREIGN KEY (username) REFERENCES Users(username),
    CONSTRAINT FK_SongComments_Parent FOREIGN KEY (parent_id) REFERENCES Song_Comments(id)
);
GO

-- [13] Follows
CREATE TABLE Follows (
    id INT IDENTITY(1,1) PRIMARY KEY,
    follower VARCHAR(50) NOT NULL,
    following VARCHAR(50) NOT NULL,
    created_at DATETIME DEFAULT GETDATE(),
    FOREIGN KEY (follower) REFERENCES Users(username),
    FOREIGN KEY (following) REFERENCES Users(username),
    CONSTRAINT UQ_Follows_Pair UNIQUE (follower, following),
    CONSTRAINT CK_Follows_NotSelf CHECK (follower <> following)
);
GO

-- [13b] Kết bạn hai chiều
CREATE TABLE Friendships (
    id INT IDENTITY(1,1) PRIMARY KEY,
    requester VARCHAR(50) NOT NULL,
    addressee VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at DATETIME DEFAULT GETDATE(),
    responded_at DATETIME NULL,
    user_low AS (CASE WHEN requester < addressee THEN requester ELSE addressee END) PERSISTED,
    user_high AS (CASE WHEN requester < addressee THEN addressee ELSE requester END) PERSISTED,
    FOREIGN KEY (requester) REFERENCES Users(username),
    FOREIGN KEY (addressee) REFERENCES Users(username),
    CONSTRAINT CK_Friendships_NotSelf CHECK (requester <> addressee),
    CONSTRAINT CK_Friendships_Status CHECK (status IN ('PENDING', 'ACCEPTED')),
    CONSTRAINT UQ_Friendships_Pair UNIQUE (user_low, user_high)
);
GO

-- [14] Notifications
CREATE TABLE Notifications (
    id INT IDENTITY(1,1) PRIMARY KEY,
    username VARCHAR(50) NOT NULL,
    type VARCHAR(50) NOT NULL,
    content NVARCHAR(255) NOT NULL,
    is_read BIT DEFAULT 0,
    ref_id INT NULL,
    created_at DATETIME DEFAULT GETDATE(),
    FOREIGN KEY (username) REFERENCES Users(username)
);
GO
CREATE UNIQUE INDEX UX_Notifications_UserTypeRef
    ON Notifications(username, type, ref_id)
    WHERE ref_id IS NOT NULL;
GO

-- [15] Genres
CREATE TABLE Genres (
    id          INT IDENTITY(1,1) PRIMARY KEY,
    name        NVARCHAR(50) NOT NULL UNIQUE,
    description NVARCHAR(255) NULL,
    created_at  DATETIME DEFAULT GETDATE()
);
GO

-- [16] Lịch sử phân tích nhạc tham khảo. Chỉ lưu kết quả và mã băm, không giữ file tải lên.
CREATE TABLE music_analysis_history (
    id             INT IDENTITY(1,1) PRIMARY KEY,
    username       VARCHAR(50) NOT NULL,
    file_name      NVARCHAR(255) NOT NULL,
    file_hash      VARCHAR(64) NOT NULL,
    detected_label NVARCHAR(100) NOT NULL,
    confidence     FLOAT NULL,
    genre_id       INT NULL,
    created_at     DATETIME NOT NULL DEFAULT GETDATE(),
    CONSTRAINT UQ_MusicAnalysisHistory_UserFile UNIQUE(username, file_hash),
    FOREIGN KEY(username) REFERENCES Users(username),
    FOREIGN KEY(genre_id) REFERENCES Genres(id)
);
GO

-- [17] SongGenres
CREATE TABLE SongGenres(
    song_id INT,
    genre_id INT,
    PRIMARY KEY(song_id, genre_id),
    FOREIGN KEY(song_id) REFERENCES Songs(id),
    FOREIGN KEY(genre_id) REFERENCES Genres(id)
);
GO

-- [17] Albums (CRUD ALBUM)
CREATE TABLE Albums (
    id          INT IDENTITY(1,1) PRIMARY KEY,
    title       NVARCHAR(255) NOT NULL,
    description NVARCHAR(1000) NULL,
    cover_url   VARCHAR(500) NULL,
    is_public   BIT NOT NULL DEFAULT 0,
    release_date DATE NULL,
    created_at  DATETIME DEFAULT GETDATE(),
    username    VARCHAR(50) NOT NULL,
    FOREIGN KEY (username) REFERENCES Users(username)
);
GO

-- [18] Album_Songs
CREATE TABLE Album_Songs (
    id           INT IDENTITY(1,1) PRIMARY KEY,
    album_id     INT NOT NULL,
    song_id      INT NOT NULL,
    track_number INT DEFAULT 0,
    FOREIGN KEY (album_id) REFERENCES Albums(id),
    FOREIGN KEY (song_id) REFERENCES Songs(id),
    CONSTRAINT UQ_AlbumSongs_Album_Song UNIQUE (album_id, song_id)
);
GO

-- [19] Chat_Messages
CREATE TABLE Chat_Messages (
    id INT IDENTITY(1,1) PRIMARY KEY,
    sender VARCHAR(50) NOT NULL,
    recipient VARCHAR(50) NOT NULL,
    content NVARCHAR(500) NOT NULL,
    timestamp DATETIME DEFAULT GETDATE(),
    is_read BIT DEFAULT 0,
    FOREIGN KEY (sender) REFERENCES Users(username),
    FOREIGN KEY (recipient) REFERENCES Users(username)
);
GO

-- [20] Nhật ký thanh toán, dùng transaction_id để chống callback lặp
CREATE TABLE Payment_Logs (
    id INT IDENTITY(1,1) PRIMARY KEY,
    order_code VARCHAR(50) NULL,
    gateway_name VARCHAR(20) NULL,
    transaction_id VARCHAR(100) NOT NULL,
    amount INT NULL,
    content NVARCHAR(MAX) NULL,
    created_at DATETIME DEFAULT GETDATE(),
    CONSTRAINT UQ_PaymentLogs_Transaction UNIQUE (transaction_id)
);
GO


-- =============================================
-- 5. DỮ LIỆU MỒI (SEED DATA)
-- =============================================

-- [1] Phân quyền hệ thống
INSERT INTO Roles (id, name) VALUES
('USER', N'Khách hàng'),
('ADMIN', N'Quản trị viên');
GO

-- [2] Khách hàng thực tế
INSERT INTO Users (username, password, fullname, email, photo, token_balance, enabled, account_tier, pro_expired_at, created_at) VALUES
('admin_core', '{noop}admin2026', N'System Admin', 'admin@musicai.vn', 'admin_avatar.png', 9999, 1, 'FREE', NULL, DATEADD(YEAR, -2, GETDATE())),
('admin_finance', '{noop}admin2026', N'Admin Tài chính', 'finance@musicai.vn', NULL, 999, 1, 'FREE', NULL, DATEADD(DAY, -2, GETDATE())),
('admin_moderator', '{noop}admin2026', N'Admin Kiểm duyệt', 'moderator@musicai.vn', NULL, 999, 1, 'FREE', NULL, DATEADD(MONTH, -3, GETDATE())),
('minh_travel', '{noop}123456', N'Minh Xê Dịch', 'minh.vlog@gmail.com', 'minh.png', 15, 1, 'FREE', NULL, GETDATE()),
('lan_chill', '{noop}123456', N'Lan ASMR', 'lan.podcast@yahoo.com', 'lan.png', 50, 1, 'PRO', '2027-12-31', DATEADD(DAY, -9, GETDATE())),
('zmedia_agency', '{noop}123456', N'Z-Media Agency', 'contact@zmedia.vn', 'zmedia.png', 850, 1, 'PRO', '2027-06-01', DATEADD(MONTH, -2, GETDATE())),
('vy_expired', '{noop}123456', N'Hải Vy', 'haivy.kts@gmail.com', 'vy.png', 15, 1, 'FREE', NULL, DATEADD(YEAR, -1, GETDATE())),
('nam_acoustic', '{noop}123456', N'Nam Acoustic', 'nam.acoustic@gmail.com', NULL, 30, 1, 'FREE', NULL, DATEADD(DAY, -1, GETDATE())),
('mai_podcast', '{noop}123456', N'Mai Podcast', 'mai.podcast@gmail.com', NULL, 25, 1, 'FREE', NULL, DATEADD(MONTH, -1, GETDATE())),
('khoa_edm', '{noop}123456', N'Khoa EDM', 'khoa.edm@gmail.com', NULL, 40, 1, 'PRO', '2027-05-30', DATEADD(DAY, -17, GETDATE())),
('linh_piano', '{noop}123456', N'Linh Piano', 'linh.piano@gmail.com', NULL, 18, 1, 'FREE', NULL, DATEADD(MONTH, -6, GETDATE())),
('cafe_moc', '{noop}123456', N'Cà phê Mộc', 'contact@cafemoc.vn', NULL, 90, 1, 'STUDIO', '2027-04-01', DATEADD(DAY, -4, GETDATE())),
('thao_content', '{noop}123456', N'Thảo Content', 'thao.creator@gmail.com', NULL, 12, 1, 'CREATOR', '2026-09-01', DATEADD(DAY, -12, GETDATE())),
('bao_rock', '{noop}123456', N'Bảo Rock', 'bao.rock@gmail.com', NULL, 0, 0, 'FREE', NULL, DATEADD(YEAR, -2, GETDATE()));
GO

-- [3] Gán quyền
INSERT INTO Authorities (username, role_id) VALUES
('admin_core', 'ADMIN'), ('admin_core', 'USER'),
('admin_finance', 'ADMIN'), ('admin_finance', 'USER'),
('admin_moderator', 'ADMIN'), ('admin_moderator', 'USER'),
('minh_travel', 'USER'), ('lan_chill', 'USER'),
('zmedia_agency', 'USER'), ('vy_expired', 'USER'),
('nam_acoustic', 'USER'), ('mai_podcast', 'USER'),
('khoa_edm', 'USER'), ('linh_piano', 'USER'), ('cafe_moc', 'USER'), ('thao_content', 'USER'), ('bao_rock', 'USER');
GO

-- [4] Gói cước kinh doanh
INSERT INTO Packages (name, tokens, price, old_price, badge, description, tier_code, duration_days) VALUES
(N'Nhà sáng tạo', 60, 29000, 39000, N'Khởi đầu', N'60 lượt tạo nhạc trong 30 ngày, phù hợp creator mới bắt đầu xây kênh', 'CREATOR', 30),
(N'Chuyên nghiệp', 180, 69000, 89000, N'Phổ biến', N'180 lượt tạo nhạc trong 30 ngày, phù hợp đăng video đều mỗi tuần', 'PRO', 30),
(N'Phòng thu', 500, 149000, 199000, N'Tiết kiệm nhất', N'500 lượt tạo nhạc trong 30 ngày, phù hợp nhóm sản xuất và quán cà phê', 'STUDIO', 30);
GO

-- [5] Kho nhạc AI
INSERT INTO Songs (title, prompt, audio_url, status, is_public, lyrics, model_ver, is_remix, parent_id, username) VALUES
(N'Bình minh Tây Bắc', N'Nhạc cinematic hoành tráng', 'https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3', 'COMPLETED', 1, NULL, 'demo-audio', 0, NULL, 'minh_travel'),
(N'Đêm mưa Sài Gòn', N'Nhạc Lofi chill, chậm', 'https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3', 'COMPLETED', 1, NULL, 'demo-audio', 0, NULL, 'lan_chill'),
(N'Mega Sale 11.11', N'Nhạc EDM House giật beat', NULL, 'PENDING', 0, NULL, 'sonic-v4', 0, NULL, 'zmedia_agency'),
(N'Bình minh Tây Bắc (Lofi Remix)', N'Phối lại Lofi', 'https://www.soundhelix.com/examples/mp3/SoundHelix-Song-3.mp3', 'COMPLETED', 1, NULL, 'demo-audio', 1, 1, 'lan_chill'),
(N'Kịch bản Tết', N'Nhạc vui tươi, hào hùng', 'https://www.soundhelix.com/examples/mp3/SoundHelix-Song-4.mp3', 'COMPLETED', 0, NULL, 'demo-audio', 0, NULL, 'vy_expired'),
(N'Chiều bên hiên nhà', N'Acoustic guitar ấm áp, nhịp chậm', 'https://www.soundhelix.com/examples/mp3/SoundHelix-Song-5.mp3', 'COMPLETED', 1, NULL, 'demo-audio', 0, NULL, 'nam_acoustic'),
(N'Chuyện kể đêm khuya', N'Piano nhẹ cho podcast', 'https://www.soundhelix.com/examples/mp3/SoundHelix-Song-6.mp3', 'COMPLETED', 1, NULL, 'demo-audio', 0, NULL, 'mai_podcast'),
(N'Neon City', N'EDM synthwave năng lượng cao', 'https://www.soundhelix.com/examples/mp3/SoundHelix-Song-7.mp3', 'COMPLETED', 1, NULL, 'demo-audio', 0, NULL, 'khoa_edm'),
(N'Mưa trên phím đàn', N'Piano độc tấu thư giãn', 'https://www.soundhelix.com/examples/mp3/SoundHelix-Song-8.mp3', 'COMPLETED', 1, NULL, 'demo-audio', 0, NULL, 'linh_piano'),
(N'Sớm mai ở quán Mộc', N'Jazz lofi không lời cho quán cà phê', 'https://www.soundhelix.com/examples/mp3/SoundHelix-Song-9.mp3', 'COMPLETED', 1, NULL, 'demo-audio', 0, NULL, 'cafe_moc'),
(N'Nhật ký một ngày', N'Pop tươi sáng cho video TikTok', 'https://www.soundhelix.com/examples/mp3/SoundHelix-Song-10.mp3', 'COMPLETED', 1, NULL, 'demo-audio', 0, NULL, 'thao_content'),
(N'Đường chân trời đỏ', N'Rock mạnh mẽ cho montage thể thao', 'https://www.soundhelix.com/examples/mp3/SoundHelix-Song-11.mp3', 'COMPLETED', 1, NULL, 'demo-audio', 0, NULL, 'bao_rock');
GO

-- [6] Gắn thẻ phân loại nhạc
INSERT INTO Tags (name) VALUES
(N'Cinematic'), (N'Travel'), (N'Lofi'), (N'Podcast'), (N'EDM'),
(N'Commercial'), (N'Remix'), (N'Corporate');

INSERT INTO Song_Tags (song_id, tag_id)
SELECT seed.song_id, t.id
FROM (VALUES
    (1, N'Cinematic'), (1, N'Travel'), (2, N'Lofi'), (2, N'Podcast'),
    (3, N'EDM'), (3, N'Commercial'), (4, N'Lofi'), (4, N'Remix'),
    (5, N'Corporate')
) seed(song_id, tag_name)
JOIN Tags t ON t.name = seed.tag_name;
GO

-- [7] Danh sách phát cá nhân
INSERT INTO Playlists (name, is_public, username) VALUES
(N'Nhạc nền Flycam 2026', 1, 'minh_travel'),
(N'Nhạc đọc truyện đêm khuya', 1, 'lan_chill'),
(N'Kho nhạc chạy Ads Tiktok', 0, 'zmedia_agency');
GO

-- [8] Thêm bài hát vào danh sách phát
INSERT INTO Playlist_Songs (playlist_id, song_id, sort_order) VALUES
(1, 1, 1), (2, 2, 1), (2, 4, 2), (3, 3, 1);
GO

-- [9] Lịch sử dòng tiền (Transactions)
INSERT INTO Transactions (username, amount, description) VALUES
('minh_travel', 5, N'Hệ thống tặng token tân thủ'),
('lan_chill', 5, N'Hệ thống tặng token tân thủ'),
('zmedia_agency', 5, N'Hệ thống tặng token tân thủ'),
('minh_travel', -1, N'Tạo nhạc: Bình minh Tây Bắc'),
('lan_chill', -1, N'Tạo nhạc: Đêm mưa Sài Gòn'),
('zmedia_agency', -1, N'Tạo nhạc: Mega Sale 11.11'),
('lan_chill', -1, N'Remix nhạc: Bình minh Tây Bắc (Lofi Remix)'),
('zmedia_agency', 500, N'Nạp thành công gói Phòng thu'),
('minh_travel', 60, N'Nạp thành công gói Nhà sáng tạo'),
('vy_expired', 180, N'Mua gói Chuyên nghiệp (Giao dịch cũ)'),
('vy_expired', -144, N'Đã tiêu hao token lúc còn hạn VIP');
GO

-- [10] Hóa đơn thanh toán thực tế (Orders)
INSERT INTO Orders (order_code, total_price, status, username, package_id) VALUES
('MOMO_987234XN', 149000, 'SUCCESS', 'zmedia_agency', 3),
('VNPAY_459123BC', 29000, 'SUCCESS', 'minh_travel', 1),
('ZALOPAY_7749PO', 69000, 'CANCELLED', 'lan_chill', 2),
('MOMO_OLD_VY', 69000, 'SUCCESS', 'vy_expired', 2),
('SP_DEMO_CAFE_01', 149000, 'SUCCESS', 'cafe_moc', 3),
('SP_DEMO_THAO_02', 29000, 'PENDING', 'thao_content', 1),
('SP_DEMO_BAO_03', 69000, 'FAILED', 'bao_rock', 2),
('SP_DEMO_REVIEW', 69000, 'REVIEW', 'lan_chill', 2),
('SP_DEMO_EXPIRED', 29000, 'EXPIRED', 'minh_travel', 1);

-- Phân bổ ngày để thử bộ lọc Hôm nay / Tháng này / Quý này / Năm nay.
UPDATE Orders SET created_at = DATEADD(DAY, -1, GETDATE()) WHERE order_code = 'MOMO_987234XN';
UPDATE Orders SET created_at = DATEADD(DAY, -8, GETDATE()) WHERE order_code = 'VNPAY_459123BC';
UPDATE Orders SET created_at = DATEADD(MONTH, -2, GETDATE()) WHERE order_code = 'ZALOPAY_7749PO';
UPDATE Orders SET created_at = DATEADD(YEAR, -1, GETDATE()) WHERE order_code = 'MOMO_OLD_VY';
UPDATE Orders SET created_at = DATEADD(DAY, -3, GETDATE()) WHERE order_code = 'SP_DEMO_CAFE_01';
GO

-- Nhật ký MÔ PHỎNG để demo màn hình. Log thật được SePay/VNPay webhook tự ghi khi có tiền về.
INSERT INTO Payment_Logs (order_code, gateway_name, transaction_id, amount, content, created_at) VALUES
('MOMO_987234XN', 'SEPAY', 'SEED-SEPAY-0001', 149000, N'[DEMO] Giao dịch SePay mô phỏng đã đối chiếu thành công', DATEADD(DAY, -1, GETDATE())),
('VNPAY_459123BC', 'VNPAY', 'SEED-VNPAY-0002', 29000, N'[DEMO] Giao dịch VNPay mô phỏng đã đối chiếu thành công', DATEADD(DAY, -8, GETDATE())),
('SP_DEMO_CAFE_01', 'SEPAY', 'SEED-SEPAY-0003', 149000, N'[DEMO] Chuyển khoản quán cà phê mô phỏng', DATEADD(DAY, -3, GETDATE())),
('SP_DEMO_REVIEW', 'SEPAY', 'SEED-SEPAY-0004', 55000, N'[CẦN ĐỐI SOÁT] [DEMO] Số tiền nhận khác số tiền đơn', GETDATE());
GO

-- [11] Tương tác xã hội: Bình luận
INSERT INTO Song_Comments (song_id, username, content, parent_id) VALUES
(1, 'lan_chill', N'Đoạn điệp khúc nghe hay quá. Cho em remix nhé!', NULL),
(4, 'minh_travel', N'Bản remix cuốn quá, đúng chất.', NULL),
(2, 'zmedia_agency', N'Bạn có nhận làm nhạc độc quyền không?', NULL);
GO

-- [12] Phản hồi bình luận (Đã sửa lỗi ID để không bị lỗi khóa ngoại)
INSERT INTO Song_Comments (song_id, username, content, parent_id) 
VALUES (1, 'minh_travel', N'Cảm ơn bạn, cứ tự nhiên nhé!', 1);
GO

-- [13] Tương tác xã hội: Thả tim
INSERT INTO Favorites (username, song_id) VALUES
('lan_chill', 1), ('zmedia_agency', 1), ('minh_travel', 4), ('zmedia_agency', 2);
GO

-- [14] Tương tác xã hội: Theo dõi (Follows)
INSERT INTO Follows (follower, following) VALUES
('lan_chill', 'minh_travel'), ('zmedia_agency', 'lan_chill'), ('zmedia_agency', 'minh_travel');
GO

-- [14b] Quan hệ bạn bè mẫu
INSERT INTO Friendships (requester, addressee, status, responded_at) VALUES
('minh_travel', 'lan_chill', 'ACCEPTED', GETDATE()),
('nam_acoustic', 'mai_podcast', 'ACCEPTED', GETDATE()),
('khoa_edm', 'linh_piano', 'PENDING', NULL);
GO

-- [15] Hệ thống thông báo tự động
INSERT INTO Notifications (username, type, content, ref_id) VALUES
('minh_travel', 'NEW_COMMENT', N'lan_chill đã bình luận', 1),
('lan_chill', 'SONG_COMPLETED', N'Bản nhạc đã tạo xong', 2),
('minh_travel', 'NEW_REMIX', N'lan_chill vừa remix lại bài hát', 4),
('zmedia_agency', 'PAYMENT_SUCCESS', N'Thanh toán thành công', NULL);
GO

-- [16] Thêm Thể Loại (Genres Data)
INSERT INTO Genres (name, description) VALUES
(N'Lofi',       N'Nhạc nhẹ nhàng, thư giãn, phù hợp học tập và làm việc'),
(N'Cinematic',  N'Nhạc nền hoành tráng cho video, phim ảnh'),
(N'Anime',      N'Nhạc theo phong cách anime Nhật Bản'),
(N'EDM',        N'Nhạc điện tử sôi động'),
(N'Acoustic',   N'Nhạc mộc với guitar, piano'),
(N'Folk',       N'Nhạc dân gian và truyền thống'),
(N'Jazz',       N'Nhạc Jazz thư giãn'),
(N'Rock',       N'Nhạc Rock mạnh mẽ');
GO

-- [17] Liên kết Nhạc - Thể Loại
INSERT INTO SongGenres (song_id, genre_id) VALUES
(1, 2), -- Bình minh Tây Bắc: Cinematic
(1, 6), -- Bình minh Tây Bắc: Folk
(2, 1), -- Đêm mưa Sài Gòn: Lofi
(2, 7), -- Đêm mưa Sài Gòn: Jazz
(3, 4), -- Mega Sale 11.11: EDM
(4, 1), -- Bình minh Tây Bắc (Lofi Remix): Lofi
(4, 6), -- Bình minh Tây Bắc (Lofi Remix): Folk
(5, 5), -- Kịch bản Tết: Acoustic
(5, 6), -- Kịch bản Tết: Folk
(6, 5), (7, 7), (8, 4), (9, 5),
(10, 1), (10, 7), (11, 3), (12, 8);
GO

-- [18] Seed Album mẫu
INSERT INTO Albums (title, description, cover_url, username) VALUES
(N'Chill Việt 2026', N'Tuyển tập nhạc Lofi Việt Nam hay nhất 2026', 'https://cdn.musicai.vn/covers/chill-viet-2026.jpg', 'lan_chill'),
(N'Nhạc nền Flycam', N'Nhạc cinematic cho video flycam du lịch', 'https://cdn.musicai.vn/covers/flycam.jpg', 'minh_travel');
GO

INSERT INTO Album_Songs (album_id, song_id, track_number) VALUES
(1, 2, 1), (1, 4, 2),
(2, 1, 1);
GO

-- =============================================
-- 19. BỘ DỮ LIỆU DEMO MỞ RỘNG (01/2026 - 06/08/2026)
-- Mục đích: mỗi màn hình quản trị có hơn 20 dòng để kiểm tra phân trang,
-- lọc theo ngày/tháng/tuần/năm và giao diện ảnh bìa/audio.
-- Toàn bộ ngày bên dưới là ngày cố định để mọi máy chạy cùng thấy một kết quả.
-- =============================================

-- 19.1. Thêm người dùng mẫu (tổng cộng 26 tài khoản, gồm 3 admin).
INSERT INTO Users (username, password, fullname, email, photo, token_balance, enabled, account_tier, pro_expired_at, created_at) VALUES
('anh_foodie', '{noop}123456', N'Anh Foodie', 'anh.foodie@gmail.com', NULL, 38, 1, 'CREATOR', '2026-09-20', '2026-01-07T08:20:00'),
('huyen_studio', '{noop}123456', N'Huyền Studio', 'huyen.studio@gmail.com', NULL, 240, 1, 'STUDIO', '2026-12-31', '2026-01-21T14:15:00'),
('duc_review', '{noop}123456', N'Đức Review', 'duc.review@gmail.com', NULL, 76, 1, 'PRO', '2026-10-10', '2026-02-03T10:00:00'),
('ngoc_cafe', '{noop}123456', N'Ngọc Cafe', 'ngoc.cafe@gmail.com', NULL, 125, 1, 'STUDIO', '2026-11-05', '2026-02-18T16:45:00'),
('tuan_film', '{noop}123456', N'Tuấn Film', 'tuan.film@gmail.com', NULL, 52, 1, 'CREATOR', '2026-09-01', '2026-03-02T09:30:00'),
('hoa_event', '{noop}123456', N'Hoa Event', 'hoa.event@gmail.com', NULL, 160, 1, 'PRO', '2026-12-15', '2026-03-17T11:25:00'),
('phuc_gaming', '{noop}123456', N'Phúc Gaming', 'phuc.gaming@gmail.com', NULL, 45, 1, 'FREE', NULL, '2026-04-01T19:10:00'),
('yen_yoga', '{noop}123456', N'Yến Yoga', 'yen.yoga@gmail.com', NULL, 82, 1, 'CREATOR', '2026-10-01', '2026-04-14T07:50:00'),
('son_market', '{noop}123456', N'Sơn Marketing', 'son.market@gmail.com', NULL, 205, 1, 'STUDIO', '2027-01-01', '2026-05-06T13:10:00'),
('trang_book', '{noop}123456', N'Trang Book', 'trang.book@gmail.com', NULL, 28, 1, 'FREE', NULL, '2026-05-25T20:00:00'),
('long_sport', '{noop}123456', N'Long Sport', 'long.sport@gmail.com', NULL, 64, 1, 'PRO', '2026-11-30', '2026-06-12T06:30:00'),
('nhi_bakery', '{noop}123456', N'Nhi Bakery', 'nhi.bakery@gmail.com', NULL, 35, 1, 'CREATOR', '2026-09-30', '2026-06-28T15:45:00');
GO

INSERT INTO Authorities (username, role_id)
SELECT username, 'USER' FROM Users
WHERE username IN ('anh_foodie','huyen_studio','duc_review','ngoc_cafe','tuan_film','hoa_event','phuc_gaming','yen_yoga','son_market','trang_book','long_sport','nhi_bakery');
GO

-- 19.2. Hoàn thiện ảnh bìa, lượt nghe và ngày tạo cho 12 bài nhạc ban đầu.
UPDATE Songs SET
    -- Bài mẫu nào từng ở trạng thái chờ cũng có audio để mọi card đều nghe thử được.
    audio_url = COALESCE(audio_url, 'https://www.soundhelix.com/examples/mp3/SoundHelix-Song-12.mp3'),
    status = CASE WHEN audio_url IS NULL THEN 'COMPLETED' ELSE status END,
    cover_url = CASE title
        WHEN N'Bình minh Tây Bắc' THEN 'https://images.unsplash.com/photo-1492684223066-81342ee5ff30?auto=format&fit=crop&w=900&q=85'
        WHEN N'Đêm mưa Sài Gòn' THEN 'https://images.unsplash.com/photo-1514525253161-7a46d19cd819?auto=format&fit=crop&w=900&q=85'
        WHEN N'Mega Sale 11.11' THEN 'https://images.unsplash.com/photo-1571266028243-d220c9c3b9be?auto=format&fit=crop&w=900&q=85'
        WHEN N'Bình minh Tây Bắc (Lofi Remix)' THEN 'https://images.unsplash.com/photo-1470229722913-7c0e2dbbafd3?auto=format&fit=crop&w=900&q=85'
        WHEN N'Kịch bản Tết' THEN 'https://images.unsplash.com/photo-1516280440614-37939bbacd81?auto=format&fit=crop&w=900&q=85'
        WHEN N'Chiều bên hiên nhà' THEN 'https://images.unsplash.com/photo-1524368535928-5b5e00ddc76b?auto=format&fit=crop&w=900&q=85'
        WHEN N'Chuyện kể đêm khuya' THEN 'https://images.unsplash.com/photo-1506157786151-b8491531f063?auto=format&fit=crop&w=900&q=85'
        WHEN N'Neon City' THEN 'https://images.unsplash.com/photo-1506157786151-b8491531f063?auto=format&fit=crop&w=900&q=85'
        WHEN N'Mưa trên phím đàn' THEN 'https://images.unsplash.com/photo-1520523839897-bd0b52f945a0?auto=format&fit=crop&w=900&q=85'
        WHEN N'Sớm mai ở quán Mộc' THEN 'https://images.unsplash.com/photo-1493225457124-a3eb161ffa5f?auto=format&fit=crop&w=900&q=85'
        WHEN N'Nhật ký một ngày' THEN 'https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?auto=format&fit=crop&w=900&q=85'
        WHEN N'Đường chân trời đỏ' THEN 'https://images.unsplash.com/photo-1493225457124-a3eb161ffa5f?auto=format&fit=crop&w=900&q=85'
    END,
    listen_count = CASE title
        WHEN N'Bình minh Tây Bắc' THEN 1860 WHEN N'Đêm mưa Sài Gòn' THEN 1430
        WHEN N'Mega Sale 11.11' THEN 915 WHEN N'Bình minh Tây Bắc (Lofi Remix)' THEN 1215
        WHEN N'Kịch bản Tết' THEN 684 WHEN N'Chiều bên hiên nhà' THEN 1120
        WHEN N'Chuyện kể đêm khuya' THEN 732 WHEN N'Neon City' THEN 1090
        WHEN N'Mưa trên phím đàn' THEN 873 WHEN N'Sớm mai ở quán Mộc' THEN 965
        WHEN N'Nhật ký một ngày' THEN 754 WHEN N'Đường chân trời đỏ' THEN 645 END,
    created_at = CASE title
        WHEN N'Bình minh Tây Bắc' THEN '2026-01-08T09:00:00' WHEN N'Đêm mưa Sài Gòn' THEN '2026-01-19T20:30:00'
        WHEN N'Mega Sale 11.11' THEN '2026-02-01T10:30:00' WHEN N'Bình minh Tây Bắc (Lofi Remix)' THEN '2026-02-11T21:00:00'
        WHEN N'Kịch bản Tết' THEN '2026-02-21T08:15:00' WHEN N'Chiều bên hiên nhà' THEN '2026-03-06T17:20:00'
        WHEN N'Chuyện kể đêm khuya' THEN '2026-03-20T22:10:00' WHEN N'Neon City' THEN '2026-04-04T19:45:00'
        WHEN N'Mưa trên phím đàn' THEN '2026-04-18T14:30:00' WHEN N'Sớm mai ở quán Mộc' THEN '2026-05-05T07:40:00'
        WHEN N'Nhật ký một ngày' THEN '2026-05-19T11:10:00' WHEN N'Đường chân trời đỏ' THEN '2026-06-02T16:55:00' END
WHERE title IN (N'Bình minh Tây Bắc',N'Đêm mưa Sài Gòn',N'Mega Sale 11.11',N'Bình minh Tây Bắc (Lofi Remix)',N'Kịch bản Tết',N'Chiều bên hiên nhà',N'Chuyện kể đêm khuya',N'Neon City',N'Mưa trên phím đàn',N'Sớm mai ở quán Mộc',N'Nhật ký một ngày',N'Đường chân trời đỏ');
GO

-- 19.3. 16 bài công khai có ảnh bìa và audio mẫu nghe được (tổng Songs = 28).
INSERT INTO Songs (title, prompt, audio_url, status, is_public, lyrics, model_ver, is_remix, parent_id, cover_url, listen_count, created_at, username) VALUES
(N'Phố lên đèn', N'City pop hiện đại cho video đêm', 'https://www.soundhelix.com/examples/mp3/SoundHelix-Song-12.mp3', 'COMPLETED', 1, N'[Verse] Phố lên đèn, ta đi qua mùa mơ', 'demo-audio', 0, NULL, 'https://images.unsplash.com/photo-1519608487953-e999c86e7450?auto=format&fit=crop&w=900&q=85', 1280, '2026-06-11T20:20:00', 'duc_review'),
(N'Lời chào ngày mới', N'Pop tích cực mở đầu video buổi sáng', 'https://www.soundhelix.com/examples/mp3/SoundHelix-Song-13.mp3', 'COMPLETED', 1, N'[Chorus] Xin chào ngày mới, mình cùng mỉm cười', 'demo-audio', 0, NULL, 'https://images.unsplash.com/photo-1500534314209-a25ddb2bd429?auto=format&fit=crop&w=900&q=85', 1170, '2026-06-16T06:20:00', 'thao_content'),
(N'Góc làm việc tập trung', N'Ambient lofi không lời cho không gian làm việc', 'https://www.soundhelix.com/examples/mp3/SoundHelix-Song-14.mp3', 'COMPLETED', 1, NULL, 'demo-audio', 0, NULL, 'https://images.unsplash.com/photo-1497366754035-f200968a6e72?auto=format&fit=crop&w=900&q=85', 980, '2026-06-20T09:00:00', 'mai_podcast'),
(N'Hè trên biển', N'Tropical house vui tươi cho vlog du lịch', 'https://www.soundhelix.com/examples/mp3/SoundHelix-Song-15.mp3', 'COMPLETED', 1, N'[Verse] Sóng gọi tên mùa hè xanh', 'demo-audio', 0, NULL, 'https://images.unsplash.com/photo-1507525428034-b723cf961d3e?auto=format&fit=crop&w=900&q=85', 1560, '2026-06-24T15:00:00', 'minh_travel'),
(N'Cà phê chiều mưa', N'Jazz lofi nhẹ, không lời cho quán cà phê', 'https://www.soundhelix.com/examples/mp3/SoundHelix-Song-16.mp3', 'COMPLETED', 1, NULL, 'demo-audio', 0, NULL, 'https://images.unsplash.com/photo-1495474472287-4d71bcdd2085?auto=format&fit=crop&w=900&q=85', 1340, '2026-06-29T16:35:00', 'ngoc_cafe'),
(N'Một phút nghỉ ngơi', N'Piano ambient thư giãn giữa giờ', 'https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3', 'COMPLETED', 1, NULL, 'demo-audio', 0, NULL, 'https://images.unsplash.com/photo-1507838153414-b4b713384a76?auto=format&fit=crop&w=900&q=85', 810, '2026-07-03T12:00:00', 'yen_yoga'),
(N'Bản tin buổi sáng', N'Corporate upbeat cho phần mở đầu bản tin', 'https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3', 'COMPLETED', 1, NULL, 'demo-audio', 0, NULL, 'https://images.unsplash.com/photo-1495020689067-958852a7765e?auto=format&fit=crop&w=900&q=85', 655, '2026-07-07T08:00:00', 'zmedia_agency'),
(N'Chạm vào hoàng hôn', N'Ballad guitar ấm áp, vocal Việt', 'https://www.soundhelix.com/examples/mp3/SoundHelix-Song-3.mp3', 'COMPLETED', 1, N'[Verse] Chạm vào hoàng hôn, chạm vào bình yên', 'demo-audio', 0, NULL, 'https://images.unsplash.com/photo-1470252649378-9c29740c9fa8?auto=format&fit=crop&w=900&q=85', 1425, '2026-07-10T18:10:00', 'nam_acoustic'),
(N'Nhịp phố cuối tuần', N'Funk pop năng động cho video sự kiện', 'https://www.soundhelix.com/examples/mp3/SoundHelix-Song-4.mp3', 'COMPLETED', 1, NULL, 'demo-audio', 0, NULL, 'https://images.unsplash.com/photo-1521337581100-8ca9a73a5f79?auto=format&fit=crop&w=900&q=85', 1060, '2026-07-14T19:00:00', 'hoa_event'),
(N'Ký ức vinyl', N'Lofi vintage với tiếng đĩa than', 'https://www.soundhelix.com/examples/mp3/SoundHelix-Song-5.mp3', 'COMPLETED', 1, NULL, 'demo-audio', 0, NULL, 'https://images.unsplash.com/photo-1461360228754-6e81c478b882?auto=format&fit=crop&w=900&q=85', 795, '2026-07-18T21:15:00', 'lan_chill'),
(N'Nắng qua ô cửa', N'Acoustic nhẹ nhàng cho vlog gia đình', 'https://www.soundhelix.com/examples/mp3/SoundHelix-Song-6.mp3', 'COMPLETED', 1, N'[Chorus] Nắng qua ô cửa, nhà mình đầy tiếng cười', 'demo-audio', 0, NULL, 'https://images.unsplash.com/photo-1493663284031-b7e3aefcae8e?auto=format&fit=crop&w=900&q=85', 920, '2026-07-22T10:10:00', 'nhi_bakery'),
(N'Con đường xanh', N'Indie folk cho video sống xanh', 'https://www.soundhelix.com/examples/mp3/SoundHelix-Song-7.mp3', 'COMPLETED', 1, NULL, 'demo-audio', 0, NULL, 'https://images.unsplash.com/photo-1441974231531-c6227db76b6e?auto=format&fit=crop&w=900&q=85', 875, '2026-07-25T07:20:00', 'anh_foodie'),
(N'Đi qua mùa hạ', N'Pop rock trẻ trung, nhịp nhanh', 'https://www.soundhelix.com/examples/mp3/SoundHelix-Song-8.mp3', 'COMPLETED', 1, N'[Verse] Đi qua mùa hạ, giữ lại điều thật trong tim', 'demo-audio', 0, NULL, 'https://images.unsplash.com/photo-1506157786151-b8491531f063?auto=format&fit=crop&w=900&q=85', 1235, '2026-07-29T17:30:00', 'long_sport'),
(N'Lặng giữa thành phố', N'Chillhop đêm muộn, nhịp chậm', 'https://www.soundhelix.com/examples/mp3/SoundHelix-Song-9.mp3', 'COMPLETED', 1, NULL, 'demo-audio', 0, NULL, 'https://images.unsplash.com/photo-1519608487953-e999c86e7450?auto=format&fit=crop&w=900&q=85', 710, '2026-08-01T22:00:00', 'linh_piano'),
(N'Bước chân tự do', N'Electronic uplifting cho reel thể thao', 'https://www.soundhelix.com/examples/mp3/SoundHelix-Song-10.mp3', 'COMPLETED', 1, NULL, 'demo-audio', 0, NULL, 'https://images.unsplash.com/photo-1538805060514-97d9cc17730c?auto=format&fit=crop&w=900&q=85', 1480, '2026-08-04T06:45:00', 'phuc_gaming'),
(N'Giai điệu cửa hàng', N'Nhạc nền nhẹ cho không gian kinh doanh', 'https://www.soundhelix.com/examples/mp3/SoundHelix-Song-11.mp3', 'COMPLETED', 1, NULL, 'demo-audio', 0, NULL, 'https://images.unsplash.com/photo-1445116572660-236099ec97a0?auto=format&fit=crop&w=900&q=85', 690, '2026-08-06T09:10:00', 'cafe_moc');
GO

-- 19.3B. Bổ sung tác phẩm công khai đa dạng để thử phân trang, Khám phá và dữ liệu cộng đồng.
INSERT INTO Songs (title, prompt, audio_url, status, is_public, lyrics, model_ver, is_remix, parent_id, cover_url, listen_count, created_at, username) VALUES
(N'Đêm đèn phố', N'Synthwave neon cho video thành phố về đêm', 'https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3', 'COMPLETED', 1, NULL, 'demo-audio', 0, NULL, 'https://images.unsplash.com/photo-1519608487953-e999c86e7450?auto=format&fit=crop&w=900&q=85', 1840, '2026-01-08T20:30:00', 'duc_review'),
(N'Thư giãn bên hiên', N'Bossa nova nhẹ nhàng cho quán cà phê buổi sáng', 'https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3', 'COMPLETED', 1, NULL, 'demo-audio', 0, NULL, 'https://images.unsplash.com/photo-1511081692775-05d0f180a065?auto=format&fit=crop&w=900&q=85', 960, '2026-01-22T08:15:00', 'ngoc_cafe'),
(N'Sóng nhỏ cuối ngày', N'Lofi êm dịu cho nhật ký và podcast', 'https://www.soundhelix.com/examples/mp3/SoundHelix-Song-3.mp3', 'COMPLETED', 1, NULL, 'demo-audio', 0, NULL, 'https://images.unsplash.com/photo-1507525428034-b723cf961d3e?auto=format&fit=crop&w=900&q=85', 1250, '2026-02-09T17:40:00', 'mai_podcast'),
(N'Bước qua mây', N'Dream pop giàu cảm xúc cho video du lịch', 'https://www.soundhelix.com/examples/mp3/SoundHelix-Song-4.mp3', 'COMPLETED', 1, N'[Chorus] Bước qua mây, mình chạm vào bình minh', 'demo-audio', 0, NULL, 'https://images.unsplash.com/photo-1464822759023-fed622ff2c3b?auto=format&fit=crop&w=900&q=85', 1365, '2026-02-28T06:50:00', 'minh_travel'),
(N'Nhịp sống trẻ', N'Afrobeats sôi động cho video ngắn và thương hiệu', 'https://www.soundhelix.com/examples/mp3/SoundHelix-Song-5.mp3', 'COMPLETED', 1, NULL, 'demo-audio', 0, NULL, 'https://images.unsplash.com/photo-1521337581100-8ca9a73a5f79?auto=format&fit=crop&w=900&q=85', 1670, '2026-03-16T19:10:00', 'thao_content'),
(N'Bản đồ ký ức', N'Soul pop ấm áp, giọng hát Việt', 'https://www.soundhelix.com/examples/mp3/SoundHelix-Song-6.mp3', 'COMPLETED', 1, N'[Verse] Bản đồ ký ức đưa ta về ngày xưa', 'demo-audio', 0, NULL, 'https://images.unsplash.com/photo-1514525253161-7a46d19cd819?auto=format&fit=crop&w=900&q=85', 1095, '2026-03-30T21:05:00', 'lan_chill'),
(N'Mưa qua khung cửa', N'Jazz piano mộc cho không gian đọc sách', 'https://www.soundhelix.com/examples/mp3/SoundHelix-Song-7.mp3', 'COMPLETED', 1, NULL, 'demo-audio', 0, NULL, 'https://images.unsplash.com/photo-1493663284031-b7e3aefcae8e?auto=format&fit=crop&w=900&q=85', 875, '2026-04-14T16:20:00', 'trang_book'),
(N'Tự do ngoài trời', N'Folk acoustic cho vlog dã ngoại', 'https://www.soundhelix.com/examples/mp3/SoundHelix-Song-8.mp3', 'COMPLETED', 1, NULL, 'demo-audio', 0, NULL, 'https://images.unsplash.com/photo-1500534314209-a25ddb2bd429?auto=format&fit=crop&w=900&q=85', 1180, '2026-05-02T10:35:00', 'anh_foodie'),
(N'Chuyến xe tháng tám', N'Pop hiện đại cho video hành trình', 'https://www.soundhelix.com/examples/mp3/SoundHelix-Song-9.mp3', 'COMPLETED', 1, N'[Chorus] Chuyến xe tháng tám mang theo nắng mới', 'demo-audio', 0, NULL, 'https://images.unsplash.com/photo-1473445361085-b9a07f55608b?auto=format&fit=crop&w=900&q=85', 1410, '2026-05-21T07:25:00', 'tuan_film'),
(N'Dư âm sân khấu', N'Techno điện tử mạnh cho sự kiện đêm', 'https://www.soundhelix.com/examples/mp3/SoundHelix-Song-10.mp3', 'COMPLETED', 1, NULL, 'demo-audio', 0, NULL, 'https://images.unsplash.com/photo-1506157786151-b8491531f063?auto=format&fit=crop&w=900&q=85', 1515, '2026-06-09T22:10:00', 'hoa_event'),
(N'Vệt nắng trên bàn', N'Ambient tối giản cho giờ làm việc tập trung', 'https://www.soundhelix.com/examples/mp3/SoundHelix-Song-11.mp3', 'COMPLETED', 1, NULL, 'demo-audio', 0, NULL, 'https://images.unsplash.com/photo-1497366754035-f200968a6e72?auto=format&fit=crop&w=900&q=85', 780, '2026-07-12T09:05:00', 'linh_piano'),
(N'Lời hẹn cuối tuần', N'Indie pop tươi sáng cho video hẹn hò', 'https://www.soundhelix.com/examples/mp3/SoundHelix-Song-12.mp3', 'COMPLETED', 1, N'[Verse] Lời hẹn cuối tuần, mình đi qua phố quen', 'demo-audio', 0, NULL, 'https://images.unsplash.com/photo-1516589178581-6cd7833ae3b2?auto=format&fit=crop&w=900&q=85', 1325, '2026-08-08T18:45:00', 'nhi_bakery');
GO

-- 19.4. Thể loại: 22 lựa chọn để kiểm tra phân trang 10 dòng/trang.
INSERT INTO Genres (name, description) VALUES
(N'Pop', N'Giai điệu đại chúng, dễ nghe và linh hoạt'),
(N'Ballad', N'Nhẹ nhàng, giàu cảm xúc, phù hợp kể chuyện'),
(N'Rap', N'Nhịp điệu mạnh với phần lời đọc'),
(N'R&B', N'Giai điệu mềm mại, hiện đại và giàu groove'),
(N'Ambient', N'Không gian âm thanh thư giãn, tập trung'),
(N'Chillhop', N'Lofi hiện đại cho học tập và đêm muộn'),
(N'Synthwave', N'Âm thanh điện tử retro, neon và hoài niệm'),
(N'House', N'Nhạc điện tử nhịp đều cho không khí sôi động'),
(N'Trap', N'Bass mạnh, nhịp hiện đại cho video ngắn'),
(N'Classical', N'Nhạc cổ điển với piano và dàn dây'),
(N'Country', N'Guitar mộc, gần gũi và kể chuyện'),
(N'Reggae', N'Nhịp điệu thư thả, tích cực'),
(N'Indie', N'Phong cách độc lập, mộc mạc và cá tính'),
(N'Funk', N'Nhịp bass vui, phù hợp sự kiện cuối tuần');
GO

-- Thể loại mở rộng để thử phân trang và sắp xếp trong Admin.
INSERT INTO Genres (name, description, created_at) VALUES
(N'Bossa Nova', N'Nhẹ nhàng, phù hợp quán cà phê', '2024-02-12T09:00:00'),
(N'Dream Pop', N'Âm thanh mơ màng cho video cảm xúc', '2024-04-18T10:00:00'),
(N'Drill', N'Nhịp rap hiện đại, bass mạnh', '2024-06-22T11:00:00'),
(N'Gospel', N'Giai điệu giàu cảm xúc, hợp xướng', '2024-08-14T12:00:00'),
(N'Hyperpop', N'Điện tử nhanh, màu sắc táo bạo', '2024-10-09T13:00:00'),
(N'Indie Pop', N'Pop độc lập, mộc mạc', '2025-01-17T14:00:00'),
(N'K-Pop', N'Pop hiện đại, nhịp bắt tai', '2025-03-21T15:00:00'),
(N'Latin Pop', N'Pop Latin sôi động', '2025-05-16T16:00:00'),
(N'New Age', N'Không gian thư giãn và thiền', '2025-07-11T17:00:00'),
(N'Afrobeats', N'Nhịp điệu hiện đại, sôi động và giàu năng lượng', '2025-09-05T18:00:00'),
(N'Soul', N'Giọng hát ấm áp và sâu lắng', '2026-01-12T19:00:00'),
(N'Techno', N'Điện tử mạnh cho sự kiện đêm', '2026-04-24T20:00:00');
GO

-- Gắn thể loại cho các bài mới bằng tên, không phụ thuộc identity ID.
INSERT INTO SongGenres (song_id, genre_id)
SELECT s.id, g.id
FROM (VALUES
    (N'Phố lên đèn', N'Synthwave'), (N'Lời chào ngày mới', N'Pop'),
    (N'Góc làm việc tập trung', N'Ambient'), (N'Hè trên biển', N'House'),
    (N'Cà phê chiều mưa', N'Jazz'), (N'Một phút nghỉ ngơi', N'Classical'),
    (N'Bản tin buổi sáng', N'Pop'), (N'Chạm vào hoàng hôn', N'Ballad'),
    (N'Nhịp phố cuối tuần', N'Funk'), (N'Ký ức vinyl', N'Chillhop'),
    (N'Nắng qua ô cửa', N'Acoustic'), (N'Con đường xanh', N'Indie'),
    (N'Đi qua mùa hạ', N'Rock'), (N'Lặng giữa thành phố', N'Chillhop'),
    (N'Bước chân tự do', N'EDM'), (N'Giai điệu cửa hàng', N'Ambient')
) AS seed(song_title, genre_name)
JOIN Songs s ON s.title = seed.song_title
JOIN Genres g ON g.name = seed.genre_name;
GO

-- Gắn thể loại cho tác phẩm bổ sung. Một bài có thể mang nhiều thể loại để phản ánh cách phân loại thực tế.
INSERT INTO SongGenres (song_id, genre_id)
SELECT s.id, g.id
FROM (VALUES
    (N'Đêm đèn phố', N'Synthwave'), (N'Đêm đèn phố', N'House'),
    (N'Thư giãn bên hiên', N'Bossa Nova'), (N'Thư giãn bên hiên', N'Jazz'),
    (N'Sóng nhỏ cuối ngày', N'Lofi'), (N'Sóng nhỏ cuối ngày', N'Chillhop'),
    (N'Bước qua mây', N'Dream Pop'), (N'Bước qua mây', N'Pop'),
    (N'Nhịp sống trẻ', N'Afrobeats'), (N'Nhịp sống trẻ', N'Pop'),
    (N'Bản đồ ký ức', N'Soul'), (N'Bản đồ ký ức', N'R&B'),
    (N'Mưa qua khung cửa', N'Jazz'), (N'Mưa qua khung cửa', N'Classical'),
    (N'Tự do ngoài trời', N'Folk'), (N'Tự do ngoài trời', N'Acoustic'),
    (N'Chuyến xe tháng tám', N'Pop'), (N'Chuyến xe tháng tám', N'Indie Pop'),
    (N'Dư âm sân khấu', N'Techno'), (N'Dư âm sân khấu', N'EDM'),
    (N'Vệt nắng trên bàn', N'Ambient'), (N'Vệt nắng trên bàn', N'New Age'),
    (N'Lời hẹn cuối tuần', N'Indie Pop'), (N'Lời hẹn cuối tuần', N'Pop')
) AS seed(song_title, genre_name)
JOIN Songs s ON s.title = seed.song_title
JOIN Genres g ON g.name = seed.genre_name;
GO

-- 19.5. Playlist và Album: mỗi mục có hơn 20 bản ghi để kiểm tra thư viện.
INSERT INTO Playlists (name, is_public, created_at, username) VALUES
(N'Vlog du lịch đầu năm', 1, '2026-01-10T09:00:00', 'minh_travel'),
(N'Nhạc làm việc nhẹ', 1, '2026-01-22T10:00:00', 'mai_podcast'),
(N'Chạy quảng cáo tháng 2', 0, '2026-02-06T11:30:00', 'zmedia_agency'),
(N'Quán Mộc - Buổi sáng', 1, '2026-02-19T07:00:00', 'cafe_moc'),
(N'Góc đọc sách cuối tuần', 1, '2026-03-05T14:00:00', 'trang_book'),
(N'Yoga và hít thở', 0, '2026-03-18T06:10:00', 'yen_yoga'),
(N'Âm nhạc sự kiện', 1, '2026-04-02T15:00:00', 'hoa_event'),
(N'Nhịp game buổi tối', 0, '2026-04-16T20:00:00', 'phuc_gaming'),
(N'Reel ẩm thực', 1, '2026-05-01T11:20:00', 'anh_foodie'),
(N'Phim ngắn mùa hè', 1, '2026-05-15T19:00:00', 'tuan_film'),
(N'Nhạc chờ Podcast', 0, '2026-05-29T22:00:00', 'lan_chill'),
(N'Workout sáng', 1, '2026-06-07T06:00:00', 'long_sport'),
(N'Không gian tiệm bánh', 1, '2026-06-18T08:00:00', 'nhi_bakery'),
(N'Nhạc nền bản tin', 0, '2026-06-26T09:00:00', 'zmedia_agency'),
(N'Chill tháng 7', 1, '2026-07-04T21:00:00', 'lan_chill'),
(N'Album khách hàng', 0, '2026-07-12T12:00:00', 'son_market'),
(N'Âm thanh thành phố', 1, '2026-07-20T22:00:00', 'duc_review'),
(N'Nhạc phim truyền cảm hứng', 1, '2026-07-27T10:30:00', 'tuan_film'),
(N'Khuyến mãi tháng 8', 0, '2026-08-02T09:30:00', 'thao_content'),
(N'Bản nháp quán cà phê', 0, '2026-08-06T08:00:00', 'ngoc_cafe');
GO

INSERT INTO Playlist_Songs (playlist_id, song_id, sort_order)
SELECT p.id, s.id, seed.sort_order
FROM (VALUES
    (N'Vlog du lịch đầu năm', N'Bình minh Tây Bắc', 1), (N'Vlog du lịch đầu năm', N'Hè trên biển', 2), (N'Vlog du lịch đầu năm', N'Chạm vào hoàng hôn', 3), (N'Vlog du lịch đầu năm', N'Con đường xanh', 4),
    (N'Nhạc làm việc nhẹ', N'Góc làm việc tập trung', 1), (N'Nhạc làm việc nhẹ', N'Một phút nghỉ ngơi', 2), (N'Nhạc làm việc nhẹ', N'Bản tin buổi sáng', 3), (N'Nhạc làm việc nhẹ', N'Mưa trên phím đàn', 4),
    (N'Quán Mộc - Buổi sáng', N'Sớm mai ở quán Mộc', 1), (N'Quán Mộc - Buổi sáng', N'Giai điệu cửa hàng', 2), (N'Quán Mộc - Buổi sáng', N'Cà phê chiều mưa', 3),
    (N'Chill tháng 7', N'Ký ức vinyl', 1), (N'Chill tháng 7', N'Lặng giữa thành phố', 2), (N'Chill tháng 7', N'Đêm mưa Sài Gòn', 3),
    (N'Workout sáng', N'Bước chân tự do', 1), (N'Âm nhạc sự kiện', N'Nhịp phố cuối tuần', 1),
    (N'Reel ẩm thực', N'Nắng qua ô cửa', 1), (N'Phim ngắn mùa hè', N'Chạm vào hoàng hôn', 1)
) AS seed(playlist_name, song_title, sort_order)
JOIN Playlists p ON p.name = seed.playlist_name
JOIN Songs s ON s.title = seed.song_title;
GO

INSERT INTO Albums (title, description, cover_url, release_date, created_at, username) VALUES
(N'Nhật ký hành trình', N'Nhạc nền cho các video du lịch đầu năm', 'https://images.unsplash.com/photo-1500534314209-a25ddb2bd429?auto=format&fit=crop&w=900&q=85', '2026-01-31', '2026-01-31T10:00:00', 'minh_travel'),
(N'Thành phố sau mưa', N'Lofi và chillhop cho buổi tối', 'https://images.unsplash.com/photo-1519608487953-e999c86e7450?auto=format&fit=crop&w=900&q=85', '2026-02-28', '2026-02-28T20:00:00', 'lan_chill'),
(N'Âm thanh thương hiệu', N'Bộ nhạc quảng cáo ngắn cho doanh nghiệp', 'https://images.unsplash.com/photo-1495020689067-958852a7765e?auto=format&fit=crop&w=900&q=85', '2026-03-15', '2026-03-15T09:00:00', 'zmedia_agency'),
(N'Piano giữa chiều', N'Nhạc piano không lời', 'https://images.unsplash.com/photo-1520523839897-bd0b52f945a0?auto=format&fit=crop&w=900&q=85', '2026-03-31', '2026-03-31T14:00:00', 'linh_piano'),
(N'Nhạc Mộc', N'Guitar và jazz cho quán cà phê', 'https://images.unsplash.com/photo-1495474472287-4d71bcdd2085?auto=format&fit=crop&w=900&q=85', '2026-04-12', '2026-04-12T08:00:00', 'cafe_moc'),
(N'Ngày năng lượng', N'Pop và electronic cho creator', 'https://images.unsplash.com/photo-1538805060514-97d9cc17730c?auto=format&fit=crop&w=900&q=85', '2026-04-30', '2026-04-30T10:00:00', 'thao_content'),
(N'Tâm sự podcast', N'Nhạc mở đầu và kết thúc podcast', 'https://images.unsplash.com/photo-1506157786151-b8491531f063?auto=format&fit=crop&w=900&q=85', '2026-05-14', '2026-05-14T21:00:00', 'mai_podcast'),
(N'Cuối tuần chuyển động', N'Funk và rock cho hoạt động thể thao', 'https://images.unsplash.com/photo-1521337581100-8ca9a73a5f79?auto=format&fit=crop&w=900&q=85', '2026-05-31', '2026-05-31T16:00:00', 'long_sport'),
(N'Bếp nhỏ vui vẻ', N'Nhạc nền cho video làm bánh', 'https://images.unsplash.com/photo-1493663284031-b7e3aefcae8e?auto=format&fit=crop&w=900&q=85', '2026-06-10', '2026-06-10T09:00:00', 'nhi_bakery'),
(N'Đêm neon', N'Synthwave và EDM thành phố', 'https://images.unsplash.com/photo-1519608487953-e999c86e7450?auto=format&fit=crop&w=900&q=85', '2026-06-22', '2026-06-22T21:00:00', 'khoa_edm'),
(N'Sống xanh', N'Indie folk cho nội dung cộng đồng', 'https://images.unsplash.com/photo-1441974231531-c6227db76b6e?auto=format&fit=crop&w=900&q=85', '2026-07-02', '2026-07-02T07:00:00', 'anh_foodie'),
(N'Hoàng hôn tháng bảy', N'Ballad vocal Việt', 'https://images.unsplash.com/photo-1470252649378-9c29740c9fa8?auto=format&fit=crop&w=900&q=85', '2026-07-12', '2026-07-12T18:00:00', 'nam_acoustic'),
(N'Tiệc ngoài trời', N'Nhạc sự kiện và party', 'https://images.unsplash.com/photo-1492684223066-81342ee5ff30?auto=format&fit=crop&w=900&q=85', '2026-07-18', '2026-07-18T19:00:00', 'hoa_event'),
(N'Không gian tập trung', N'Ambient cho học tập', 'https://images.unsplash.com/photo-1497366754035-f200968a6e72?auto=format&fit=crop&w=900&q=85', '2026-07-24', '2026-07-24T09:00:00', 'yen_yoga'),
(N'Phố tháng tám', N'Nhịp điệu urban trẻ', 'https://images.unsplash.com/photo-1514525253161-7a46d19cd819?auto=format&fit=crop&w=900&q=85', '2026-08-01', '2026-08-01T20:00:00', 'duc_review'),
(N'Gaming highlights', N'Nhạc nền highlight game', 'https://images.unsplash.com/photo-1542751371-adc38448a05e?auto=format&fit=crop&w=900&q=85', '2026-08-06', '2026-08-06T10:00:00', 'phuc_gaming'),
(N'Thương hiệu mùa thu', N'Âm nhạc quảng cáo sắp ra mắt', 'https://images.unsplash.com/photo-1571266028243-d220c9c3b9be?auto=format&fit=crop&w=900&q=85', '2026-08-06', '2026-08-06T11:00:00', 'son_market'),
(N'Chuyện đọc sách', N'Piano và acoustic cho video review sách', 'https://images.unsplash.com/photo-1516280440614-37939bbacd81?auto=format&fit=crop&w=900&q=85', '2026-08-06', '2026-08-06T12:00:00', 'trang_book'),
(N'Vũ trụ sáng tạo', N'Bộ nhạc đa phong cách cho creator', 'https://images.unsplash.com/photo-1470229722913-7c0e2dbbafd3?auto=format&fit=crop&w=900&q=85', '2026-08-06', '2026-08-06T13:00:00', 'huyen_studio'),
(N'Cafe chiều tối', N'Nhạc nền cho không gian quán', 'https://images.unsplash.com/photo-1445116572660-236099ec97a0?auto=format&fit=crop&w=900&q=85', '2026-08-06', '2026-08-06T14:00:00', 'ngoc_cafe');
GO

-- Album mẫu công khai để hiển thị tại Hồ sơ và Khám phá. Album còn lại giữ riêng tư.
UPDATE Albums
SET is_public = CASE WHEN username IN ('minh_travel', 'lan_chill', 'cafe_moc', 'thao_content', 'long_sport') THEN 1 ELSE 0 END;
GO

INSERT INTO Album_Songs (album_id, song_id, track_number)
SELECT a.id, s.id, seed.track_number
FROM (VALUES
    (N'Nhật ký hành trình', N'Bình minh Tây Bắc', 1), (N'Nhật ký hành trình', N'Hè trên biển', 2),
    (N'Thành phố sau mưa', N'Đêm mưa Sài Gòn', 1), (N'Thành phố sau mưa', N'Ký ức vinyl', 2),
    (N'Âm thanh thương hiệu', N'Bản tin buổi sáng', 1), (N'Piano giữa chiều', N'Mưa trên phím đàn', 1),
    (N'Nhạc Mộc', N'Cà phê chiều mưa', 1), (N'Ngày năng lượng', N'Lời chào ngày mới', 1),
    (N'Tâm sự podcast', N'Chuyện kể đêm khuya', 1), (N'Cuối tuần chuyển động', N'Bước chân tự do', 1),
    (N'Bếp nhỏ vui vẻ', N'Nắng qua ô cửa', 1), (N'Đêm neon', N'Phố lên đèn', 1),
    (N'Sống xanh', N'Con đường xanh', 1), (N'Hoàng hôn tháng bảy', N'Chạm vào hoàng hôn', 1),
    (N'Tiệc ngoài trời', N'Nhịp phố cuối tuần', 1), (N'Không gian tập trung', N'Một phút nghỉ ngơi', 1),
    (N'Phố tháng tám', N'Lặng giữa thành phố', 1), (N'Gaming highlights', N'Bước chân tự do', 1),
    (N'Thương hiệu mùa thu', N'Mega Sale 11.11', 1), (N'Chuyện đọc sách', N'Góc làm việc tập trung', 1)
) AS seed(album_title, song_title, track_number)
JOIN Albums a ON a.title = seed.album_title
JOIN Songs s ON s.title = seed.song_title;
GO

-- 19.5A. Dữ liệu riêng cho tài khoản quản trị chính để demo toàn bộ luồng người dùng.
-- Đăng nhập: admin_core / admin2026. Có bài công khai, riêng tư, đang xử lý, playlist và album.
INSERT INTO Songs (title, prompt, audio_url, status, is_public, lyrics, model_ver, is_remix, parent_id, cover_url, listen_count, created_at, username) VALUES
(N'Điều hành buổi sớm', N'Nhạc piano điện tử nhẹ cho phần mở đầu ngày làm việc', 'https://www.soundhelix.com/examples/mp3/SoundHelix-Song-10.mp3', 'COMPLETED', 1, N'[Verse] Một ngày mới bắt đầu, nhịp điệu khẽ gọi tên', 'demo-audio', 0, NULL, 'https://images.unsplash.com/photo-1497366811353-6870744d04b2?auto=format&fit=crop&w=900&q=85', 860, '2026-03-12T08:30:00', 'admin_core'),
(N'Góc kiểm thử riêng', N'Ambient không lời cho không gian tập trung, bản nội bộ', 'https://www.soundhelix.com/examples/mp3/SoundHelix-Song-11.mp3', 'COMPLETED', 0, NULL, 'demo-audio', 0, NULL, 'https://images.unsplash.com/photo-1497366754035-f200968a6e72?auto=format&fit=crop&w=900&q=85', 42, '2026-04-20T14:15:00', 'admin_core'),
(N'Bản tin MusicAI', N'Corporate pop tích cực cho video giới thiệu sản phẩm', 'https://www.soundhelix.com/examples/mp3/SoundHelix-Song-12.mp3', 'COMPLETED', 1, N'[Chorus] MusicAI cùng bạn tạo nên dấu ấn riêng', 'demo-audio', 0, NULL, 'https://images.unsplash.com/photo-1495020689067-958852a7765e?auto=format&fit=crop&w=900&q=85', 1120, '2026-06-08T10:00:00', 'admin_core'),
(N'Bản phối đang hoàn thiện', N'Chillhop thử nghiệm cho video tổng kết', NULL, 'PENDING', 0, NULL, 'sonic-v4', 0, NULL, 'https://images.unsplash.com/photo-1519608487953-e999c86e7450?auto=format&fit=crop&w=900&q=85', 0, '2026-08-05T16:00:00', 'admin_core'),
(N'Điểm hẹn sáng tạo', N'Indie pop truyền cảm hứng cho video giới thiệu đội nhóm', 'https://www.soundhelix.com/examples/mp3/SoundHelix-Song-13.mp3', 'COMPLETED', 1, N'[Verse] Ta gặp nhau trong một giai điệu mới', 'demo-audio', 0, NULL, 'https://images.unsplash.com/photo-1516280440614-37939bbacd81?auto=format&fit=crop&w=900&q=85', 760, '2026-07-16T18:20:00', 'admin_core'),
(N'Đêm vận hành yên tĩnh', N'Ambient nhẹ nhàng cho không gian làm việc muộn', 'https://www.soundhelix.com/examples/mp3/SoundHelix-Song-14.mp3', 'COMPLETED', 0, NULL, 'demo-audio', 0, NULL, 'https://images.unsplash.com/photo-1497366811353-6870744d04b2?auto=format&fit=crop&w=900&q=85', 205, '2026-07-25T22:10:00', 'admin_core');
GO

INSERT INTO SongGenres (song_id, genre_id)
SELECT s.id, g.id
FROM (VALUES
    (N'Điều hành buổi sớm', N'Synthwave'),
    (N'Góc kiểm thử riêng', N'Ambient'),
    (N'Bản tin MusicAI', N'Pop'),
    (N'Bản phối đang hoàn thiện', N'Chillhop'),
    (N'Điểm hẹn sáng tạo', N'Indie'),
    (N'Đêm vận hành yên tĩnh', N'Ambient')
) AS seed(song_title, genre_name)
JOIN Songs s ON s.title = seed.song_title AND s.username = 'admin_core'
JOIN Genres g ON g.name = seed.genre_name;
GO

INSERT INTO Playlists (name, is_public, created_at, username) VALUES
(N'Playlist demo quản trị', 1, '2026-06-10T09:00:00', 'admin_core'),
(N'Bản nháp nội bộ', 0, '2026-08-05T16:05:00', 'admin_core');
GO

INSERT INTO Playlist_Songs (playlist_id, song_id, sort_order)
SELECT p.id, s.id, seed.sort_order
FROM (VALUES
    (N'Playlist demo quản trị', N'Điều hành buổi sớm', 1),
    (N'Playlist demo quản trị', N'Bản tin MusicAI', 2),
    (N'Playlist demo quản trị', N'Điểm hẹn sáng tạo', 3),
    (N'Bản nháp nội bộ', N'Góc kiểm thử riêng', 1),
    (N'Bản nháp nội bộ', N'Bản phối đang hoàn thiện', 2),
    (N'Bản nháp nội bộ', N'Điều hành buổi sớm', 3),
    (N'Bản nháp nội bộ', N'Đêm vận hành yên tĩnh', 4)
) AS seed(playlist_name, song_title, sort_order)
JOIN Playlists p ON p.name = seed.playlist_name AND p.username = 'admin_core'
JOIN Songs s ON s.title = seed.song_title AND s.username = 'admin_core';
GO

INSERT INTO Albums (title, description, cover_url, release_date, created_at, is_public, username) VALUES
(N'Nhịp điệu điều hành', N'Bộ sưu tập công khai của tài khoản quản trị để thử hiển thị cộng đồng', 'https://images.unsplash.com/photo-1470229722913-7c0e2dbbafd3?auto=format&fit=crop&w=900&q=85', '2026-06-15', '2026-06-15T09:00:00', 1, 'admin_core'),
(N'Kho thử nghiệm quản trị', N'Album riêng tư dùng để kiểm tra quyền hiển thị', 'https://images.unsplash.com/photo-1516280440614-37939bbacd81?auto=format&fit=crop&w=900&q=85', '2026-08-05', '2026-08-05T16:10:00', 0, 'admin_core');
GO

INSERT INTO Album_Songs (album_id, song_id, track_number)
SELECT a.id, s.id, seed.track_number
FROM (VALUES
    (N'Nhịp điệu điều hành', N'Điều hành buổi sớm', 1),
    (N'Nhịp điệu điều hành', N'Bản tin MusicAI', 2),
    (N'Nhịp điệu điều hành', N'Điểm hẹn sáng tạo', 3),
    (N'Kho thử nghiệm quản trị', N'Góc kiểm thử riêng', 1),
    (N'Kho thử nghiệm quản trị', N'Bản phối đang hoàn thiện', 2),
    (N'Kho thử nghiệm quản trị', N'Đêm vận hành yên tĩnh', 3)
) AS seed(album_title, song_title, track_number)
JOIN Albums a ON a.title = seed.album_title AND a.username = 'admin_core'
JOIN Songs s ON s.title = seed.song_title AND s.username = 'admin_core';
GO

-- 19.6. Đơn hàng trải đều từ tháng 01 đến ngày 06/08/2026.
-- Kết hợp đủ trạng thái để thử lọc Doanh thu/Đơn hàng và nút duyệt đối soát.
INSERT INTO Orders (order_code, total_price, status, payment_method, created_at, username, package_id)
SELECT seed.order_code, seed.total_price, seed.status, seed.payment_method, seed.created_at, seed.username, p.id
FROM (VALUES
    ('DEMO-2026-01-001', 29000, 'SUCCESS',   'SEPAY', '2026-01-08T09:10:00', 'anh_foodie', N'Nhà sáng tạo'),
    ('DEMO-2026-01-002', 69000, 'SUCCESS',   'VNPAY', '2026-01-24T14:20:00', 'duc_review', N'Chuyên nghiệp'),
    ('DEMO-2026-02-001', 149000,'SUCCESS',   'SEPAY', '2026-02-05T10:45:00', 'huyen_studio', N'Phòng thu'),
    ('DEMO-2026-02-002', 29000, 'CANCELLED', 'SEPAY', '2026-02-20T17:30:00', 'trang_book', N'Nhà sáng tạo'),
    ('DEMO-2026-03-001', 69000, 'SUCCESS',   'VNPAY', '2026-03-04T08:50:00', 'tuan_film', N'Chuyên nghiệp'),
    ('DEMO-2026-03-002', 149000,'SUCCESS',   'SEPAY', '2026-03-19T12:00:00', 'ngoc_cafe', N'Phòng thu'),
    ('DEMO-2026-04-001', 29000, 'EXPIRED',   'SEPAY', '2026-04-03T15:25:00', 'phuc_gaming', N'Nhà sáng tạo'),
    ('DEMO-2026-04-002', 69000, 'SUCCESS',   'VNPAY', '2026-04-16T19:40:00', 'yen_yoga', N'Chuyên nghiệp'),
    ('DEMO-2026-05-001', 149000,'SUCCESS',   'SEPAY', '2026-05-06T10:05:00', 'son_market', N'Phòng thu'),
    ('DEMO-2026-05-002', 29000, 'FAILED',    'SEPAY', '2026-05-27T21:10:00', 'nhi_bakery', N'Nhà sáng tạo'),
    ('DEMO-2026-06-001', 69000, 'SUCCESS',   'VNPAY', '2026-06-09T07:55:00', 'long_sport', N'Chuyên nghiệp'),
    ('DEMO-2026-06-002', 149000,'SUCCESS',   'SEPAY', '2026-06-23T16:35:00', 'cafe_moc', N'Phòng thu'),
    ('DEMO-2026-07-001', 29000, 'SUCCESS',   'SEPAY', '2026-07-04T13:45:00', 'thao_content', N'Nhà sáng tạo'),
    ('DEMO-2026-07-002', 69000, 'REVIEW',    'SEPAY', '2026-07-19T18:15:00', 'lan_chill', N'Chuyên nghiệp'),
    ('DEMO-2026-07-003', 149000,'SUCCESS',   'VNPAY', '2026-07-28T09:30:00', 'hoa_event', N'Phòng thu'),
    ('DEMO-2026-08-001', 29000, 'PENDING',   'SEPAY', '2026-08-06T11:15:00', 'bao_rock', N'Nhà sáng tạo')
) AS seed(order_code, total_price, status, payment_method, created_at, username, package_name)
JOIN Packages p ON p.name = seed.package_name;
GO

-- 19.6a. Dữ liệu bán hàng xuyên nhiều năm: mỗi tuần có 3 đơn để kiểm tra bộ lọc theo tuần/tháng/năm.
;WITH WeekSeeds AS (
    SELECT CAST('2024-01-01' AS DATE) AS week_start
    UNION ALL
    SELECT DATEADD(WEEK, 1, week_start) FROM WeekSeeds WHERE week_start < '2026-08-03'
), WeekOrders AS (
    SELECT w.week_start, v.sequence_no, DATEADD(DAY, v.day_offset, w.week_start) AS created_at,
           CASE v.sequence_no
               WHEN 1 THEN 'SUCCESS'
               WHEN 2 THEN 'SUCCESS'
               ELSE CASE DATEPART(ISO_WEEK, w.week_start) % 4
                   WHEN 0 THEN 'PENDING' WHEN 1 THEN 'REVIEW' WHEN 2 THEN 'FAILED' ELSE 'SUCCESS' END
           END AS status,
           CASE (DATEPART(ISO_WEEK, w.week_start) + v.sequence_no) % 2 WHEN 0 THEN 'SEPAY' ELSE 'VNPAY' END AS payment_method,
           CASE (DATEPART(ISO_WEEK, w.week_start) + v.sequence_no) % 3
               WHEN 0 THEN N'Nhà sáng tạo' WHEN 1 THEN N'Chuyên nghiệp' ELSE N'Phòng thu' END AS package_name,
           CASE (DATEPART(ISO_WEEK, w.week_start) + v.sequence_no) % 8
               WHEN 0 THEN 'anh_foodie' WHEN 1 THEN 'duc_review' WHEN 2 THEN 'huyen_studio' WHEN 3 THEN 'tuan_film'
               WHEN 4 THEN 'ngoc_cafe' WHEN 5 THEN 'long_sport' WHEN 6 THEN 'thao_content' ELSE 'hoa_event' END AS username
    FROM WeekSeeds w CROSS JOIN (VALUES (1, 1), (2, 3), (3, 5)) v(sequence_no, day_offset)
)
INSERT INTO Orders (order_code, total_price, status, payment_method, created_at, username, package_id)
SELECT CONCAT('SEED-WEEK-', CONVERT(CHAR(8), created_at, 112), '-', sequence_no), p.price, status, payment_method, created_at, username, p.id
FROM WeekOrders wo JOIN Packages p ON p.name = wo.package_name
OPTION (MAXRECURSION 200);
GO

-- Nhật ký thanh toán/token tương ứng cho các đơn thành công mẫu để thử phân trang lịch sử.
INSERT INTO Payment_Logs (order_code, gateway_name, transaction_id, amount, content, created_at)
SELECT o.order_code, o.payment_method, CONCAT('SEED-TRANS-', o.order_code), o.total_price,
       N'[DEMO] Thanh toán thành công từ dữ liệu mồi theo tuần', o.created_at
FROM Orders o WHERE o.order_code LIKE 'SEED-WEEK-%' AND o.status = 'SUCCESS';
GO

INSERT INTO Transactions (username, amount, description, created_at)
SELECT o.username, p.tokens, CONCAT(N'Cộng token từ đơn ', o.order_code), o.created_at
FROM Orders o JOIN Packages p ON p.id = o.package_id
WHERE o.order_code LIKE 'SEED-WEEK-%' AND o.status = 'SUCCESS';
GO

-- Lịch sử thanh toán riêng của admin_core: đủ hơn một trang để thử lọc và phân trang ở màn hình người dùng.
INSERT INTO Orders (order_code, total_price, status, payment_method, created_at, username, package_id)
SELECT seed.order_code, seed.total_price, seed.status, seed.payment_method, seed.created_at, 'admin_core', p.id
FROM (VALUES
    ('ADMIN-2026-01-01', 29000,  'SUCCESS',   'SEPAY', '2026-01-05T09:20:00', N'Nhà sáng tạo'),
    ('ADMIN-2026-01-02', 69000,  'SUCCESS',   'VNPAY', '2026-01-26T14:30:00', N'Chuyên nghiệp'),
    ('ADMIN-2026-02-01', 149000, 'SUCCESS',   'SEPAY', '2026-02-14T10:00:00', N'Phòng thu'),
    ('ADMIN-2026-02-02', 29000,  'CANCELLED', 'SEPAY', '2026-02-28T16:25:00', N'Nhà sáng tạo'),
    ('ADMIN-2026-03-01', 69000,  'SUCCESS',   'VNPAY', '2026-03-17T11:40:00', N'Chuyên nghiệp'),
    ('ADMIN-2026-04-01', 29000,  'EXPIRED',   'SEPAY', '2026-04-09T20:10:00', N'Nhà sáng tạo'),
    ('ADMIN-2026-05-01', 149000, 'SUCCESS',   'SEPAY', '2026-05-22T08:15:00', N'Phòng thu'),
    ('ADMIN-2026-06-01', 69000,  'FAILED',    'VNPAY', '2026-06-11T13:35:00', N'Chuyên nghiệp'),
    ('ADMIN-2026-07-01', 29000,  'REVIEW',    'SEPAY', '2026-07-08T17:50:00', N'Nhà sáng tạo'),
    ('ADMIN-2026-07-02', 69000,  'SUCCESS',   'SEPAY', '2026-07-24T09:05:00', N'Chuyên nghiệp'),
    ('ADMIN-2026-08-01', 149000, 'PENDING',   'SEPAY', '2026-08-06T11:45:00', N'Phòng thu')
) AS seed(order_code, total_price, status, payment_method, created_at, package_name)
JOIN Packages p ON p.name = seed.package_name;
GO

-- Điều chỉnh ngày của 9 đơn hàng nền để bộ lọc theo tháng/từng tuần có dữ liệu thật.
UPDATE Orders SET created_at = CASE order_code
    WHEN 'MOMO_987234XN' THEN '2026-01-13T13:00:00'
    WHEN 'VNPAY_459123BC' THEN '2026-02-12T10:00:00'
    WHEN 'ZALOPAY_7749PO' THEN '2026-03-26T11:00:00'
    WHEN 'MOMO_OLD_VY' THEN '2026-04-28T09:00:00'
    WHEN 'SP_DEMO_CAFE_01' THEN '2026-05-18T14:00:00'
    WHEN 'SP_DEMO_THAO_02' THEN '2026-06-30T10:00:00'
    WHEN 'SP_DEMO_BAO_03' THEN '2026-07-08T18:00:00'
    WHEN 'SP_DEMO_REVIEW' THEN '2026-08-02T15:00:00'
    WHEN 'SP_DEMO_EXPIRED' THEN '2026-08-05T08:00:00' END
WHERE order_code IN ('MOMO_987234XN','VNPAY_459123BC','ZALOPAY_7749PO','MOMO_OLD_VY','SP_DEMO_CAFE_01','SP_DEMO_THAO_02','SP_DEMO_BAO_03','SP_DEMO_REVIEW','SP_DEMO_EXPIRED');
GO

-- Trạng thái gói/số dư mẫu khớp với các đơn SUCCESS ở trên; đơn hủy, hết hạn,
-- thất bại và cần đối soát không tự cộng token.
UPDATE Users SET
    account_tier = CASE username
        WHEN 'minh_travel' THEN 'CREATOR' WHEN 'zmedia_agency' THEN 'STUDIO'
        WHEN 'cafe_moc' THEN 'STUDIO' WHEN 'anh_foodie' THEN 'CREATOR'
        WHEN 'duc_review' THEN 'PRO' WHEN 'huyen_studio' THEN 'STUDIO'
        WHEN 'tuan_film' THEN 'PRO' WHEN 'ngoc_cafe' THEN 'STUDIO'
        WHEN 'yen_yoga' THEN 'PRO' WHEN 'son_market' THEN 'STUDIO'
        WHEN 'long_sport' THEN 'PRO' WHEN 'thao_content' THEN 'CREATOR'
        WHEN 'hoa_event' THEN 'STUDIO' ELSE account_tier END,
    token_balance = CASE username
        WHEN 'minh_travel' THEN 58 WHEN 'zmedia_agency' THEN 498
        WHEN 'cafe_moc' THEN 498 WHEN 'anh_foodie' THEN 58
        WHEN 'duc_review' THEN 180 WHEN 'huyen_studio' THEN 500
        WHEN 'tuan_film' THEN 180 WHEN 'ngoc_cafe' THEN 498
        WHEN 'yen_yoga' THEN 180 WHEN 'son_market' THEN 500
        WHEN 'long_sport' THEN 180 WHEN 'thao_content' THEN 58
        WHEN 'hoa_event' THEN 500 ELSE token_balance END,
    pro_expired_at = CASE username
        WHEN 'minh_travel' THEN '2026-09-06T23:59:59' WHEN 'zmedia_agency' THEN '2026-09-06T23:59:59'
        WHEN 'cafe_moc' THEN '2026-09-06T23:59:59' WHEN 'anh_foodie' THEN '2026-09-06T23:59:59'
        WHEN 'duc_review' THEN '2026-09-06T23:59:59' WHEN 'huyen_studio' THEN '2026-09-06T23:59:59'
        WHEN 'tuan_film' THEN '2026-09-06T23:59:59' WHEN 'ngoc_cafe' THEN '2026-09-06T23:59:59'
        WHEN 'yen_yoga' THEN '2026-09-06T23:59:59' WHEN 'son_market' THEN '2026-09-06T23:59:59'
        WHEN 'long_sport' THEN '2026-09-06T23:59:59' WHEN 'thao_content' THEN '2026-09-06T23:59:59'
        WHEN 'hoa_event' THEN '2026-09-06T23:59:59' ELSE pro_expired_at END
WHERE username IN ('minh_travel','zmedia_agency','cafe_moc','anh_foodie','duc_review','huyen_studio','tuan_film','ngoc_cafe','yen_yoga','son_market','long_sport','thao_content','hoa_event');
GO

-- 19.7. Lịch sử token: 25 dòng để thử phân trang/lọc người dùng.
INSERT INTO Transactions (username, amount, description, created_at) VALUES
('anh_foodie', 60, N'Nạp thành công gói Nhà sáng tạo', '2026-01-08T09:12:00'),
('anh_foodie', -2, N'Tạo nhạc: Con đường xanh', '2026-01-10T11:00:00'),
('duc_review', 180, N'Nạp thành công gói Chuyên nghiệp', '2026-01-24T14:22:00'),
('huyen_studio', 500, N'Nạp thành công gói Phòng thu', '2026-02-05T10:47:00'),
('tuan_film', 180, N'Nạp thành công gói Chuyên nghiệp', '2026-03-04T08:52:00'),
('ngoc_cafe', 500, N'Nạp thành công gói Phòng thu', '2026-03-19T12:03:00'),
('yen_yoga', 180, N'Nạp thành công gói Chuyên nghiệp', '2026-04-16T19:42:00'),
('son_market', 500, N'Nạp thành công gói Phòng thu', '2026-05-06T10:08:00'),
('long_sport', 180, N'Nạp thành công gói Chuyên nghiệp', '2026-06-09T07:58:00'),
('cafe_moc', 500, N'Nạp thành công gói Phòng thu', '2026-06-23T16:38:00'),
('thao_content', 60, N'Nạp thành công gói Nhà sáng tạo', '2026-07-04T13:47:00'),
('lan_chill', -2, N'Tạo nhạc: Ký ức vinyl', '2026-07-18T21:17:00'),
('hoa_event', 500, N'Nạp thành công gói Phòng thu', '2026-07-28T09:32:00'),
('phuc_gaming', -2, N'Tạo nhạc: Bước chân tự do', '2026-08-04T06:47:00'),
('ngoc_cafe', -2, N'Tạo nhạc: Cà phê chiều mưa', '2026-08-06T09:12:00');
GO

-- 19.8. Nhật ký thanh toán: 22 bản ghi có mã giao dịch riêng để thử phân trang/lọc.
INSERT INTO Payment_Logs (order_code, gateway_name, transaction_id, amount, content, created_at) VALUES
('DEMO-2026-01-001', 'SEPAY', 'DEMO-SEPAY-202601-001', 29000, N'[DEMO] Đã đối chiếu đúng số tiền', '2026-01-08T09:11:00'),
('DEMO-2026-01-002', 'VNPAY', 'DEMO-VNPAY-202601-002', 69000, N'[DEMO] Thanh toán sandbox thành công', '2026-01-24T14:21:00'),
('DEMO-2026-02-001', 'SEPAY', 'DEMO-SEPAY-202602-001', 149000, N'[DEMO] Đã đối chiếu đúng số tiền', '2026-02-05T10:46:00'),
('DEMO-2026-03-001', 'VNPAY', 'DEMO-VNPAY-202603-001', 69000, N'[DEMO] Thanh toán sandbox thành công', '2026-03-04T08:51:00'),
('DEMO-2026-03-002', 'SEPAY', 'DEMO-SEPAY-202603-002', 149000, N'[DEMO] Đã đối chiếu đúng số tiền', '2026-03-19T12:01:00'),
('DEMO-2026-04-002', 'VNPAY', 'DEMO-VNPAY-202604-001', 69000, N'[DEMO] Thanh toán sandbox thành công', '2026-04-16T19:41:00'),
('DEMO-2026-05-001', 'SEPAY', 'DEMO-SEPAY-202605-001', 149000, N'[DEMO] Đã đối chiếu đúng số tiền', '2026-05-06T10:06:00'),
('DEMO-2026-06-001', 'VNPAY', 'DEMO-VNPAY-202606-001', 69000, N'[DEMO] Thanh toán sandbox thành công', '2026-06-09T07:56:00'),
('DEMO-2026-06-002', 'SEPAY', 'DEMO-SEPAY-202606-002', 149000, N'[DEMO] Đã đối chiếu đúng số tiền', '2026-06-23T16:36:00'),
('DEMO-2026-07-001', 'SEPAY', 'DEMO-SEPAY-202607-001', 29000, N'[DEMO] Đã đối chiếu đúng số tiền', '2026-07-04T13:46:00'),
('DEMO-2026-07-002', 'SEPAY', 'DEMO-SEPAY-202607-REVIEW', 55000, N'[DEMO] Cần đối soát: số tiền nhận thấp hơn đơn', '2026-07-19T18:16:00'),
('DEMO-2026-07-003', 'VNPAY', 'DEMO-VNPAY-202607-003', 149000, N'[DEMO] Thanh toán sandbox thành công', '2026-07-28T09:31:00'),
('MOMO_987234XN', 'SEPAY', 'DEMO-SEPAY-BASE-001', 149000, N'[DEMO] Bản ghi nền tháng 01', '2026-01-13T13:01:00'),
('VNPAY_459123BC', 'VNPAY', 'DEMO-VNPAY-BASE-002', 29000, N'[DEMO] Bản ghi nền tháng 02', '2026-02-12T10:01:00'),
('MOMO_OLD_VY', 'SEPAY', 'DEMO-SEPAY-BASE-003', 69000, N'[DEMO] Bản ghi nền tháng 04', '2026-04-28T09:01:00'),
('SP_DEMO_CAFE_01', 'SEPAY', 'DEMO-SEPAY-BASE-004', 149000, N'[DEMO] Bản ghi nền tháng 05', '2026-05-18T14:01:00'),
('SP_DEMO_BAO_03', 'SEPAY', 'DEMO-SEPAY-FAILED', 0, N'[DEMO] Giao dịch thất bại', '2026-07-08T18:01:00'),
('SP_DEMO_REVIEW', 'SEPAY', 'DEMO-SEPAY-BASE-REVIEW', 55000, N'[DEMO] Cần đối soát số tiền', '2026-08-02T15:01:00');
GO

-- 19.9. Dữ liệu cộng đồng: tim, theo dõi, bình luận, thông báo và chat.
-- Các danh sách đều vượt 20 dòng để kiểm tra trải nghiệm khi có dữ liệu thực tế.
INSERT INTO Favorites (username, song_id, created_at)
SELECT seed.username, s.id, seed.created_at
FROM (VALUES
    ('anh_foodie', N'Bình minh Tây Bắc', '2026-01-09T10:00:00'), ('duc_review', N'Đêm mưa Sài Gòn', '2026-01-25T14:00:00'),
    ('huyen_studio', N'Neon City', '2026-02-06T12:00:00'), ('ngoc_cafe', N'Sớm mai ở quán Mộc', '2026-02-20T08:00:00'),
    ('tuan_film', N'Bình minh Tây Bắc', '2026-03-05T09:00:00'), ('hoa_event', N'Nhịp phố cuối tuần', '2026-03-20T20:00:00'),
    ('phuc_gaming', N'Bước chân tự do', '2026-04-05T19:00:00'), ('yen_yoga', N'Một phút nghỉ ngơi', '2026-04-17T07:00:00'),
    ('son_market', N'Bản tin buổi sáng', '2026-05-07T11:00:00'), ('trang_book', N'Góc làm việc tập trung', '2026-05-28T21:00:00'),
    ('long_sport', N'Đi qua mùa hạ', '2026-06-10T08:00:00'), ('nhi_bakery', N'Nắng qua ô cửa', '2026-06-24T17:00:00'),
    ('minh_travel', N'Hè trên biển', '2026-07-05T12:00:00'), ('lan_chill', N'Ký ức vinyl', '2026-07-19T22:00:00'),
    ('zmedia_agency', N'Phố lên đèn', '2026-07-29T10:00:00'), ('cafe_moc', N'Cà phê chiều mưa', '2026-08-01T18:00:00'),
    ('thao_content', N'Lời chào ngày mới', '2026-08-03T08:00:00'), ('mai_podcast', N'Chuyện kể đêm khuya', '2026-08-04T22:00:00'),
    ('nam_acoustic', N'Chạm vào hoàng hôn', '2026-08-05T18:00:00'), ('linh_piano', N'Mưa trên phím đàn', '2026-08-06T09:00:00')
) AS seed(username, song_title, created_at)
JOIN Songs s ON s.title = seed.song_title;
GO

-- Tạo phân bố lượt thích rõ ràng cho Top bài hát: mỗi người chỉ được thích một lần trên một bài.
;WITH RankedUsers AS (
    SELECT username, ROW_NUMBER() OVER (ORDER BY username) AS user_rank
    FROM Users
), PopularSongs AS (
    SELECT N'Bình minh Tây Bắc' AS song_title, 18 AS target_likes UNION ALL
    SELECT N'Đêm đèn phố', 16 UNION ALL
    SELECT N'Hè trên biển', 15 UNION ALL
    SELECT N'Bước chân tự do', 14 UNION ALL
    SELECT N'Nhịp sống trẻ', 13 UNION ALL
    SELECT N'Cà phê chiều mưa', 12 UNION ALL
    SELECT N'Chạm vào hoàng hôn', 11 UNION ALL
    SELECT N'Chuyến xe tháng tám', 10 UNION ALL
    SELECT N'Đêm mưa Sài Gòn', 9 UNION ALL
    SELECT N'Bản đồ ký ức', 8
)
INSERT INTO Favorites (username, song_id, created_at)
SELECT u.username, s.id, DATEADD(DAY, u.user_rank, CAST('2026-07-01T08:00:00' AS DATETIME))
FROM PopularSongs ps
JOIN Songs s ON s.title = ps.song_title
JOIN RankedUsers u ON u.user_rank <= ps.target_likes
WHERE NOT EXISTS (
    SELECT 1 FROM Favorites f WHERE f.username = u.username AND f.song_id = s.id
);
GO

-- 19.9a. Lượt nghe mới để kiểm tra bộ lọc "Xu hướng" của Admin.
-- Xu hướng dùng tổng tương tác mới: lượt nghe mới cộng lượt tim mới.
INSERT INTO Song_Listen_History (song_id, username, listened_at)
SELECT s.id, seed.username, seed.listened_at
FROM (VALUES
    ('lan_chill', N'Bình minh Tây Bắc', '2026-08-04T08:10:00'), ('anh_foodie', N'Bình minh Tây Bắc', '2026-08-05T09:20:00'),
    ('tuan_film', N'Bình minh Tây Bắc', '2026-08-06T11:15:00'), ('hoa_event', N'Bình minh Tây Bắc', '2026-08-07T20:30:00'),
    ('cafe_moc', N'Cà phê chiều mưa', '2026-08-03T16:10:00'), ('nhi_bakery', N'Cà phê chiều mưa', '2026-08-05T17:20:00'),
    ('trang_book', N'Cà phê chiều mưa', '2026-08-08T18:10:00'), ('phuc_gaming', N'Bước chân tự do', '2026-08-04T06:30:00'),
    ('long_sport', N'Bước chân tự do', '2026-08-06T07:40:00'), ('mai_podcast', N'Bước chân tự do', '2026-08-08T08:50:00'),
    ('minh_travel', N'Hè trên biển', '2026-08-02T10:20:00'), ('ngoc_cafe', N'Hè trên biển', '2026-08-04T11:10:00'),
    ('zmedia_agency', N'Phố lên đèn', '2026-08-06T21:25:00'), ('duc_review', N'Phố lên đèn', '2026-08-07T22:00:00'),
    ('thao_content', N'Lời chào ngày mới', '2026-08-03T07:30:00'), ('yen_yoga', N'Một phút nghỉ ngơi', '2026-08-05T06:50:00'),
    ('nam_acoustic', N'Chạm vào hoàng hôn', '2026-08-08T18:30:00'), ('linh_piano', N'Mưa trên phím đàn', '2026-08-09T09:10:00')
) AS seed(username, song_title, listened_at)
JOIN Songs s ON s.title = seed.song_title;
GO

INSERT INTO Follows (follower, following, created_at) VALUES
('anh_foodie','minh_travel','2026-01-09T10:00:00'), ('duc_review','lan_chill','2026-01-25T14:00:00'),
('huyen_studio','khoa_edm','2026-02-06T12:00:00'), ('ngoc_cafe','cafe_moc','2026-02-20T08:00:00'),
('tuan_film','minh_travel','2026-03-05T09:00:00'), ('hoa_event','thao_content','2026-03-20T20:00:00'),
('phuc_gaming','khoa_edm','2026-04-05T19:00:00'), ('yen_yoga','linh_piano','2026-04-17T07:00:00'),
('son_market','zmedia_agency','2026-05-07T11:00:00'), ('trang_book','mai_podcast','2026-05-28T21:00:00'),
('long_sport','bao_rock','2026-06-10T08:00:00'), ('nhi_bakery','nam_acoustic','2026-06-24T17:00:00'),
('minh_travel','cafe_moc','2026-07-05T12:00:00'), ('lan_chill','linh_piano','2026-07-19T22:00:00'),
('zmedia_agency','hoa_event','2026-07-29T10:00:00'), ('cafe_moc','lan_chill','2026-08-01T18:00:00'),
('thao_content','anh_foodie','2026-08-03T08:00:00'), ('mai_podcast','trang_book','2026-08-04T22:00:00'),
('nam_acoustic','minh_travel','2026-08-05T18:00:00'), ('linh_piano','yen_yoga','2026-08-06T09:00:00');
GO

INSERT INTO Song_Comments (song_id, username, content, parent_id, created_at)
SELECT s.id, seed.username, seed.content, NULL, seed.created_at
FROM (VALUES
    (N'Phố lên đèn','lan_chill',N'Phần synth nghe rất cuốn vào buổi tối.','2026-06-12T20:30:00'),
    (N'Lời chào ngày mới','minh_travel',N'Bài này hợp mở đầu video lắm.','2026-06-17T07:00:00'),
    (N'Góc làm việc tập trung','trang_book',N'Đúng nhạc nền mình đang cần để đọc sách.','2026-06-21T09:30:00'),
    (N'Hè trên biển','anh_foodie',N'Nghe là muốn đi biển ngay.','2026-06-25T15:30:00'),
    (N'Cà phê chiều mưa','cafe_moc',N'Quán mình sẽ thử mở bản này vào chiều mưa.','2026-06-30T17:00:00'),
    (N'Một phút nghỉ ngơi','yen_yoga',N'Nhẹ và rất dễ thở.','2026-07-04T12:30:00'),
    (N'Bản tin buổi sáng','zmedia_agency',N'Nhịp vào rất gọn cho intro.','2026-07-08T08:30:00'),
    (N'Chạm vào hoàng hôn','nhi_bakery',N'Lời bài hát ấm áp quá.','2026-07-11T18:30:00'),
    (N'Nhịp phố cuối tuần','hoa_event',N'Sẽ dùng cho teaser sự kiện cuối tuần.','2026-07-15T19:30:00'),
    (N'Ký ức vinyl','mai_podcast',N'Âm vinyl hợp phần kể chuyện.','2026-07-19T21:30:00'),
    (N'Nắng qua ô cửa','thao_content',N'Màu sắc rất hợp nội dung gia đình.','2026-07-23T10:30:00'),
    (N'Con đường xanh','tuan_film',N'Có thể làm nhạc nền phim ngắn.','2026-07-26T08:30:00'),
    (N'Đi qua mùa hạ','long_sport',N'Beat chạy bộ rất ổn.','2026-07-30T18:30:00'),
    (N'Lặng giữa thành phố','duc_review',N'Không khí thành phố đêm nghe rõ nét.','2026-08-02T22:30:00'),
    (N'Bước chân tự do','phuc_gaming',N'Hợp clip gaming highlight.','2026-08-04T07:30:00'),
    (N'Giai điệu cửa hàng','ngoc_cafe',N'Nhẹ vừa đủ để khách trò chuyện.','2026-08-06T10:00:00')
) AS seed(song_title, username, content, created_at)
JOIN Songs s ON s.title = seed.song_title;
GO

INSERT INTO Song_Comments (song_id, username, content, parent_id, created_at)
SELECT s.id, 'huyen_studio', N'Bộ dữ liệu demo này đủ để mình thử phần phân trang rồi.', NULL, '2026-08-06T13:30:00'
FROM Songs s WHERE s.title = N'Giai điệu cửa hàng';
GO

INSERT INTO Notifications (username, type, content, is_read, ref_id, created_at) VALUES
('minh_travel','FOLLOW_SONG',N'Lan ASMR đã phát hành bài mới: Ký ức vinyl',0,NULL,'2026-01-19T21:00:00'),
('lan_chill','FOLLOW_SONG',N'Minh Xê Dịch đã phát hành bài mới: Hè trên biển',1,NULL,'2026-02-12T16:00:00'),
('zmedia_agency','PAYMENT_SUCCESS',N'Thanh toán gói Phòng thu thành công',1,NULL,'2026-02-05T10:47:00'),
('cafe_moc','PAYMENT_SUCCESS',N'Thanh toán gói Phòng thu thành công',0,NULL,'2026-03-19T12:03:00'),
('thao_content','SONG_COMPLETED',N'Bài Lời chào ngày mới đã tạo xong',1,NULL,'2026-06-16T06:25:00'),
('mai_podcast','SONG_COMPLETED',N'Bài Góc làm việc tập trung đã tạo xong',0,NULL,'2026-06-20T09:05:00'),
('minh_travel','SONG_COMPLETED',N'Bài Hè trên biển đã tạo xong',1,NULL,'2026-06-24T15:05:00'),
('ngoc_cafe','SONG_COMPLETED',N'Bài Cà phê chiều mưa đã tạo xong',0,NULL,'2026-06-29T16:40:00'),
('yen_yoga','SONG_COMPLETED',N'Bài Một phút nghỉ ngơi đã tạo xong',1,NULL,'2026-07-03T12:05:00'),
('zmedia_agency','SONG_COMPLETED',N'Bài Bản tin buổi sáng đã tạo xong',1,NULL,'2026-07-07T08:05:00'),
('nam_acoustic','SONG_COMPLETED',N'Bài Chạm vào hoàng hôn đã tạo xong',0,NULL,'2026-07-10T18:15:00'),
('hoa_event','SONG_COMPLETED',N'Bài Nhịp phố cuối tuần đã tạo xong',1,NULL,'2026-07-14T19:05:00'),
('lan_chill','SONG_COMPLETED',N'Bài Ký ức vinyl đã tạo xong',0,NULL,'2026-07-18T21:20:00'),
('nhi_bakery','SONG_COMPLETED',N'Bài Nắng qua ô cửa đã tạo xong',1,NULL,'2026-07-22T10:15:00'),
('anh_foodie','SONG_COMPLETED',N'Bài Con đường xanh đã tạo xong',0,NULL,'2026-07-25T07:25:00'),
('long_sport','SONG_COMPLETED',N'Bài Đi qua mùa hạ đã tạo xong',1,NULL,'2026-07-29T17:35:00'),
('linh_piano','SONG_COMPLETED',N'Bài Lặng giữa thành phố đã tạo xong',0,NULL,'2026-08-01T22:05:00'),
('phuc_gaming','SONG_COMPLETED',N'Bài Bước chân tự do đã tạo xong',0,NULL,'2026-08-04T06:50:00'),
('cafe_moc','SONG_COMPLETED',N'Bài Giai điệu cửa hàng đã tạo xong',1,NULL,'2026-08-06T09:15:00'),
('lan_chill','PAYMENT_REVIEW',N'Đơn DEMO-2026-07-002 đang cần đối soát',0,NULL,'2026-07-19T18:16:00');
GO

INSERT INTO Chat_Messages (sender, recipient, content, timestamp, is_read) VALUES
('minh_travel','lan_chill',N'Bản remix nghe rất hợp video flycam.', '2026-01-20T20:00:00',1),
('lan_chill','minh_travel',N'Cảm ơn bạn, mình sẽ gửi bản cập nhật nhé.', '2026-01-20T20:03:00',1),
('cafe_moc','ngoc_cafe',N'Bạn thử nghe playlist buổi sáng chưa?', '2026-02-21T08:00:00',1),
('ngoc_cafe','cafe_moc',N'Mình đang mở thử trong quán, khá ổn.', '2026-02-21T08:05:00',1),
('thao_content','zmedia_agency',N'Mình cần một intro 10 giây cho TikTok.', '2026-03-08T10:00:00',1),
('zmedia_agency','thao_content',N'Bạn chọn Pop hoặc House trong wizard nhé.', '2026-03-08T10:05:00',1),
('yen_yoga','linh_piano',N'Piano bản mới rất thư giãn.', '2026-04-18T07:00:00',1),
('linh_piano','yen_yoga',N'Cảm ơn bạn, mình sẽ làm thêm bản 5 phút.', '2026-04-18T07:05:00',1),
('hoa_event','long_sport',N'Bài Funk dùng cho sự kiện được không?', '2026-05-20T18:00:00',1),
('long_sport','hoa_event',N'Được, nhịp phù hợp mở màn.', '2026-05-20T18:04:00',1),
('nhi_bakery','nam_acoustic',N'Guitar bài Nắng qua ô cửa rất hợp tiệm bánh.', '2026-06-02T09:00:00',1),
('nam_acoustic','nhi_bakery',N'Mình vui vì bạn thích.', '2026-06-02T09:03:00',1),
('phuc_gaming','khoa_edm',N'Có thể làm bass mạnh thêm chút không?', '2026-06-18T22:00:00',1),
('khoa_edm','phuc_gaming',N'Bạn chọn mức năng lượng cao ở bước phong cách.', '2026-06-18T22:04:00',1),
('anh_foodie','minh_travel',N'Bài đi biển này quay food tour cũng hợp.', '2026-07-05T12:00:00',1),
('minh_travel','anh_foodie',N'Hay quá, bạn cứ thêm vào playlist nhé.', '2026-07-05T12:03:00',1),
('duc_review','trang_book',N'Mình vừa nghe Góc làm việc tập trung.', '2026-07-25T15:00:00',0),
('trang_book','duc_review',N'Mình cũng đang dùng để đọc sách.', '2026-07-25T15:03:00',0),
('son_market','thao_content',N'Chiến dịch tháng 8 cần nhạc 15 giây.', '2026-08-03T09:00:00',0),
('thao_content','son_market',N'Mình đã lưu preset trong wizard rồi.', '2026-08-03T09:05:00',0);
GO

INSERT INTO Chat_Messages (sender, recipient, content, timestamp, is_read) VALUES
('huyen_studio','son_market',N'Mình vừa lưu album demo, bạn xem giúp nhé.', '2026-08-06T13:10:00',0),
('son_market','huyen_studio',N'Ổn rồi, giao diện danh sách rất rõ.', '2026-08-06T13:15:00',0);
GO
GO

-- =============================================
-- 6. KIỂM TRA LẠI DỮ LIỆU
-- =============================================
SELECT 'Users' AS TableName, COUNT(*) AS TotalRows FROM Users;
SELECT 'Songs' AS TableName, COUNT(*) AS TotalRows FROM Songs;
SELECT 'Packages' AS TableName, COUNT(*) AS TotalRows FROM Packages;
SELECT 'Orders' AS TableName, COUNT(*) AS TotalRows FROM Orders;
SELECT 'Playlists' AS TableName, COUNT(*) AS TotalRows FROM Playlists;
SELECT 'Genres' AS TableName, COUNT(*) AS TotalRows FROM Genres;
SELECT 'Albums' AS TableName, COUNT(*) AS TotalRows FROM Albums;
SELECT 'Transactions' AS TableName, COUNT(*) AS TotalRows FROM Transactions;
SELECT 'Payment_Logs' AS TableName, COUNT(*) AS TotalRows FROM Payment_Logs;
SELECT 'Favorites' AS TableName, COUNT(*) AS TotalRows FROM Favorites;
SELECT 'Follows' AS TableName, COUNT(*) AS TotalRows FROM Follows;
SELECT 'Song_Comments' AS TableName, COUNT(*) AS TotalRows FROM Song_Comments;
SELECT 'Notifications' AS TableName, COUNT(*) AS TotalRows FROM Notifications;
SELECT 'Chat_Messages' AS TableName, COUNT(*) AS TotalRows FROM Chat_Messages;
GO
