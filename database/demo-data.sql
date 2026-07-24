USE MusicAI_DB;
GO

/*
  Dữ liệu mẫu tùy chọn.
  Chạy sau setup-fresh.sql hoặc upgrade-existing.sql.
  Script có thể chạy lại: chỉ bổ sung dữ liệu demo còn thiếu.
  Mật khẩu của các tài khoản demo: 123456
*/

IF NOT EXISTS (SELECT 1 FROM dbo.Users WHERE username = 'demo_lofi')
    INSERT INTO dbo.Users
        (username, password, fullname, email, photo, token_balance, enabled, account_tier, token_version)
    VALUES
        ('demo_lofi', '{noop}123456', N'An Lofi', 'demo.lofi@musicai.local', NULL, 20, 1, 'FREE', 0);
GO

IF NOT EXISTS (SELECT 1 FROM dbo.Users WHERE username = 'demo_rock')
    INSERT INTO dbo.Users
        (username, password, fullname, email, photo, token_balance, enabled, account_tier, token_version)
    VALUES
        ('demo_rock', '{noop}123456', N'Bảo Rock', 'demo.rock@musicai.local', NULL, 20, 1, 'FREE', 0);
GO

IF NOT EXISTS (SELECT 1 FROM dbo.Users WHERE username = 'demo_piano')
    INSERT INTO dbo.Users
        (username, password, fullname, email, photo, token_balance, enabled, account_tier, token_version)
    VALUES
        ('demo_piano', '{noop}123456', N'Chi Piano', 'demo.piano@musicai.local', NULL, 20, 1, 'FREE', 0);
GO

INSERT INTO dbo.Authorities (username, role_id)
SELECT u.username, 'USER'
FROM dbo.Users u
WHERE u.username IN ('demo_lofi', 'demo_rock', 'demo_piano')
  AND NOT EXISTS (
      SELECT 1 FROM dbo.Authorities a
      WHERE a.username = u.username AND a.role_id = 'USER'
  );
GO

IF NOT EXISTS (SELECT 1 FROM dbo.Songs WHERE username = 'demo_lofi' AND title = N'Phố mưa lúc 2 giờ')
    INSERT INTO dbo.Songs
        (title, prompt, audio_url, status, is_public, lyrics, model_ver, is_remix, username)
    VALUES
        (N'Phố mưa lúc 2 giờ', N'Lofi chill, tiếng piano và mưa nhẹ',
         'https://www.soundhelix.com/examples/mp3/SoundHelix-Song-9.mp3',
         'COMPLETED', 1, NULL, 'demo-audio', 0, 'demo_lofi');
GO

IF NOT EXISTS (SELECT 1 FROM dbo.Songs WHERE username = 'demo_rock' AND title = N'Đường chân trời')
    INSERT INTO dbo.Songs
        (title, prompt, audio_url, status, is_public, lyrics, model_ver, is_remix, username)
    VALUES
        (N'Đường chân trời', N'Alternative rock, guitar mạnh và trống dồn',
         'https://www.soundhelix.com/examples/mp3/SoundHelix-Song-10.mp3',
         'COMPLETED', 1, NULL, 'demo-audio', 0, 'demo_rock');
GO

IF NOT EXISTS (SELECT 1 FROM dbo.Songs WHERE username = 'demo_piano' AND title = N'Khoảng lặng')
    INSERT INTO dbo.Songs
        (title, prompt, audio_url, status, is_public, lyrics, model_ver, is_remix, username)
    VALUES
        (N'Khoảng lặng', N'Piano độc tấu tối giản, thư giãn',
         'https://www.soundhelix.com/examples/mp3/SoundHelix-Song-11.mp3',
         'COMPLETED', 1, NULL, 'demo-audio', 0, 'demo_piano');
GO

IF NOT EXISTS (SELECT 1 FROM dbo.Playlists WHERE username = 'demo_lofi' AND name = N'Nhạc học bài')
    INSERT INTO dbo.Playlists (name, is_public, username)
    VALUES (N'Nhạc học bài', 1, 'demo_lofi');
GO

DECLARE @playlist_id INT = (
    SELECT TOP 1 id FROM dbo.Playlists
    WHERE username = 'demo_lofi' AND name = N'Nhạc học bài'
);
DECLARE @song_id INT = (
    SELECT TOP 1 id FROM dbo.Songs
    WHERE username = 'demo_lofi' AND title = N'Phố mưa lúc 2 giờ'
);
IF @playlist_id IS NOT NULL AND @song_id IS NOT NULL
   AND NOT EXISTS (
       SELECT 1 FROM dbo.Playlist_Songs
       WHERE playlist_id = @playlist_id AND song_id = @song_id
   )
    INSERT INTO dbo.Playlist_Songs (playlist_id, song_id, sort_order)
    VALUES (@playlist_id, @song_id, 1);
GO

IF NOT EXISTS (
    SELECT 1 FROM dbo.Friendships
    WHERE user_low = 'demo_lofi' AND user_high = 'demo_rock'
)
    INSERT INTO dbo.Friendships (requester, addressee, status, responded_at)
    VALUES ('demo_lofi', 'demo_rock', 'ACCEPTED', GETDATE());
GO

PRINT N'Đã kiểm tra và bổ sung dữ liệu demo WebMusicAI.';
GO
