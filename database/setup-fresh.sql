/*
  WEBMUSICAI - CÀI ĐẶT DATABASE MỚI

  CẢNH BÁO: Script này XÓA toàn bộ MusicAI_DB hiện có rồi tạo lại từ đầu.
  Chỉ chạy khi cài mới hoặc khi chắc chắn không cần giữ dữ liệu cũ.
  Nếu database đang có dữ liệu cần giữ, hãy chạy upgrade-existing.sql thay thế.
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
    auth_provider VARCHAR(20) NOT NULL DEFAULT 'LOCAL'
);
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

-- [8] Song_Tags
CREATE TABLE Song_Tags (
    id INT IDENTITY(1,1) PRIMARY KEY,
    song_id INT NOT NULL,
    tag NVARCHAR(50) NOT NULL,
    FOREIGN KEY (song_id) REFERENCES Songs(id),
    CONSTRAINT UQ_SongTags_Song_Tag UNIQUE (song_id, tag)
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

-- [12] Song_Comments (Đã tích hợp cấu trúc phân cấp parent_id sạch sẽ ngay từ đầu)
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

-- [16] SongGenres
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
INSERT INTO Users (username, password, fullname, email, photo, token_balance, enabled, account_tier, pro_expired_at) VALUES
('admin_core', '{noop}admin2026', N'System Admin', 'admin@musicai.vn', 'admin_avatar.png', 9999, 1, 'FREE', NULL),
('minh_travel', '{noop}123456', N'Minh Xê Dịch', 'minh.vlog@gmail.com', 'minh.png', 15, 1, 'FREE', NULL),
('lan_chill', '{noop}123456', N'Lan ASMR', 'lan.podcast@yahoo.com', 'lan.png', 50, 1, 'PRO', '2027-12-31'),
('zmedia_agency', '{noop}123456', N'Z-Media Agency', 'contact@zmedia.vn', 'zmedia.png', 850, 1, 'PRO', '2027-06-01'),
('vy_expired', '{noop}123456', N'Hải Vy', 'haivy.kts@gmail.com', 'vy.png', 15, 1, 'FREE', NULL),
('nam_acoustic', '{noop}123456', N'Nam Acoustic', 'nam.acoustic@gmail.com', NULL, 30, 1, 'FREE', NULL),
('mai_podcast', '{noop}123456', N'Mai Podcast', 'mai.podcast@gmail.com', NULL, 25, 1, 'FREE', NULL),
('khoa_edm', '{noop}123456', N'Khoa EDM', 'khoa.edm@gmail.com', NULL, 40, 1, 'PRO', '2027-05-30'),
('linh_piano', '{noop}123456', N'Linh Piano', 'linh.piano@gmail.com', NULL, 18, 1, 'FREE', NULL);
GO

-- [3] Gán quyền
INSERT INTO Authorities (username, role_id) VALUES
('admin_core', 'ADMIN'), ('admin_core', 'USER'),
('minh_travel', 'USER'), ('lan_chill', 'USER'),
('zmedia_agency', 'USER'), ('vy_expired', 'USER'),
('nam_acoustic', 'USER'), ('mai_podcast', 'USER'),
('khoa_edm', 'USER'), ('linh_piano', 'USER');
GO

-- [4] Gói cước kinh doanh
INSERT INTO Packages (name, tokens, price, old_price, badge, description, tier_code, duration_days) VALUES
(N'Nhà sáng tạo', 45, 3000, NULL, NULL, N'45 token, tạo nhạc riêng tư và sử dụng đầy đủ thư viện trong 30 ngày', 'CREATOR', 30),
(N'Chuyên nghiệp', 120, 5000, NULL, N'Phổ biến', N'120 token, phù hợp người sáng tạo nội dung thường xuyên trong 30 ngày', 'PRO', 30),
(N'Phòng thu', 300, 10000, NULL, N'Nhiều token nhất', N'300 token, phù hợp nhóm sản xuất và trình diễn toàn bộ tính năng trong 30 ngày', 'STUDIO', 30);
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
(N'Mưa trên phím đàn', N'Piano độc tấu thư giãn', 'https://www.soundhelix.com/examples/mp3/SoundHelix-Song-8.mp3', 'COMPLETED', 1, NULL, 'demo-audio', 0, NULL, 'linh_piano');
GO

-- [6] Gắn thẻ phân loại nhạc
INSERT INTO Song_Tags (song_id, tag) VALUES
(1, N'Cinematic'), (1, N'Travel'), (2, N'Lofi'), (2, N'Podcast'),
(3, N'EDM'), (3, N'Commercial'), (4, N'Lofi'), (4, N'Remix'),
(5, N'Corporate');
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
('zmedia_agency', 300, N'Nạp thành công gói Phòng thu'),
('minh_travel', 45, N'Nạp thành công gói Nhà sáng tạo'),
('vy_expired', 120, N'Mua gói Chuyên nghiệp (Giao dịch cũ)'),
('vy_expired', -288, N'Đã tiêu hao token lúc còn hạn VIP');
GO

-- [10] Hóa đơn thanh toán thực tế (Orders)
INSERT INTO Orders (order_code, total_price, status, username, package_id) VALUES
('MOMO_987234XN', 10000, 'SUCCESS', 'zmedia_agency', 3),
('VNPAY_459123BC', 3000, 'SUCCESS', 'minh_travel', 1),
('ZALOPAY_7749PO', 5000, 'CANCELLED', 'lan_chill', 2),
('MOMO_OLD_VY', 5000, 'SUCCESS', 'vy_expired', 2);
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
(5, 6); -- Kịch bản Tết: Folk
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
-- 6. KIỂM TRA LẠI DỮ LIỆU
-- =============================================
SELECT 'Users' AS TableName, COUNT(*) AS TotalRows FROM Users;
SELECT 'Songs' AS TableName, COUNT(*) AS TotalRows FROM Songs;
SELECT 'Packages' AS TableName, COUNT(*) AS TotalRows FROM Packages;
SELECT 'Orders' AS TableName, COUNT(*) AS TotalRows FROM Orders;
SELECT 'Playlists' AS TableName, COUNT(*) AS TotalRows FROM Playlists;
SELECT 'Genres' AS TableName, COUNT(*) AS TotalRows FROM Genres;
SELECT 'Albums' AS TableName, COUNT(*) AS TotalRows FROM Albums;
GO
