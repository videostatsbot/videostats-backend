CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    telegram_user_id BIGINT NOT NULL UNIQUE,
    username VARCHAR(255),
    role VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE videos (
    id BIGSERIAL PRIMARY KEY,
    platform VARCHAR(50) NOT NULL,
    video_id VARCHAR(255) NOT NULL,
    url TEXT NOT NULL UNIQUE,
    title VARCHAR(500),
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    last_view_count BIGINT,
    last_successful_view_count BIGINT,
    last_checked_at TIMESTAMP,
    last_successful_checked_at TIMESTAMP,
    last_error_message TEXT,
    added_by_user_id BIGINT REFERENCES users(id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT videos_platform_video_id_unique UNIQUE (platform, video_id)
);

CREATE TABLE video_stats_history (
    id BIGSERIAL PRIMARY KEY,
    video_id BIGINT NOT NULL REFERENCES videos(id) ON DELETE CASCADE,
    view_count BIGINT,
    checked_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(50) NOT NULL,
    error_message TEXT
);

CREATE INDEX idx_videos_platform ON videos(platform);
CREATE INDEX idx_videos_status ON videos(status);
CREATE INDEX idx_video_stats_history_video_id ON video_stats_history(video_id);
CREATE INDEX idx_video_stats_history_checked_at ON video_stats_history(checked_at);
