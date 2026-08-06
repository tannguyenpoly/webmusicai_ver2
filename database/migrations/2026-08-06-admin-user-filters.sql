/* Chạy một lần trên MusicAI_DB đã tồn tại. Không xóa dữ liệu. */
USE MusicAI_DB;
GO

IF COL_LENGTH('dbo.Users', 'created_at') IS NULL
BEGIN
    ALTER TABLE dbo.Users ADD created_at DATETIME NULL;
    UPDATE dbo.Users SET created_at = GETDATE() WHERE created_at IS NULL;
    ALTER TABLE dbo.Users ALTER COLUMN created_at DATETIME NOT NULL;
    ALTER TABLE dbo.Users ADD CONSTRAINT DF_Users_created_at DEFAULT GETDATE() FOR created_at;
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_Users_AdminFilter' AND object_id = OBJECT_ID('dbo.Users'))
    CREATE INDEX IX_Users_AdminFilter ON dbo.Users(created_at, account_tier, token_balance);
GO
