-- Lưu lựa chọn giọng hát của bài được tạo: auto, male hoặc female.
IF COL_LENGTH('dbo.Songs', 'vocal_gender') IS NULL
BEGIN
    ALTER TABLE dbo.Songs ADD vocal_gender VARCHAR(10) NULL;
END
GO
