-- Execute using an administrator account; choose your own strong password before running.
CREATE DATABASE IF NOT EXISTS community_repair CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER IF NOT EXISTS 'community_app'@'localhost' IDENTIFIED BY 'REPLACE_WITH_YOUR_LOCAL_PASSWORD';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES
  ON community_repair.* TO 'community_app'@'localhost';
-- Existing accounts retain their current password. No existing data is removed.
