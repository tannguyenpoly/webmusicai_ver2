﻿-- =============================================
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

-- Kiểm tra nếu chưa có Login thì mới tạo (tránh lỗi báo đỏ khi chạy lại script nhiều lần)
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

-- Tạo User trong database liên kết với Login vừa tạo
CREATE USER dtbmusic FOR LOGIN dtbmusic;
GO

-- Cấp quyền kết nối (truy cập) vào database
GRANT CONNECT TO dtbmusic;

-- Cấp quyền thêm, xóa, sửa và đọc dữ liệu (Toàn quyền thao tác dữ liệu)
ALTER ROLE db_datareader ADD MEMBER dtbmusic; -- Quyền đọc dữ liệu (Select)
ALTER ROLE db_datawriter ADD MEMBER dtbmusic; -- Quyền ghi dữ liệu (Insert, Update, Delete)
GO

-- =============================================
-- 4. TẠO CÁC BẢNG (TABLES) - TỔNG 14 BẢNG
-- =============================================

-- [1] Users
CREATE TABLE Users (
    username VARCHAR(50) PRIMARY KEY,
    password VARCHAR(100) NOT NULL,
    fullname NVARCHAR(100) NOT NULL,
    email VARCHAR(100) NULL,
    photo VARCHAR(255) NULL,
    token_balance INT DEFAULT 0,
    enabled BIT DEFAULT 1
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
    FOREIGN KEY (role_id) REFERENCES Roles(id)
);
GO

-- [4] Songs (Đã nâng cấp cho tính năng Viral)
CREATE TABLE Songs (
    id INT IDENTITY(1,1) PRIMARY KEY,
    title NVARCHAR(255) NOT NULL,
    prompt NVARCHAR(MAX) NOT NULL,
    audio_url VARCHAR(500) NULL,
    status VARCHAR(20) NOT NULL, 
    is_public BIT DEFAULT 0,
    lyrics NVARCHAR(MAX) NULL,          -- Lời bài hát
    model_ver VARCHAR(20) NULL,         -- Version AI (VD: sonic-v4)
    is_remix BIT DEFAULT 0,             -- Đánh dấu là bản phối lại
    parent_id INT NULL,                 -- ID bài gốc nếu là remix
    created_at DATETIME DEFAULT GETDATE(),
    username VARCHAR(50) NOT NULL,
    FOREIGN KEY (username) REFERENCES Users(username),
    FOREIGN KEY (parent_id) REFERENCES Songs(id)
);
GO

-- [5] Transactions (Ví token)
CREATE TABLE Transactions (
    id INT IDENTITY(1,1) PRIMARY KEY,
    username VARCHAR(50) NOT NULL,
    amount INT NOT NULL,
    description NVARCHAR(255) NULL,
    created_at DATETIME DEFAULT GETDATE(),
    FOREIGN KEY (username) REFERENCES Users(username)
);
GO

-- [6] Packages (Các gói Token bán)
CREATE TABLE Packages (
    id INT IDENTITY(1,1) PRIMARY KEY,
    name NVARCHAR(100) NOT NULL,
    tokens INT NOT NULL,
    price INT NOT NULL,                 -- Giá tiền VND
    description NVARCHAR(255) NULL
);
GO

-- [7] Orders (Hóa đơn mua gói)
CREATE TABLE Orders (
    id INT IDENTITY(1,1) PRIMARY KEY,
    order_code VARCHAR(50) UNIQUE NOT NULL, -- Mã giao dịch VNPAY/Ngân hàng
    total_price INT NOT NULL,
    status VARCHAR(20) NOT NULL,            -- PENDING, SUCCESS, FAILED
    created_at DATETIME DEFAULT GETDATE(),
    username VARCHAR(50) NOT NULL,
    package_id INT NOT NULL,
    FOREIGN KEY (username) REFERENCES Users(username),
    FOREIGN KEY (package_id) REFERENCES Packages(id)
);
GO

-- [8] Song_Tags (Thẻ phân loại nhạc)
CREATE TABLE Song_Tags (
    id INT IDENTITY(1,1) PRIMARY KEY,
    song_id INT NOT NULL,
    tag NVARCHAR(50) NOT NULL,
    FOREIGN KEY (song_id) REFERENCES Songs(id)
);
GO

-- [9] Playlists (Danh sách phát)
CREATE TABLE Playlists (
    id INT IDENTITY(1,1) PRIMARY KEY,
    name NVARCHAR(100) NOT NULL,
    is_public BIT DEFAULT 0,
    created_at DATETIME DEFAULT GETDATE(),
    username VARCHAR(50) NOT NULL,
    FOREIGN KEY (username) REFERENCES Users(username)
);
GO

-- [10] Playlist_Songs (Chi tiết danh sách phát)
CREATE TABLE Playlist_Songs (
    id INT IDENTITY(1,1) PRIMARY KEY,
    playlist_id INT NOT NULL,
    song_id INT NOT NULL,
    sort_order INT DEFAULT 0,
    FOREIGN KEY (playlist_id) REFERENCES Playlists(id),
    FOREIGN KEY (song_id) REFERENCES Songs(id)
);
GO

-- [11] Favorites (Lượt yêu thích/Thả tim)
CREATE TABLE Favorites (
    id INT IDENTITY(1,1) PRIMARY KEY,
    username VARCHAR(50) NOT NULL,
    song_id INT NOT NULL,
    created_at DATETIME DEFAULT GETDATE(),
    FOREIGN KEY (username) REFERENCES Users(username),
    FOREIGN KEY (song_id) REFERENCES Songs(id)
);
GO

-- [12] Song_Comments (Bình luận)
CREATE TABLE Song_Comments (
    id INT IDENTITY(1,1) PRIMARY KEY,
    song_id INT NOT NULL,
    username VARCHAR(50) NOT NULL,
    content NVARCHAR(500) NOT NULL,
    created_at DATETIME DEFAULT GETDATE(),
    FOREIGN KEY (song_id) REFERENCES Songs(id),
    FOREIGN KEY (username) REFERENCES Users(username)
);
GO

-- [13] Follows (Theo dõi)
CREATE TABLE Follows (
    id INT IDENTITY(1,1) PRIMARY KEY,
    follower VARCHAR(50) NOT NULL,
    following VARCHAR(50) NOT NULL,
    created_at DATETIME DEFAULT GETDATE(),
    FOREIGN KEY (follower) REFERENCES Users(username),
    FOREIGN KEY (following) REFERENCES Users(username)
);
GO

-- [14] Notifications (Thông báo)
CREATE TABLE Notifications (
    id INT IDENTITY(1,1) PRIMARY KEY,
    username VARCHAR(50) NOT NULL,
    type VARCHAR(50) NOT NULL,          -- SONG_COMPLETED, NEW_LIKE, NEW_COMMENT
    content NVARCHAR(255) NOT NULL,
    is_read BIT DEFAULT 0,
    ref_id INT NULL,                    -- ID tham chiếu (song_id, comment_id)
    created_at DATETIME DEFAULT GETDATE(),
    FOREIGN KEY (username) REFERENCES Users(username)
);
GO

-- =============================================
-- 5. DỮ LIỆU MỒI (SEED DATA) - KỊCH BẢN THỰC TẾ
-- =============================================

-- [1] Phân quyền hệ thống
INSERT INTO Roles (id, name) VALUES 
('USER', N'Khách hàng'),
('ADMIN', N'Quản trị viên');
GO

-- [2] Khách hàng thực tế
INSERT INTO Users (username, password, fullname, email, photo, token_balance, enabled) VALUES 
('admin_core', '{noop}admin2026', N'System Admin', 'admin@musicai.vn', 'admin_avatar.png', 9999, 1),
('minh_travel', '{noop}123456', N'Minh Xê Dịch', 'minh.vlog@gmail.com', 'minh.png', 15, 1),
('lan_chill', '{noop}123456', N'Lan ASMR', 'lan.podcast@yahoo.com', 'lan.png', 50, 1),
('zmedia_agency', '{noop}123456', N'Z-Media Agency', 'contact@zmedia.vn', 'zmedia.png', 850, 1);
GO

-- [3] Gán quyền
INSERT INTO Authorities (username, role_id) VALUES 
('admin_core', 'ADMIN'),
('admin_core', 'USER'),
('minh_travel', 'USER'),
('lan_chill', 'USER'),
('zmedia_agency', 'USER');
GO

-- [4] Gói cước kinh doanh (Định giá theo thị trường)
INSERT INTO Packages (name, tokens, price, description) VALUES
(N'Gói Trải Nghiệm', 50, 29000, N'Dành cho người mới bắt đầu sáng tạo nội dung'),
(N'Gói Creator (Bán chạy)', 200, 99000, N'Phù hợp cho Vlogger, Tiktoker ra video hàng tuần'),
(N'Gói Agency Pro', 1000, 399000, N'Dành cho doanh nghiệp, xuất file .WAV chất lượng cao');
GO

-- [5] Kho nhạc AI (Bao gồm cả tính năng Remix)
-- ID 1, 2, 3 là bài gốc. ID 4 là bản remix của bài 1.
INSERT INTO Songs (title, prompt, audio_url, status, is_public, lyrics, model_ver, is_remix, parent_id, username) VALUES 
(N'Bình minh Tây Bắc', N'Nhạc cinematic hoành tráng, âm hưởng dân tộc miền núi phía Bắc, có tiếng sáo mèo, dùng cho video flycam', 'https://cdn.musicai.vn/audio/taybac-flycam.mp3', 'COMPLETED', 1, NULL, 'sonic-v4', 0, NULL, 'minh_travel'),
(N'Đêm mưa Sài Gòn', N'Nhạc Lofi chill, nhịp tempo chậm 70bpm, có tiếng mưa rơi lất phất và tiếng lật trang sách', 'https://cdn.musicai.vn/audio/lofi-rain.mp3', 'COMPLETED', 1, NULL, 'sonic-v3.5', 0, NULL, 'lan_chill'),
(N'Mega Sale 11.11', N'Nhạc EDM House giật beat mạnh, tiết tấu nhanh dồn dập, tạo cảm giác hối thúc chốt đơn', NULL, 'PENDING', 0, NULL, 'sonic-v4', 0, NULL, 'zmedia_agency'),
(N'Bình minh Tây Bắc (Lofi Remix)', N'Phối lại theo phong cách Lofi chill từ bản gốc, giữ lại tiếng sáo mèo nhưng đổi beat trống sang hiphop lofi', 'https://cdn.musicai.vn/audio/taybac-lofi-remix.mp3', 'COMPLETED', 1, NULL, 'sonic-v4', 1, 1, 'lan_chill');
GO

-- [6] Gắn thẻ phân loại nhạc (SEO & Tìm kiếm)
INSERT INTO Song_Tags (song_id, tag) VALUES
(1, N'Cinematic'),
(1, N'Travel'),
(2, N'Lofi'),
(2, N'Podcast'),
(3, N'EDM'),
(3, N'Commercial'),
(4, N'Lofi'),
(4, N'Remix');
GO

-- [7] Danh sách phát cá nhân
INSERT INTO Playlists (name, is_public, username) VALUES
(N'Nhạc nền Flycam 2026', 1, 'minh_travel'),
(N'Nhạc đọc truyện đêm khuya', 1, 'lan_chill'),
(N'Kho nhạc chạy Ads Tiktok', 0, 'zmedia_agency');
GO

-- [8] Thêm bài hát vào danh sách phát
INSERT INTO Playlist_Songs (playlist_id, song_id, sort_order) VALUES
(1, 1, 1),
(2, 2, 1),
(2, 4, 2),
(3, 3, 1);
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
('zmedia_agency', 1000, N'Nạp thành công gói Agency Pro'),
('minh_travel', 50, N'Nạp thành công gói Trải Nghiệm');
GO

-- [10] Hóa đơn thanh toán thực tế (Orders)
INSERT INTO Orders (order_code, total_price, status, username, package_id) VALUES
('MOMO_987234XN', 399000, 'SUCCESS', 'zmedia_agency', 3),
('VNPAY_459123BC', 29000, 'SUCCESS', 'minh_travel', 1),
('ZALOPAY_7749PO', 99000, 'PENDING', 'lan_chill', 2);
GO

-- [11] Tương tác xã hội: Bình luận
INSERT INTO Song_Comments (song_id, username, content) VALUES
(1, 'lan_chill', N'Đoạn điệp khúc dùng sáo mèo nghe nổi da gà luôn anh Minh ơi. Cho em xin phép remix lại bản này sang Lofi nhé!'),
(4, 'minh_travel', N'Bản remix nghe cuốn quá, đúng chất ngồi chill ban đêm.'),
(2, 'zmedia_agency', N'Bạn có nhận làm nhạc độc quyền cho nhãn hàng không? Liên hệ mình nhé.');
GO

-- [12] Tương tác xã hội: Thả tim
INSERT INTO Favorites (username, song_id) VALUES
('lan_chill', 1),
('zmedia_agency', 1),
('minh_travel', 4),
('zmedia_agency', 2);
GO

-- [13] Tương tác xã hội: Theo dõi (Follows)
INSERT INTO Follows (follower, following) VALUES
('lan_chill', 'minh_travel'),
('zmedia_agency', 'lan_chill'),
('zmedia_agency', 'minh_travel');
GO

-- [14] Hệ thống thông báo tự động
INSERT INTO Notifications (username, type, content, ref_id) VALUES
('minh_travel', 'NEW_COMMENT', N'lan_chill đã bình luận về bài hát "Bình minh Tây Bắc" của bạn', 1),
('lan_chill', 'SONG_COMPLETED', N'Bản nhạc "Đêm mưa Sài Gòn" của bạn đã tạo xong. Nghe ngay!', 2),
('minh_travel', 'NEW_REMIX', N'lan_chill vừa remix lại bài hát "Bình minh Tây Bắc" của bạn', 4),
('zmedia_agency', 'PAYMENT_SUCCESS', N'Thanh toán thành công 399.000đ. Đã cộng 1000 Token vào tài khoản.', NULL);
GO

-- =============================================
-- 6. KIỂM TRA LẠI DỮ LIỆU
-- =============================================
SELECT 'Users' AS TableName, COUNT(*) AS TotalRows FROM Users;
SELECT 'Songs' AS TableName, COUNT(*) AS TotalRows FROM Songs;
SELECT 'Packages' AS TableName, COUNT(*) AS TotalRows FROM Packages;
SELECT 'Orders' AS TableName, COUNT(*) AS TotalRows FROM Orders;
SELECT 'Playlists' AS TableName, COUNT(*) AS TotalRows FROM Playlists;
GO

-- =============================================
-- 1. PHÂN HỆ AUTH & USERS (Tài khoản & Phân quyền)
-- =============================================
SELECT * FROM Users;
SELECT * FROM Roles;
SELECT * FROM Authorities;

-- =============================================
-- 2. PHÂN HỆ MUSIC ENGINE (Lõi sinh nhạc)
-- =============================================
SELECT * FROM Songs;
SELECT * FROM Song_Tags;

-- =============================================
-- 3. PHÂN HỆ FINANCE (Tài chính & Giao dịch)
-- =============================================
SELECT * FROM Transactions;
SELECT * FROM Packages;
SELECT * FROM Orders;

-- =============================================
-- 4. PHÂN HỆ LIBRARY & SOCIAL (Thư viện & Mạng xã hội)
-- =============================================
SELECT * FROM Playlists;
SELECT * FROM Playlist_Songs;
SELECT * FROM Favorites;
SELECT * FROM Song_Comments;
SELECT * FROM Follows;
SELECT * FROM Notifications;
ALTER TABLE Songs ADD image_url VARCHAR(500) NULL;