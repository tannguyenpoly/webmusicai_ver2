CREATE DATABASE MusicAI_DB;
GO
USE MusicAI_DB;
GO

-- =============================================
-- 1. TẠO CÁC BẢNG (TABLES)
-- =============================================

CREATE TABLE Users (
    username VARCHAR(50) PRIMARY KEY,
    password VARCHAR(100) NOT NULL,
    fullname NVARCHAR(100) NOT NULL,
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

INSERT INTO Users (username, password, fullname, photo, token_balance, enabled) VALUES 
('admin@gmail.com', '{noop}123', N'Trần Quản Trị', 'admin.png', 9999, 1),
('user1@gmail.com', '{noop}123', N'Nguyễn Khách Hàng', 'user1.png', 10, 1),
('user2@gmail.com', '{noop}123', N'Lê Tân Binh', 'user2.png', 0, 1);
GO

INSERT INTO Authorities (username, role_id) VALUES 
('admin@gmail.com', 'ADMIN'),
('admin@gmail.com', 'USER'),
('user1@gmail.com', 'USER'),
('user2@gmail.com', 'USER');
GO

INSERT INTO Songs (title, prompt, audio_url, status, is_public, username) VALUES 
(N'Bản tình ca mùa thu', N'Tạo một bài hát lofi chill về mùa thu Hà Nội', 'https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3', 'COMPLETED', 1, 'user1@gmail.com'),
(N'Rock xuyên màn đêm', N'Nhạc rock giật gân, trống đập mạnh', 'https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3', 'COMPLETED', 1, 'user1@gmail.com'),
(N'Đang xử lý - Bài hát 3', N'Nhạc edm sôi động đi bar', NULL, 'PENDING', 0, 'user2@gmail.com');
GO

INSERT INTO Transactions (username, amount, description) VALUES 
('user1@gmail.com', 10, N'Đăng ký tài khoản tặng 10 token'),
('admin@gmail.com', 9999, N'Cấp token không giới hạn cho Admin'),
('user1@gmail.com', -1, N'Tạo bài hát #1'),
('user1@gmail.com', -1, N'Tạo bài hát #2');
GO
ALTER TABLE Users
ADD email VARCHAR(100) NULL;

SELECT * FROM Songs
SELECT * FROM Transactions