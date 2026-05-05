ALTER TABLE videos DROP CONSTRAINT IF EXISTS videos_added_by_user_id_fkey;

DROP TABLE IF EXISTS users;
