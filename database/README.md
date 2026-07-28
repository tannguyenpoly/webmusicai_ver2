# WebMusicAI database

Use only `setup-fresh.sql`.

The script deletes and recreates `MusicAI_DB`, creates the complete schema, and inserts demo data. It is intentionally destructive and should be run only when existing data can be discarded.

## Run

1. Stop the Spring Boot application.
2. Open `setup-fresh.sql` in SQL Server Management Studio.
3. Connect using a SQL Server administrator account and execute the entire file from top to bottom.
4. Start the Spring Boot application again.

Do not run individual fragments of the script.
