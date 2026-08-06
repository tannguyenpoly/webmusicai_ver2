# WebMusicAI database

For a new demo database, use only `setup-fresh.sql`. It contains the entire schema and all demo data in one file.

The seed is intentionally large enough to test the actual UI: 26 users, 28 songs with cover images and playable sample audio, 22 genres, 23 playlists, 22 albums, 25 orders, 26 token transactions, and 22 payment logs. Dates are fixed from January 2026 through 6 August 2026, so the Admin time filters and pagination always have meaningful results on every machine.

The script deletes and recreates `MusicAI_DB`, creates the complete schema, and inserts demo data. It is intentionally destructive and should be run only when existing data can be discarded.

## Run

1. Stop the Spring Boot application.
2. Open `setup-fresh.sql` in SQL Server Management Studio.
3. Connect using a SQL Server administrator account and execute the entire file from top to bottom.
4. Start the Spring Boot application again.

Do not run individual fragments of the script. The script is destructive: it deletes and recreates `MusicAI_DB`. Use it only for the shared demo database, not a database containing real presentation transactions.

## Existing database

Do not use `setup-fresh.sql` because it deletes data. Run the newest file in `migrations/` once instead.

Migrations only change the schema; they deliberately do not inject the large shared demo dataset into a database that may contain real accounts or real payments. To obtain exactly the same 2026 demo data as every other member, back up any local data you need and run `setup-fresh.sql`.
The demo payment logs in `setup-fresh.sql` are labelled `[DEMO]`; real SePay/VNPay callbacks are added to `Payment_Logs` automatically by the application.
