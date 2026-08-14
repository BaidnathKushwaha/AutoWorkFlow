-- ============================================================
-- V3: Add Google OAuth support to users table
-- ============================================================

-- Make password_hash nullable (Google-only users have no password)
ALTER TABLE users
    ALTER COLUMN password_hash DROP NOT NULL;

-- Add Google ID for linking accounts
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS google_id VARCHAR(255);

-- Add avatar URL from Google profile
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS avatar_url TEXT;

-- Unique index on google_id for fast lookup
CREATE UNIQUE INDEX IF NOT EXISTS idx_users_google_id ON users(google_id)
    WHERE google_id IS NOT NULL;
