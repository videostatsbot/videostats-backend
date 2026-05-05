package org.example.service;

import org.example.model.NewVideo;
import org.example.model.StoredVideo;
import org.example.model.VideoHistoryEntry;
import org.example.model.VideoPlatform;
import org.example.model.VideoStatus;
import org.example.model.VideoSummary;
import org.example.repository.VideoRepository;

import java.util.ArrayList;
import java.util.List;

public class VideoService {
    private final YoutubeUrlParser youtubeUrlParser;
    private final RutubeUrlParser rutubeUrlParser;
    private final VideoRepository videoRepository;
    private final YoutubeClient youtubeClient;
    private final RutubeClient rutubeClient;

    public VideoService(
            YoutubeUrlParser youtubeUrlParser,
            RutubeUrlParser rutubeUrlParser,
            VideoRepository videoRepository,
            YoutubeClient youtubeClient,
            RutubeClient rutubeClient
    ) {
        this.youtubeUrlParser = youtubeUrlParser;
        this.rutubeUrlParser = rutubeUrlParser;
        this.videoRepository = videoRepository;
        this.youtubeClient = youtubeClient;
        this.rutubeClient = rutubeClient;
    }

    public AddVideoResult addVideo(String rawUrl) {
        return addVideo(rawUrl, null);
    }

    public AddVideoResult addVideo(String rawUrl, Long addedByUserId) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return AddVideoResult.invalid("Нужно указать ссылку на видео YouTube или Rutube.");
        }

        String normalizedUrl = rawUrl.trim();

        return youtubeUrlParser.extractVideoId(normalizedUrl)
                .map(videoId -> saveVideo(VideoPlatform.YOUTUBE, normalizedUrl, videoId, addedByUserId))
                .or(() -> rutubeUrlParser.extractVideoId(normalizedUrl)
                        .map(videoId -> saveVideo(VideoPlatform.RUTUBE, normalizedUrl, videoId, addedByUserId)))
                .orElseGet(() -> AddVideoResult.invalid("Пока что я принимаю только корректные ссылки на YouTube и Rutube."));
    }

    private AddVideoResult saveVideo(VideoPlatform platform, String rawUrl, String videoId, Long addedByUserId) {
        var saveResult = videoRepository.save(new NewVideo(
                platform,
                videoId,
                rawUrl,
                VideoStatus.ACTIVE,
                addedByUserId
        ));

        if (saveResult.alreadyExists()) {
            return AddVideoResult.alreadyExists();
        }

        RefreshSingleResult refreshResult = refreshVideoById(saveResult.id());
        if (refreshResult.updated()) {
            return AddVideoResult.saved(refreshResult.displayLabel());
        }

        return AddVideoResult.savedWithoutStats(refreshResult.displayLabel(), refreshResult.message());
    }

    public List<StoredVideo> getAllVideos() {
        return videoRepository.findAll();
    }

    public List<StoredVideo> getYoutubeVideos() {
        return videoRepository.findAllByPlatform(VideoPlatform.YOUTUBE);
    }

    public List<StoredVideo> getRutubeVideos() {
        return videoRepository.findAllByPlatform(VideoPlatform.RUTUBE);
    }

    public VideoSummary getSummary() {
        return videoRepository.getSummary();
    }

    public StoredVideo getVideoById(long id) {
        return videoRepository.findById(id);
    }

    public List<VideoHistoryEntry> getVideoHistory(long id, int limit) {
        return videoRepository.findHistoryByVideoId(id, limit);
    }

    public SearchResult searchVideos(String query) {
        if (query == null || query.isBlank()) {
            return SearchResult.invalid("После команды /search нужно указать часть названия видео.");
        }

        return SearchResult.success(query.trim(), videoRepository.searchByTitle(query.trim()));
    }

    public DeleteVideoResult deleteVideo(String rawId) {
        if (rawId == null || rawId.isBlank()) {
            return DeleteVideoResult.invalid("После команды /delete нужно указать id видео. Его можно посмотреть в /list.");
        }

        long videoId;
        try {
            videoId = Long.parseLong(rawId.trim());
        } catch (NumberFormatException e) {
            return DeleteVideoResult.invalid("id видео должен быть числом. Посмотри нужный id через /list.");
        }

        boolean deleted = videoRepository.deleteById(videoId);
        if (!deleted) {
            return DeleteVideoResult.notFound(videoId);
        }

        return DeleteVideoResult.deleted(videoId);
    }

    public RefreshAllResult refreshAllVideos() {
        List<StoredVideo> videos = videoRepository.findAll();
        if (videos.isEmpty()) {
            return new RefreshAllResult(0, 0, List.of("Список видео пуст."));
        }

        int updatedCount = 0;
        List<String> messages = new ArrayList<>();
        for (StoredVideo video : videos) {
            RefreshSingleResult result = refreshVideo(video);
            if (result.updated()) {
                updatedCount++;
            }
            messages.add(result.displayLabel() + ": " + result.message());
        }

        return new RefreshAllResult(videos.size(), updatedCount, messages);
    }

    public RefreshSingleResult refreshVideoById(long id) {
        StoredVideo video = videoRepository.findById(id);
        if (video == null) {
            return new RefreshSingleResult(false, "[??] Видео не найдено", "видео не найдено");
        }

        return refreshVideo(video);
    }

    private RefreshSingleResult refreshVideo(StoredVideo video) {
        return switch (video.platform()) {
            case YOUTUBE -> refreshYoutubeVideo(video);
            case RUTUBE -> refreshRutubeVideo(video);
        };
    }

    private RefreshSingleResult refreshYoutubeVideo(StoredVideo video) {
        YoutubeClient.FetchYoutubeResult result = youtubeClient.fetchVideoDetails(video.videoId());
        if (result.isSuccess()) {
            YoutubeVideoDetails details = result.details().orElseThrow();
            videoRepository.updateVideoMetadataAfterSuccess(video.id(), details.title(), details.viewCount());
            return new RefreshSingleResult(true, buildDisplayLabel(video.platform(), details.title()), "статистика обновлена");
        }

        VideoStatus status = result.failureReason() == YoutubeClient.FailureReason.NOT_FOUND
                ? VideoStatus.INVALID_LINK
                : VideoStatus.API_ERROR;
        videoRepository.updateVideoMetadataAfterFailure(video.id(), status, result.errorMessage());
        return new RefreshSingleResult(false, buildDisplayLabel(video), result.errorMessage());
    }

    private RefreshSingleResult refreshRutubeVideo(StoredVideo video) {
        RutubeClient.FetchRutubeResult result = rutubeClient.fetchVideoDetails(video.videoId());
        if (result.isSuccess()) {
            RutubeVideoDetails details = result.details().orElseThrow();
            videoRepository.updateVideoMetadataAfterSuccess(video.id(), details.title(), details.viewCount());
            return new RefreshSingleResult(true, buildDisplayLabel(video.platform(), details.title()), "статистика обновлена");
        }

        VideoStatus status = result.failureReason() == RutubeClient.FailureReason.NOT_FOUND
                ? VideoStatus.INVALID_LINK
                : VideoStatus.API_ERROR;
        videoRepository.updateVideoMetadataAfterFailure(video.id(), status, result.errorMessage());
        return new RefreshSingleResult(false, buildDisplayLabel(video), result.errorMessage());
    }

    public record AddVideoResult(Status status, String message) {
        public static AddVideoResult saved(String displayLabel) {
            return new AddVideoResult(
                    Status.SAVED,
                    """
                    Видео добавлено.
                    %s
                    """.formatted(displayLabel)
            );
        }

        public static AddVideoResult savedWithoutStats(String displayLabel, String refreshMessage) {
            return new AddVideoResult(
                    Status.SAVED,
                    """
                    Видео добавлено, но получить статистику сразу не удалось.
                    %s
                    Причина: %s
                    """.formatted(displayLabel, refreshMessage)
            );
        }

        public static AddVideoResult alreadyExists() {
            return new AddVideoResult(
                    Status.ALREADY_EXISTS,
                    "Такое видео уже добавлено."
            );
        }

        public static AddVideoResult invalid(String message) {
            return new AddVideoResult(Status.INVALID, message);
        }
    }

    public enum Status {
        SAVED,
        ALREADY_EXISTS,
        INVALID
    }

    public record RefreshSingleResult(boolean updated, String displayLabel, String message) {
    }

    public record RefreshAllResult(int totalVideos, int updatedVideos, List<String> messages) {
    }

    public record DeleteVideoResult(Status status, String message) {
        public static DeleteVideoResult deleted(long id) {
            return new DeleteVideoResult(Status.SAVED, "Видео удалено.\nid: " + id);
        }

        public static DeleteVideoResult notFound(long id) {
            return new DeleteVideoResult(Status.INVALID, "Видео с id " + id + " не найдено.");
        }

        public static DeleteVideoResult invalid(String message) {
            return new DeleteVideoResult(Status.INVALID, message);
        }
    }

    public record SearchResult(boolean valid, String query, List<StoredVideo> videos, String message) {
        public static SearchResult invalid(String message) {
            return new SearchResult(false, "", List.of(), message);
        }

        public static SearchResult success(String query, List<StoredVideo> videos) {
            return new SearchResult(true, query, videos, "");
        }
    }

    private String buildDisplayLabel(StoredVideo video) {
        return buildDisplayLabel(video.platform(), video.title());
    }

    private String buildDisplayLabel(VideoPlatform platform, String title) {
        String platformCode = switch (platform) {
            case YOUTUBE -> "YT";
            case RUTUBE -> "RT";
        };

        String baseTitle = title == null || title.isBlank()
                ? "Без названия"
                : title.trim();

        String shortenedTitle = baseTitle.length() > 32
                ? baseTitle.substring(0, 32).trim() + "..."
                : baseTitle;

        return "[" + platformCode + "] " + shortenedTitle;
    }
}
