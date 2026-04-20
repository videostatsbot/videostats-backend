package org.example.repository;

import org.example.model.NewVideo;
import org.example.model.StoredVideo;
import org.example.model.VideoHistoryEntry;
import org.example.model.VideoPlatform;
import org.example.model.VideoStatus;
import org.example.model.VideoSummary;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class VideoRepository {
    private final DataSource dataSource;

    public VideoRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public SaveVideoResult save(NewVideo newVideo) {
        if (existsByPlatformAndVideoId(newVideo.platform().name(), newVideo.videoId())) {
            return SaveVideoResult.duplicateVideo();
        }

        String sql = """
                INSERT INTO videos (
                    platform,
                    video_id,
                    url,
                    status,
                    added_by_user_id
                ) VALUES (?, ?, ?, ?, ?)
                RETURNING id
                """;

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, newVideo.platform().name());
            statement.setString(2, newVideo.videoId());
            statement.setString(3, newVideo.url());
            statement.setString(4, newVideo.status().name());
            if (newVideo.addedByUserId() == null) {
                statement.setNull(5, java.sql.Types.BIGINT);
            } else {
                statement.setLong(5, newVideo.addedByUserId());
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return SaveVideoResult.savedVideo(resultSet.getLong("id"));
                }
            }
            throw new IllegalStateException("Video was inserted but id was not returned");
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to save video", e);
        }
    }

    public List<StoredVideo> findAll() {
        String sql = """
                SELECT id, platform, video_id, url, title, status, last_view_count
                FROM videos
                ORDER BY created_at DESC, id DESC
                """;

        List<StoredVideo> videos = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                videos.add(mapStoredVideo(resultSet));
            }
            return videos;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to fetch videos", e);
        }
    }

    public StoredVideo findById(long id) {
        String sql = """
                SELECT id, platform, video_id, url, title, status, last_view_count
                FROM videos
                WHERE id = ?
                """;

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return mapStoredVideo(resultSet);
                }
                return null;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to fetch video by id", e);
        }
    }

    public List<StoredVideo> searchByTitle(String query) {
        String sql = """
                SELECT id, platform, video_id, url, title, status, last_view_count,
                       CASE
                           WHEN LOWER(COALESCE(title, '')) = LOWER(?) THEN 0
                           WHEN LOWER(COALESCE(title, '')) LIKE LOWER(?) || '%' THEN 1
                           WHEN LOWER(COALESCE(title, '')) LIKE '%' || LOWER(?) || '%' THEN 2
                           ELSE 3
                       END AS relevance_rank
                FROM videos
                WHERE LOWER(COALESCE(title, '')) LIKE '%' || LOWER(?) || '%'
                ORDER BY relevance_rank, created_at DESC, id DESC
                """;

        List<StoredVideo> videos = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, query);
            statement.setString(2, query);
            statement.setString(3, query);
            statement.setString(4, query);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    videos.add(mapStoredVideo(resultSet));
                }
            }
            return videos;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to search videos by title", e);
        }
    }

    public List<VideoHistoryEntry> findHistoryByVideoId(long videoId, int limit) {
        String sql = """
                SELECT view_count, checked_at, status, error_message
                FROM video_stats_history
                WHERE video_id = ?
                ORDER BY checked_at DESC, id DESC
                LIMIT ?
                """;

        List<VideoHistoryEntry> history = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, videoId);
            statement.setInt(2, limit);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    Long viewCount = resultSet.getObject("view_count") == null
                            ? null
                            : resultSet.getLong("view_count");

                    history.add(new VideoHistoryEntry(
                            viewCount,
                            resultSet.getTimestamp("checked_at").toLocalDateTime(),
                            VideoStatus.valueOf(resultSet.getString("status")),
                            resultSet.getString("error_message")
                    ));
                }
            }
            return history;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to fetch video history", e);
        }
    }

    public VideoSummary getSummary() {
        String sql = """
                SELECT COUNT(*) AS total_videos,
                       COALESCE(SUM(last_view_count), 0) AS total_views
                FROM videos
                """;

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            if (resultSet.next()) {
                return new VideoSummary(
                        resultSet.getInt("total_videos"),
                        resultSet.getLong("total_views")
                );
            }
            return new VideoSummary(0, 0);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to fetch video summary", e);
        }
    }

    public boolean deleteById(long id) {
        String sql = """
                DELETE FROM videos
                WHERE id = ?
                """;

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to delete video", e);
        }
    }

    public void updateVideoMetadataAfterSuccess(long id, String title, long viewCount) {
        String updateSql = """
                UPDATE videos
                SET title = ?,
                    status = ?,
                    last_view_count = ?,
                    last_successful_view_count = ?,
                    last_checked_at = CURRENT_TIMESTAMP,
                    last_successful_checked_at = CURRENT_TIMESTAMP,
                    last_error_message = NULL,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """;

        String insertHistorySql = """
                INSERT INTO video_stats_history (video_id, view_count, status, error_message)
                VALUES (?, ?, ?, NULL)
                """;

        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement updateStatement = connection.prepareStatement(updateSql);
                 PreparedStatement historyStatement = connection.prepareStatement(insertHistorySql)) {
                updateStatement.setString(1, title);
                updateStatement.setString(2, VideoStatus.ACTIVE.name());
                updateStatement.setLong(3, viewCount);
                updateStatement.setLong(4, viewCount);
                updateStatement.setLong(5, id);
                updateStatement.executeUpdate();

                historyStatement.setLong(1, id);
                historyStatement.setLong(2, viewCount);
                historyStatement.setString(3, VideoStatus.ACTIVE.name());
                historyStatement.executeUpdate();

                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to update successful video stats", e);
        }
    }

    public void updateVideoMetadataAfterFailure(long id, VideoStatus status, String errorMessage) {
        String updateSql = """
                UPDATE videos
                SET status = ?,
                    last_checked_at = CURRENT_TIMESTAMP,
                    last_error_message = ?,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """;

        String insertHistorySql = """
                INSERT INTO video_stats_history (video_id, view_count, status, error_message)
                VALUES (?, NULL, ?, ?)
                """;

        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement updateStatement = connection.prepareStatement(updateSql);
                 PreparedStatement historyStatement = connection.prepareStatement(insertHistorySql)) {
                updateStatement.setString(1, status.name());
                updateStatement.setString(2, errorMessage);
                updateStatement.setLong(3, id);
                updateStatement.executeUpdate();

                historyStatement.setLong(1, id);
                historyStatement.setString(2, status.name());
                historyStatement.setString(3, errorMessage);
                historyStatement.executeUpdate();

                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to update failed video stats", e);
        }
    }

    private boolean existsByPlatformAndVideoId(String platform, String videoId) {
        String sql = """
                SELECT 1
                FROM videos
                WHERE platform = ? AND video_id = ?
                LIMIT 1
                """;

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, platform);
            statement.setString(2, videoId);

            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to check existing video", e);
        }
    }

    private StoredVideo mapStoredVideo(ResultSet resultSet) throws SQLException {
        Long lastViewCount = resultSet.getObject("last_view_count") == null
                ? null
                : resultSet.getLong("last_view_count");

        return new StoredVideo(
                resultSet.getLong("id"),
                VideoPlatform.valueOf(resultSet.getString("platform")),
                resultSet.getString("video_id"),
                resultSet.getString("url"),
                resultSet.getString("title"),
                VideoStatus.valueOf(resultSet.getString("status")),
                lastViewCount
        );
    }

    public record SaveVideoResult(boolean saved, boolean alreadyExists, Long id) {
        public static SaveVideoResult savedVideo(long id) {
            return new SaveVideoResult(true, false, id);
        }

        public static SaveVideoResult duplicateVideo() {
            return new SaveVideoResult(false, true, null);
        }
    }
}
