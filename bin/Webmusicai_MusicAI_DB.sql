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
-- 1. TẠO DATABASE VÀ CÁC BẢNG (TABLES)
-- =============================================
CREATE DATABASE MusicAI_DB;
GO
USE MusicAI_DB;
GO

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

CREATE TABLE Roles (
    id VARCHAR(20) PRIMARY KEY,
    name NVARCHAR(50) NOT NULL
);
GO

CREATE TABLE Authorities (
    id INT IDENTITY(1,1) PRIMARY KEY,
    username VARCHAR(50) NOT NULL,
    role_id VARCHAR(20) NOT NULL,
    FOREIGN KEY (username) REFERENCES Users(username),
    FOREIGN KEY (role_id) REFERENCES Roles(id)
);
GO

CREATE TABLE Songs (
    id INT IDENTITY(1,1) PRIMARY KEY,
    title NVARCHAR(255) NOT NULL,
    prompt NVARCHAR(MAX) NOT NULL,
    audio_url VARCHAR(500) NULL,
    status VARCHAR(20) NOT NULL, 
    is_public BIT DEFAULT 0,     
    created_at DATETIME DEFAULT GETDATE(),
    username VARCHAR(50) NOT NULL,
    FOREIGN KEY (username) REFERENCES Users(username)
);
GO

CREATE TABLE Transactions (
    id INT IDENTITY(1,1) PRIMARY KEY,
    username VARCHAR(50) NOT NULL,
    amount INT NOT NULL,
    description NVARCHAR(255) NULL,
    created_at DATETIME DEFAULT GETDATE(),
    FOREIGN KEY (username) REFERENCES Users(username)
);
GO

-- =============================================
-- 2. DỮ LIỆU MỒI (SEED DATA)
-- =============================================

INSERT INTO Roles (id, name) VALUES 
('USER', N'Người dùng'),
('ADMIN', N'Quản trị viên');
GO

INSERT INTO Users (username, password, fullname, email, photo, token_balance, enabled) VALUES 
('admin', '{noop}123', N'Trần Quản Trị', 'admin@gmail.com', 'admin.png', 9999, 1),
('gacon', '{noop}123', N'Gà Con Media', 'gacon@gendmedia.com', 'mascot.png', 50, 1),
('nguoithuong', '{noop}123', N'Khách Hàng Thân Thiết', 'khachhang@gmail.com', 'user.png', 0, 1);
GO

INSERT INTO Authorities (username, role_id) VALUES 
('admin', 'ADMIN'),
('admin', 'USER'),
('gacon', 'USER'),
('nguoithuong', 'USER');
GO

INSERT INTO Songs (title, prompt, audio_url, status, is_public, username) VALUES 
(N'Nhạc nền quán ăn Sài Gòn', N'Nhạc lofi chill nhẹ nhàng, có tiếng xèo xèo của đồ ăn, phù hợp làm nhạc nền video review quán ăn dọc các con đường đẹp ở thành phố Hồ Chí Minh', 'https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3', 'COMPLETED', 1, 'gacon'),
(N'Vlog Hành trình miền Tây', N'Nhạc acoustic vui tươi, mang âm hưởng miền Tây hiện đại, dồn dập, dành cho video quay ngoài trời tại khu vực Chợ Gạo Tiền Giang', 'https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3', 'COMPLETED', 1, 'gacon'),
(N'Thông điệp chân thành', N'Nhạc nền Piano trầm ấm, tạo cảm giác cinematic mood, truyền tải thông điệp đừng chạy theo view ảo mà hãy kết nối sâu sắc với khán giả', NULL, 'PENDING', 0, 'nguoithuong');
GO

INSERT INTO Transactions (username, amount, description) VALUES 
('gacon', 52, N'Đăng ký tài khoản tặng gói Khởi động'),
('admin', 9999, N'Cấp token không giới hạn cho hệ thống Admin'),
('gacon', -1, N'Tạo bài hát: Nhạc nền quán ăn Sài Gòn'),
('gacon', -1, N'Tạo bài hát: Vlog Hành trình miền Tây');
GO

-- =============================================
-- 3. KIỂM TRA LẠI DỮ LIỆU
-- =============================================
SELECT * FROM Users;
SELECT * FROM Songs;
SELECT * FROM Transactions;
GO