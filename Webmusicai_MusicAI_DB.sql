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
    -- 4. TẠO CÁC BẢNG (TABLES) - TỔNG 14 BẢNG
    -- =============================================

    -- [1] Users (Đã thêm account_tier và pro_expired_at)
    CREATE TABLE Users (
        username VARCHAR(50) PRIMARY KEY,
        password VARCHAR(100) NOT NULL,
        fullname NVARCHAR(100) NOT NULL,
        email VARCHAR(100) NULL,
        photo VARCHAR(255) NULL,
        token_balance INT DEFAULT 0,
        enabled BIT DEFAULT 1,
        account_tier VARCHAR(20) DEFAULT 'BASIC',
        pro_expired_at DATETIME NULL
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

    -- [4] Songs (ĐÃ BỎ image_url theo yêu cầu)
    CREATE TABLE Songs (
        id INT IDENTITY(1,1) PRIMARY KEY,
        title NVARCHAR(255) NOT NULL,
        prompt NVARCHAR(MAX) NOT NULL,
        audio_url VARCHAR(500) NULL,
        status VARCHAR(20) NOT NULL,
        is_public BIT DEFAULT 0,
        lyrics NVARCHAR(MAX) NULL,
        model_ver VARCHAR(20) NULL,
        is_remix BIT DEFAULT 0,
        parent_id INT NULL,
        cover_url   VARCHAR(500) NULL,
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

    -- [6] Packages (Đã thêm old_price và badge)
    CREATE TABLE Packages (
        id INT IDENTITY(1,1) PRIMARY KEY,
        name NVARCHAR(100) NOT NULL,
        tokens INT NOT NULL,
        price INT NOT NULL,
        old_price INT NULL,
        badge NVARCHAR(50) NULL,
        description NVARCHAR(255) NULL
    );
    GO

    -- [7] Orders
    CREATE TABLE Orders (
        id INT IDENTITY(1,1) PRIMARY KEY,
        order_code VARCHAR(50) UNIQUE NOT NULL,
        total_price INT NOT NULL,
        status VARCHAR(20) NOT NULL,
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
        FOREIGN KEY (song_id) REFERENCES Songs(id)
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
        FOREIGN KEY (song_id) REFERENCES Songs(id)
    );
    GO

    -- [11] Favorites
    CREATE TABLE Favorites (
        id INT IDENTITY(1,1) PRIMARY KEY,
        username VARCHAR(50) NOT NULL,
        song_id INT NOT NULL,
        created_at DATETIME DEFAULT GETDATE(),
        FOREIGN KEY (username) REFERENCES Users(username),
        FOREIGN KEY (song_id) REFERENCES Songs(id)
    );
    GO

    -- [12] Song_Comments
    CREATE TABLE Song_Comments (
        id INT IDENTITY(1,1) PRIMARY KEY,
        song_id INT NOT NULL,
        username VARCHAR(50) NOT NULL,
        content NVARCHAR(500) NOT NULL,
        parent_id INT NULL,
        created_at DATETIME DEFAULT GETDATE(),
        FOREIGN KEY (song_id) REFERENCES Songs(id),
        FOREIGN KEY (username) REFERENCES Users(username),
        FOREIGN KEY (parent_id) REFERENCES Song_Comments(id)
    );
    GO

    -- [13] Follows
    CREATE TABLE Follows (
        id INT IDENTITY(1,1) PRIMARY KEY,
        follower VARCHAR(50) NOT NULL,
        following VARCHAR(50) NOT NULL,
        created_at DATETIME DEFAULT GETDATE(),
        FOREIGN KEY (follower) REFERENCES Users(username),
        FOREIGN KEY (following) REFERENCES Users(username)
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
    ('admin_core', '{noop}admin2026', N'System Admin', 'admin@musicai.vn', 'admin_avatar.png', 9999, 1, 'BASIC', NULL),
    ('minh_travel', '{noop}123456', N'Minh Xê Dịch', 'minh.vlog@gmail.com', 'minh.png', 15, 1, 'BASIC', NULL),
    ('lan_chill', '{noop}123456', N'Lan ASMR', 'lan.podcast@yahoo.com', 'lan.png', 50, 1, 'PRO', '2027-12-31'),
    ('zmedia_agency', '{noop}123456', N'Z-Media Agency', 'contact@zmedia.vn', 'zmedia.png', 850, 1, 'PRO', '2027-06-01'),
    ('vy_expired', '{noop}123456', N'Hải Vy', 'haivy.kts@gmail.com', 'vy.png', 12, 1, 'BASIC', '2026-05-15');
    GO

    -- [3] Gán quyền
    INSERT INTO Authorities (username, role_id) VALUES
    ('admin_core', 'ADMIN'), ('admin_core', 'USER'),
    ('minh_travel', 'USER'), ('lan_chill', 'USER'),
    ('zmedia_agency', 'USER'), ('vy_expired', 'USER');
    GO

    -- [4] Gói cước kinh doanh
    INSERT INTO Packages (name, tokens, price, old_price, badge, description) VALUES
    (N'Gói Trải Nghiệm', 50, 29000, NULL, NULL, N'Dành cho người mới bắt đầu sáng tạo nội dung'),
    (N'Gói Creator (Bán chạy)', 200, 99000, 150000, N'🔥 Bán chạy nhất', N'Phù hợp cho Vlogger, Tiktoker ra video hàng tuần'),
    (N'Gói Agency Pro', 1000, 399000, 750000, N'💎 Tiết kiệm 45%', N'Dành cho doanh nghiệp, xuất file .WAV chất lượng cao');
    GO

    -- [5] Kho nhạc AI (ĐÃ BỎ image_url trong INSERT)
    INSERT INTO Songs (title, prompt, audio_url, status, is_public, lyrics, model_ver, is_remix, parent_id, username) VALUES
    (N'Bình minh Tây Bắc', N'Nhạc cinematic hoành tráng', 'https://cdn.musicai.vn/audio/taybac-flycam.mp3', 'COMPLETED', 1, NULL, 'sonic-v4', 0, NULL, 'minh_travel'),
    (N'Đêm mưa Sài Gòn', N'Nhạc Lofi chill, chậm', 'https://cdn.musicai.vn/audio/lofi-rain.mp3', 'COMPLETED', 1, NULL, 'sonic-v3.5', 0, NULL, 'lan_chill'),
    (N'Mega Sale 11.11', N'Nhạc EDM House giật beat', NULL, 'PENDING', 0, NULL, 'sonic-v4', 0, NULL, 'zmedia_agency'),
    (N'Bình minh Tây Bắc (Lofi Remix)', N'Phối lại Lofi', 'https://cdn.musicai.vn/audio/taybac-lofi-remix.mp3', 'COMPLETED', 1, NULL, 'sonic-v4', 1, 1, 'lan_chill'),
    (N'Kịch bản Tết', N'Nhạc vui tươi, hào hùng', 'https://cdn.musicai.vn/audio/tet-agency.mp3', 'COMPLETED', 0, NULL, 'sonic-v4', 0, NULL, 'vy_expired');
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
    ('zmedia_agency', 1000, N'Nạp thành công gói Agency Pro'),
    ('minh_travel', 50, N'Nạp thành công gói Trải Nghiệm'),
    ('vy_expired', 300, N'Mua gói Creator (Giao dịch cũ)'),
    ('vy_expired', -288, N'Đã tiêu hao token lúc còn hạn VIP');
    GO

    -- [10] Hóa đơn thanh toán thực tế (Orders)
    INSERT INTO Orders (order_code, total_price, status, username, package_id) VALUES
    ('MOMO_987234XN', 399000, 'SUCCESS', 'zmedia_agency', 3),
    ('VNPAY_459123BC', 29000, 'SUCCESS', 'minh_travel', 1),
    ('ZALOPAY_7749PO', 99000, 'PENDING', 'lan_chill', 2),
    ('MOMO_OLD_VY', 99000, 'SUCCESS', 'vy_expired', 2);
    GO

    -- [11] Tương tác xã hội: Bình luận
    INSERT INTO Song_Comments (song_id, username, content) VALUES
    (1, 'lan_chill', N'Đoạn điệp khúc nghe hay quá. Cho em remix nhé!'),
    (4, 'minh_travel', N'Bản remix cuốn quá, đúng chất.'),
    (2, 'zmedia_agency', N'Bạn có nhận làm nhạc độc quyền không?');
    GO

    -- [12] Tương tác xã hội: Thả tim
    INSERT INTO Favorites (username, song_id) VALUES
    ('lan_chill', 1), ('zmedia_agency', 1), ('minh_travel', 4), ('zmedia_agency', 2);
    GO

    -- [13] Tương tác xã hội: Theo dõi (Follows)
    INSERT INTO Follows (follower, following) VALUES
    ('lan_chill', 'minh_travel'), ('zmedia_agency', 'lan_chill'), ('zmedia_agency', 'minh_travel');
    GO

    -- [14] Hệ thống thông báo tự động
    INSERT INTO Notifications (username, type, content, ref_id) VALUES
    ('minh_travel', 'NEW_COMMENT', N'lan_chill đã bình luận', 1),
    ('lan_chill', 'SONG_COMPLETED', N'Bản nhạc đã tạo xong', 2),
    ('minh_travel', 'NEW_REMIX', N'lan_chill vừa remix lại bài hát', 4),
    ('zmedia_agency', 'PAYMENT_SUCCESS', N'Thanh toán thành công', NULL);
    GO

    -- =============================================
    -- 6. KIỂM TRA LẠI DỮ LIỆU
    -- =============================================
    SELECT 'Users' AS TableName, COUNT(*) AS TotalRows FROM Users;
    SELECT 'Songs' AS TableName, COUNT(*) AS TotalRows FROM Songs;
    SELECT 'Packages' AS TableName, COUNT(*) AS TotalRows FROM Packages;
    SELECT 'Orders' AS TableName, COUNT(*) AS TotalRows FROM Orders;
    SELECT 'Playlists' AS TableName, COUNT(*) AS TotalRows FROM Playlists;
    select * from Genres
    GO
    Select * from Songs
    GO

    CREATE TABLE Genres (
        id          INT IDENTITY(1,1) PRIMARY KEY,
        name        NVARCHAR(50) NOT NULL UNIQUE,
        description NVARCHAR(255) NULL,
        created_at  DATETIME DEFAULT GETDATE()
    );
    GO
    CREATE TABLE SongGenres(
        song_id INT,
        genre_id INT,

        PRIMARY KEY(song_id, genre_id),

        FOREIGN KEY(song_id) REFERENCES Songs(id),
        FOREIGN KEY(genre_id) REFERENCES Genres(id)
    );

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
    INSERT INTO SongGenres (song_id, genre_id) VALUES
    -- Bình minh Tây Bắc
    (1, 2), -- Cinematic
    (1, 6), -- Folk

    -- Đêm mưa Sài Gòn
    (2, 1), -- Lofi
    (2, 7), -- Jazz

    -- Mega Sale 11.11
    (3, 4), -- EDM

    -- Bình minh Tây Bắc (Lofi Remix)
    (4, 1), -- Lofi
    (4, 6), -- Folk

    -- Kịch bản Tết
    (5, 5), -- Acoustic
    (5, 6); -- Folk
    GO
    -- =============================================
    -- 7. BẢNG ALBUMS + ALBUM_SONGS (CRUD ALBUM)
    -- =============================================
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

    CREATE TABLE Album_Songs (
        id           INT IDENTITY(1,1) PRIMARY KEY,
        album_id     INT NOT NULL,
        song_id      INT NOT NULL,
        track_number INT DEFAULT 0,
        FOREIGN KEY (album_id) REFERENCES Albums(id),
        FOREIGN KEY (song_id) REFERENCES Songs(id)
    );
    GO

    -- Seed Album mẫu
    INSERT INTO Albums (title, description, cover_url, username) VALUES
    (N'Chill Việt 2026', N'Tuyển tập nhạc Lofi Việt Nam hay nhất 2026', 'https://cdn.musicai.vn/covers/chill-viet-2026.jpg', 'lan_chill'),
    (N'Nhạc nền Flycam', N'Nhạc cinematic cho video flycam du lịch', 'https://cdn.musicai.vn/covers/flycam.jpg', 'minh_travel');
    GO

    INSERT INTO Album_Songs (album_id, song_id, track_number) VALUES
    (1, 2, 1), (1, 4, 2),
    (2, 1, 1);
    GO

    SELECT TABLE_NAME
    FROM INFORMATION_SCHEMA.TABLES;


	-- chạy thêm dòng này 
	USE MusicAI_DB;
GO

ALTER TABLE Songs ALTER COLUMN audio_url VARCHAR(MAX);
GO

--thêm đoạn này cho phần comment
USE MusicAI_DB;
GO

ALTER TABLE Song_Comments
ADD parent_id INT NULL;
GO

ALTER TABLE Song_Comments
ADD CONSTRAINT FK_SongComments_Parent
FOREIGN KEY (parent_id) REFERENCES Song_Comments(id);
GO

INSERT INTO Song_Comments (song_id, username, content, parent_id) 
VALUES (1, 'minh_travel', N'Cảm ơn bạn, cứ tự nhiên nhé!', 1);
GO

IF NOT EXISTS (SELECT * FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'Songs' AND COLUMN_NAME = 'cover_url')
BEGIN
    ALTER TABLE Songs ADD cover_url VARCHAR(500) NULL;
END
GO

IF NOT EXISTS (SELECT * FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'Songs' AND COLUMN_NAME = 'listen_count')
BEGIN
    ALTER TABLE Songs ADD listen_count INT NOT NULL DEFAULT 0;
END
GO  