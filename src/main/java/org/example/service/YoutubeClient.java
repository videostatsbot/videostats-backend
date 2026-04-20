package org.example.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

public class YoutubeClient {
    private static final String API_URL = "https://www.googleapis.com/youtube/v3/videos";

    private final String apiKey;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public YoutubeClient(String apiKey) {
        this(apiKey, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(), new ObjectMapper());
    }

    public YoutubeClient(String apiKey, HttpClient httpClient, ObjectMapper objectMapper) {
        this.apiKey = apiKey;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    public FetchYoutubeResult fetchVideoDetails(String videoId) {
        try {
            String query = "part=snippet,statistics&id=" + encode(videoId)
                    + "&key=" + encode(apiKey)
                    + "&fields=items(id,snippet/title,statistics/viewCount)";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_URL + "?" + query))
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return FetchYoutubeResult.apiError("YouTube API вернул код " + response.statusCode());
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode items = root.path("items");
            if (!items.isArray() || items.isEmpty()) {
                return FetchYoutubeResult.notFound();
            }

            JsonNode firstItem = items.get(0);
            String title = firstItem.path("snippet").path("title").asText(null);
            JsonNode viewCountNode = firstItem.path("statistics").path("viewCount");
            if (title == null || viewCountNode.isMissingNode() || viewCountNode.isNull()) {
                return FetchYoutubeResult.apiError("YouTube API не вернул обязательные поля видео");
            }

            long viewCount = viewCountNode.asLong();
            return FetchYoutubeResult.success(new YoutubeVideoDetails(title, viewCount));
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return FetchYoutubeResult.apiError("Не удалось получить данные от YouTube API");
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    public record FetchYoutubeResult(Optional<YoutubeVideoDetails> details, FailureReason failureReason, String errorMessage) {
        public static FetchYoutubeResult success(YoutubeVideoDetails details) {
            return new FetchYoutubeResult(Optional.of(details), null, null);
        }

        public static FetchYoutubeResult notFound() {
            return new FetchYoutubeResult(Optional.empty(), FailureReason.NOT_FOUND, "Видео не найдено в YouTube");
        }

        public static FetchYoutubeResult apiError(String message) {
            return new FetchYoutubeResult(Optional.empty(), FailureReason.API_ERROR, message);
        }

        public boolean isSuccess() {
            return details.isPresent();
        }
    }

    public enum FailureReason {
        NOT_FOUND,
        API_ERROR
    }
}
