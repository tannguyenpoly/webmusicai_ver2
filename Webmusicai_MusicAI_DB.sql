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
-- 5. DỮ LIỆU MỒI (SEED DATA)
-- =============================================

INSERT INTO Roles (id, name) VALUES 
('USER', N'Người dùng'),
('ADMIN', N'Quản trị viên');
GO

INSERT INTO Users (username, password, fullname, email, photo, token_balance, enabled) VALUES 
('admin', '{noop}123', N'Trần Quản Trị', 'admin@gmail.com', 'admin.png', 9999, 1),
('gacon', '{noop}123', N'Gà Con Media', 'gacon@gendmedia.com', 'mascot.png', 50, 1),
('ngoctan', '{noop}123', N'Nguyễn Ngọc Tân', 'tannguyen@gmail.com', 'tan.png', 10, 1),
('camedo', '{noop}123', N'CAMEDO HOUSE', 'contact@camedo.vn', 'shop.png', 100, 1);
GO

INSERT INTO Authorities (username, role_id) VALUES 
('admin', 'ADMIN'),
('admin', 'USER'),
('gacon', 'USER'),
('ngoctan', 'USER'),
('camedo', 'USER');
GO

INSERT INTO Packages (name, tokens, price, description) VALUES
(N'Gói Khởi Động', 50, 20000, N'Phù hợp trải nghiệm tạo nhạc cơ bản'),
(N'Gói Creator TikTok', 200, 50000, N'Dành cho đội ngũ xây kênh, làm nội dung media'),
(N'Gói Pro Studio', 1000, 200000, N'Không giới hạn sáng tạo cho dự án chuyên nghiệp');
GO

INSERT INTO Songs (title, prompt, audio_url, status, is_public, model_ver, username) VALUES 
(N'Nhạc nền quán ăn Sài Gòn', N'Nhạc lofi chill nhẹ nhàng, có tiếng xèo xèo của đồ ăn, phù hợp làm nhạc nền video review quán ăn dọc các con đường đẹp ở thành phố Hồ Chí Minh', 'https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3', 'COMPLETED', 1, 'sonic-v4', 'gacon'),
(N'Nhạc năng động chốt đơn', N'Beat điện tử dồn dập, vui tươi, kích thích mua sắm, dùng làm nhạc nền cho video bán giày, thời trang', 'https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3', 'COMPLETED', 1, 'sonic-v4', 'camedo'),
(N'Thông điệp chân thành', N'Nhạc nền Piano trầm ấm, tạo cảm giác cinematic mood, truyền tải thông điệp đừng chạy theo view ảo mà hãy kết nối sâu sắc với khán giả', NULL, 'PENDING', 0, 'sonic-v3.5', 'ngoctan');
GO

INSERT INTO Song_Tags (song_id, tag) VALUES
(1, N'Lofi'),
(1, N'Food Review'),
(2, N'Tiktok Shop'),
(2, N'Sôi động');
GO

INSERT INTO Playlists (name, is_public, username) VALUES
(N'Bộ sưu tập nhạc Review HCMC', 1, 'gacon'),
(N'Nhạc chốt đơn livestream', 0, 'camedo');
GO

INSERT INTO Playlist_Songs (playlist_id, song_id, sort_order) VALUES
(1, 1, 1),
(2, 2, 1);
GO

INSERT INTO Song_Comments (song_id, username, content) VALUES
(1, 'ngoctan', N'Beat này ghép vào video có mascot gà con review ẩm thực là hết bài luôn nha!'),
(2, 'gacon', N'Xin phép remix lại bản này cho campaign sắp tới nhé shop.');
GO

INSERT INTO Transactions (username, amount, description) VALUES 
('gacon', 52, N'Đăng ký tài khoản tặng gói Khởi động'),
('admin', 9999, N'Cấp token không giới hạn cho hệ thống Admin'),
('gacon', -1, N'Tạo bài hát: Nhạc nền quán ăn Sài Gòn'),
('camedo', -1, N'Tạo bài hát: Nhạc năng động chốt đơn');
GO

INSERT INTO Orders (order_code, total_price, status, username, package_id) VALUES
('VNP123456789', 50000, 'SUCCESS', 'camedo', 2),
('MB987654321', 20000, 'PENDING', 'ngoctan', 1);
GO

INSERT INTO Notifications (username, type, content, ref_id) VALUES
('gacon', 'NEW_COMMENT', N'Nguyễn Ngọc Tân đã bình luận về bài hát của bạn', 1);
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