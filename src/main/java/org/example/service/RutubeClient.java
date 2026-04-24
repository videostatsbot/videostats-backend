package org.example.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

public class RutubeClient {
    private static final String API_URL_TEMPLATE = "https://rutube.ru/api/video/%s";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public RutubeClient() {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(), new ObjectMapper());
    }

    public RutubeClient(HttpClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    public FetchRutubeResult fetchVideoDetails(String videoId) {
        if (videoId == null || videoId.isBlank()) {
            return FetchRutubeResult.notFound("Не указан Rutube video_id");
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_URL_TEMPLATE.formatted(videoId.trim())))
                    .timeout(Duration.ofSeconds(15))
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 404) {
                return FetchRutubeResult.notFound("Видео не найдено в Rutube");
            }
            if (response.statusCode() != 200) {
                return FetchRutubeResult.apiError("Rutube API вернул код " + response.statusCode());
            }

            JsonNode root = objectMapper.readTree(response.body());
            if (!root.isObject() || root.isEmpty()) {
                return FetchRutubeResult.apiError("Rutube API вернул пустой ответ");
            }

            String title = root.path("title").asText(null);
            JsonNode hitsNode = root.path("hits");
            if (title == null || title.isBlank() || hitsNode.isMissingNode() || hitsNode.isNull()) {
                return FetchRutubeResult.apiError("Rutube API не вернул обязательные поля видео");
            }

            return FetchRutubeResult.success(new RutubeVideoDetails(title, hitsNode.asLong()));
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return FetchRutubeResult.apiError("Не удалось получить данные от Rutube API");
        }
    }

    public record FetchRutubeResult(Optional<RutubeVideoDetails> details, FailureReason failureReason, String errorMessage) {
        public static FetchRutubeResult success(RutubeVideoDetails details) {
            return new FetchRutubeResult(Optional.of(details), null, null);
        }

        public static FetchRutubeResult notFound(String message) {
            return new FetchRutubeResult(Optional.empty(), FailureReason.NOT_FOUND, message);
        }

        public static FetchRutubeResult apiError(String message) {
            return new FetchRutubeResult(Optional.empty(), FailureReason.API_ERROR, message);
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
