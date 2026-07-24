/*
  WEBMUSICAI - NÂNG CẤP DATABASE ĐANG CÓ

  - Không DROP DATABASE và không xóa tài khoản/bài hát/đơn hàng đang có.
  - Đã gộp script sửa lỗi ngày 23/07 và các migration ngày 23-24/07.
  - Có thể chạy lại nhiều lần.
  - Chạy bằng tài khoản SQL Server có quyền db_owner hoặc ALTER.

  Không chạy setup-fresh.sql trước file này nếu muốn giữ dữ liệu cũ.
*/
USE MusicAI_DB;
GO

-- payment_method trước đây bị thêm nhầm vào Songs.
IF COL_LENGTH('dbo.Orders', 'payment_method') IS NULL
BEGIN
    ALTER TABLE dbo.Orders ADD payment_method VARCHAR(20) NOT NULL
        CONSTRAINT DF_Orders_PaymentMethod DEFAULT 'VNPAY';
END
GO

IF COL_LENGTH('dbo.Songs', 'payment_method') IS NOT NULL
BEGIN
    DECLARE @songs_default SYSNAME;
    SELECT @songs_default = dc.name
    FROM sys.default_constraints dc
    JOIN sys.columns c
      ON c.object_id = dc.parent_object_id
     AND c.column_id = dc.parent_column_id
    WHERE dc.parent_object_id = OBJECT_ID('dbo.Songs')
      AND c.name = 'payment_method';

    IF @songs_default IS NOT NULL
        EXEC(N'ALTER TABLE dbo.Songs DROP CONSTRAINT ' + QUOTENAME(@songs_default));

    ALTER TABLE dbo.Songs DROP COLUMN payment_method;
END
GO

-- payment_status là cột cũ bị trùng ý nghĩa với Orders.status.
IF COL_LENGTH('dbo.Orders', 'payment_status') IS NOT NULL
BEGIN
    DECLARE @payment_status_default SYSNAME;
    SELECT @payment_status_default = dc.name
    FROM sys.default_constraints dc
    JOIN sys.columns c
      ON c.object_id = dc.parent_object_id
     AND c.column_id = dc.parent_column_id
    WHERE dc.parent_object_id = OBJECT_ID('dbo.Orders')
      AND c.name = 'payment_status';

    IF @payment_status_default IS NOT NULL
        EXEC(N'ALTER TABLE dbo.Orders DROP CONSTRAINT ' + QUOTENAME(@payment_status_default));

    ALTER TABLE dbo.Orders DROP COLUMN payment_status;
END
GO

IF OBJECT_ID('dbo.Song_Tags', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.Song_Tags (
        id INT IDENTITY(1,1) PRIMARY KEY,
        song_id INT NOT NULL,
        tag NVARCHAR(50) NOT NULL,
        FOREIGN KEY (song_id) REFERENCES dbo.Songs(id),
        CONSTRAINT UQ_SongTags_Song_Tag UNIQUE (song_id, tag)
    );
END
GO

IF OBJECT_ID('dbo.Playlist_Songs', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.Playlist_Songs (
        id INT IDENTITY(1,1) PRIMARY KEY,
        playlist_id INT NOT NULL,
        song_id INT NOT NULL,
        sort_order INT DEFAULT 0,
        FOREIGN KEY (playlist_id) REFERENCES dbo.Playlists(id),
        FOREIGN KEY (song_id) REFERENCES dbo.Songs(id),
        CONSTRAINT UQ_PlaylistSongs_Playlist_Song UNIQUE (playlist_id, song_id)
    );
END
GO

;WITH duplicates AS (
    SELECT id,
           ROW_NUMBER() OVER (PARTITION BY username, song_id ORDER BY id) AS row_number
    FROM dbo.Favorites
)
DELETE FROM duplicates WHERE row_number > 1;
GO

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE object_id = OBJECT_ID('dbo.Favorites')
      AND name IN ('UQ_Favorites_User_Song', 'UX_Favorites_UserSong')
)
    CREATE UNIQUE INDEX UX_Favorites_UserSong
        ON dbo.Favorites(username, song_id);
GO

-- Bổ sung lại seed bị bỏ qua do hai bảng chưa tồn tại.
INSERT INTO dbo.Song_Tags (song_id, tag)
SELECT seed.song_id, seed.tag
FROM (VALUES
    (1, N'Cinematic'), (1, N'Travel'),
    (2, N'Lofi'), (2, N'Podcast'),
    (3, N'EDM'), (3, N'Commercial'),
    (4, N'Lofi'), (4, N'Remix'),
    (5, N'Corporate')
) seed(song_id, tag)
WHERE EXISTS (SELECT 1 FROM dbo.Songs s WHERE s.id = seed.song_id)
  AND NOT EXISTS (
      SELECT 1 FROM dbo.Song_Tags st
      WHERE st.song_id = seed.song_id AND st.tag = seed.tag
  );
GO

INSERT INTO dbo.Playlist_Songs (playlist_id, song_id, sort_order)
SELECT seed.playlist_id, seed.song_id, seed.sort_order
FROM (VALUES
    (1, 1, 1),
    (2, 2, 1),
    (2, 4, 2),
    (3, 3, 1)
) seed(playlist_id, song_id, sort_order)
WHERE EXISTS (SELECT 1 FROM dbo.Playlists p WHERE p.id = seed.playlist_id)
  AND EXISTS (SELECT 1 FROM dbo.Songs s WHERE s.id = seed.song_id)
  AND NOT EXISTS (
      SELECT 1 FROM dbo.Playlist_Songs ps
      WHERE ps.playlist_id = seed.playlist_id
        AND ps.song_id = seed.song_id
  );
GO

SELECT
    COL_LENGTH('dbo.Orders', 'payment_method') AS OrdersPaymentMethodExists,
    COL_LENGTH('dbo.Songs', 'payment_method') AS WrongSongsPaymentMethod,
    OBJECT_ID('dbo.Song_Tags', 'U') AS SongTagsTableId,
    OBJECT_ID('dbo.Playlist_Songs', 'U') AS PlaylistSongsTableId;

SELECT COUNT(*) AS SongTagRows FROM dbo.Song_Tags;
SELECT COUNT(*) AS PlaylistSongRows FROM dbo.Playlist_Songs;
GO

-- =====================================================================
-- PHẦN 2: CẤU TRÚC DEMO, JWT, PRESENCE, BẠN BÈ VÀ THANH TOÁN
-- =====================================================================

IF COL_LENGTH('dbo.Users', 'token_version') IS NULL
    ALTER TABLE dbo.Users ADD token_version INT NOT NULL
        CONSTRAINT DF_Users_TokenVersion DEFAULT 0;
GO

IF COL_LENGTH('dbo.Users', 'last_seen_at') IS NULL
    ALTER TABLE dbo.Users ADD last_seen_at DATETIME NULL;
GO

IF COL_LENGTH('dbo.Orders', 'payment_method') IS NULL
    ALTER TABLE dbo.Orders ADD payment_method VARCHAR(20) NOT NULL
        CONSTRAINT DF_Orders_PaymentMethod DEFAULT 'VNPAY';
GO

IF OBJECT_ID('dbo.Friendships', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.Friendships (
        id INT IDENTITY(1,1) PRIMARY KEY,
        requester VARCHAR(50) NOT NULL,
        addressee VARCHAR(50) NOT NULL,
        status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
        created_at DATETIME DEFAULT GETDATE(),
        responded_at DATETIME NULL,
        user_low AS (CASE WHEN requester < addressee THEN requester ELSE addressee END) PERSISTED,
        user_high AS (CASE WHEN requester < addressee THEN addressee ELSE requester END) PERSISTED,
        FOREIGN KEY (requester) REFERENCES dbo.Users(username),
        FOREIGN KEY (addressee) REFERENCES dbo.Users(username),
        CONSTRAINT CK_Friendships_NotSelf CHECK (requester <> addressee),
        CONSTRAINT CK_Friendships_Status CHECK (status IN ('PENDING', 'ACCEPTED'))
    );
END
GO

IF COL_LENGTH('dbo.Friendships', 'user_low') IS NULL
    ALTER TABLE dbo.Friendships ADD
        user_low AS (CASE WHEN requester < addressee THEN requester ELSE addressee END) PERSISTED;
GO

IF COL_LENGTH('dbo.Friendships', 'user_high') IS NULL
    ALTER TABLE dbo.Friendships ADD
        user_high AS (CASE WHEN requester < addressee THEN addressee ELSE requester END) PERSISTED;
GO

;WITH duplicate_pairs AS (
    SELECT id,
           ROW_NUMBER() OVER (
               PARTITION BY user_low, user_high
               ORDER BY CASE WHEN status = 'ACCEPTED' THEN 0 ELSE 1 END, id
           ) AS row_number
    FROM dbo.Friendships
)
DELETE FROM duplicate_pairs WHERE row_number > 1;
GO

IF EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE name = 'UX_Friendships_Pair'
      AND object_id = OBJECT_ID('dbo.Friendships')
)
    DROP INDEX UX_Friendships_Pair ON dbo.Friendships;
GO

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE name IN ('UQ_Friendships_Pair', 'UX_Friendships_UnorderedPair')
      AND object_id = OBJECT_ID('dbo.Friendships')
)
    CREATE UNIQUE INDEX UX_Friendships_UnorderedPair
        ON dbo.Friendships(user_low, user_high);
GO

IF OBJECT_ID('dbo.Payment_Logs', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.Payment_Logs (
        id INT IDENTITY(1,1) PRIMARY KEY,
        order_code VARCHAR(50),
        gateway_name VARCHAR(20),
        transaction_id VARCHAR(100) NOT NULL UNIQUE,
        amount INT,
        content NVARCHAR(MAX),
        created_at DATETIME DEFAULT GETDATE()
    );
END
ELSE IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE name IN ('UQ_PaymentLogs_Transaction', 'UX_PaymentLogs_TransactionId')
      AND object_id = OBJECT_ID('dbo.Payment_Logs')
)
    CREATE UNIQUE INDEX UX_PaymentLogs_TransactionId
        ON dbo.Payment_Logs(transaction_id)
        WHERE transaction_id IS NOT NULL;
GO

;WITH duplicates AS (
    SELECT id, ROW_NUMBER() OVER (PARTITION BY username, song_id ORDER BY id) AS row_number
    FROM dbo.Favorites
)
DELETE FROM duplicates WHERE row_number > 1;
GO

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE name IN ('UQ_Favorites_User_Song', 'UX_Favorites_UserSong')
      AND object_id = OBJECT_ID('dbo.Favorites')
)
    CREATE UNIQUE INDEX UX_Favorites_UserSong ON dbo.Favorites(username, song_id);
GO

;WITH duplicates AS (
    SELECT id, ROW_NUMBER() OVER (PARTITION BY follower, following ORDER BY id) AS row_number
    FROM dbo.Follows
)
DELETE FROM duplicates WHERE row_number > 1;
GO

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE name IN ('UQ_Follows_Pair', 'UX_Follows_Pair')
      AND object_id = OBJECT_ID('dbo.Follows')
)
    CREATE UNIQUE INDEX UX_Follows_Pair ON dbo.Follows(follower, following);
GO

IF OBJECT_ID('dbo.Notifications', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.Notifications (
        id INT IDENTITY(1,1) PRIMARY KEY,
        username VARCHAR(50) NOT NULL,
        type VARCHAR(50) NOT NULL,
        content NVARCHAR(255) NOT NULL,
        is_read BIT NOT NULL DEFAULT 0,
        ref_id INT NULL,
        created_at DATETIME NOT NULL DEFAULT GETDATE(),
        FOREIGN KEY (username) REFERENCES dbo.Users(username)
    );
END
GO

;WITH duplicates AS (
    SELECT id,
           ROW_NUMBER() OVER (
               PARTITION BY username, type, ref_id
               ORDER BY id
           ) AS row_number
    FROM dbo.Notifications
    WHERE ref_id IS NOT NULL
)
DELETE FROM duplicates WHERE row_number > 1;
GO

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE name = 'UX_Notifications_UserTypeRef'
      AND object_id = OBJECT_ID('dbo.Notifications')
)
    CREATE UNIQUE INDEX UX_Notifications_UserTypeRef
        ON dbo.Notifications(username, type, ref_id)
        WHERE ref_id IS NOT NULL;
GO

;WITH duplicates AS (
    SELECT id, ROW_NUMBER() OVER (PARTITION BY playlist_id, song_id ORDER BY id) AS row_number
    FROM dbo.Playlist_Songs
)
DELETE FROM duplicates WHERE row_number > 1;
GO

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE name IN ('UQ_PlaylistSongs_Playlist_Song', 'UX_PlaylistSongs_Pair')
      AND object_id = OBJECT_ID('dbo.Playlist_Songs')
)
    CREATE UNIQUE INDEX UX_PlaylistSongs_Pair ON dbo.Playlist_Songs(playlist_id, song_id);
GO

;WITH duplicates AS (
    SELECT id, ROW_NUMBER() OVER (PARTITION BY album_id, song_id ORDER BY id) AS row_number
    FROM dbo.Album_Songs
)
DELETE FROM duplicates WHERE row_number > 1;
GO

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE name IN ('UQ_AlbumSongs_Album_Song', 'UX_AlbumSongs_Pair')
      AND object_id = OBJECT_ID('dbo.Album_Songs')
)
    CREATE UNIQUE INDEX UX_AlbumSongs_Pair ON dbo.Album_Songs(album_id, song_id);
GO

;WITH duplicates AS (
    SELECT id, ROW_NUMBER() OVER (PARTITION BY username, role_id ORDER BY id) AS row_number
    FROM dbo.Authorities
)
DELETE FROM duplicates WHERE row_number > 1;
GO

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE name IN ('UQ_Authorities_User_Role', 'UX_Authorities_UserRole')
      AND object_id = OBJECT_ID('dbo.Authorities')
)
    CREATE UNIQUE INDEX UX_Authorities_UserRole ON dbo.Authorities(username, role_id);
GO

-- =====================================================================
-- PHẦN 3: TÀI KHOẢN, 15 TOKEN MIỄN PHÍ VÀ BA GÓI DỊCH VỤ
-- =====================================================================

IF COL_LENGTH('dbo.Users', 'auth_provider') IS NULL
    ALTER TABLE dbo.Users ADD auth_provider VARCHAR(20) NOT NULL
        CONSTRAINT DF_Users_AuthProvider DEFAULT 'LOCAL';
GO

-- OAuth hiện dùng email làm username. Nhận diện dữ liệu Google cũ.
UPDATE dbo.Users
SET auth_provider = 'GOOGLE'
WHERE email IS NOT NULL
  AND LOWER(LTRIM(RTRIM(username))) = LOWER(LTRIM(RTRIM(email)))
  AND auth_provider = 'LOCAL';
GO

UPDATE dbo.Users
SET account_tier = 'FREE'
WHERE account_tier IS NULL OR account_tier = '' OR account_tier = 'BASIC';
GO

-- Không làm giảm tài khoản đang có nhiều hơn 15 token.
UPDATE dbo.Users
SET token_balance = 15
WHERE account_tier = 'FREE' AND ISNULL(token_balance, 0) < 15;
GO

IF COL_LENGTH('dbo.Packages', 'tier_code') IS NULL
    ALTER TABLE dbo.Packages ADD tier_code VARCHAR(20) NOT NULL
        CONSTRAINT DF_Packages_TierCode DEFAULT 'CREATOR';
GO

IF COL_LENGTH('dbo.Packages', 'duration_days') IS NULL
    ALTER TABLE dbo.Packages ADD duration_days INT NOT NULL
        CONSTRAINT DF_Packages_DurationDays DEFAULT 30;
GO

-- Giá nhỏ để nhóm trình diễn chuyển khoản thật. Mỗi lần tạo/remix tốn 1 token.
IF EXISTS (SELECT 1 FROM dbo.Packages WHERE id = 1)
    UPDATE dbo.Packages
    SET name = N'Nhà sáng tạo', tokens = 45, price = 3000,
        old_price = NULL, badge = NULL, tier_code = 'CREATOR', duration_days = 30,
        description = N'45 token, tạo nhạc riêng tư và sử dụng đầy đủ thư viện trong 30 ngày'
    WHERE id = 1;
ELSE
    INSERT INTO dbo.Packages (name, tokens, price, old_price, badge, description, tier_code, duration_days)
    VALUES (N'Nhà sáng tạo', 45, 3000, NULL, NULL,
            N'45 token, tạo nhạc riêng tư và sử dụng đầy đủ thư viện trong 30 ngày', 'CREATOR', 30);
GO

IF EXISTS (SELECT 1 FROM dbo.Packages WHERE id = 2)
    UPDATE dbo.Packages
    SET name = N'Chuyên nghiệp', tokens = 120, price = 5000,
        old_price = NULL, badge = N'Phổ biến', tier_code = 'PRO', duration_days = 30,
        description = N'120 token, phù hợp người sáng tạo nội dung thường xuyên trong 30 ngày'
    WHERE id = 2;
ELSE
    INSERT INTO dbo.Packages (name, tokens, price, old_price, badge, description, tier_code, duration_days)
    VALUES (N'Chuyên nghiệp', 120, 5000, NULL, N'Phổ biến',
            N'120 token, phù hợp người sáng tạo nội dung thường xuyên trong 30 ngày', 'PRO', 30);
GO

IF EXISTS (SELECT 1 FROM dbo.Packages WHERE id = 3)
    UPDATE dbo.Packages
    SET name = N'Phòng thu', tokens = 300, price = 10000,
        old_price = NULL, badge = N'Nhiều token nhất', tier_code = 'STUDIO', duration_days = 30,
        description = N'300 token, phù hợp nhóm sản xuất và trình diễn toàn bộ tính năng trong 30 ngày'
    WHERE id = 3;
ELSE
    INSERT INTO dbo.Packages (name, tokens, price, old_price, badge, description, tier_code, duration_days)
    VALUES (N'Phòng thu', 300, 10000, NULL, N'Nhiều token nhất',
            N'300 token, phù hợp nhóm sản xuất và trình diễn toàn bộ tính năng trong 30 ngày', 'STUDIO', 30);
GO

PRINT N'Đã nâng cấp MusicAI_DB thành công. Dữ liệu cũ được giữ nguyên.';
GO
