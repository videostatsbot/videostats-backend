package org.example.model;

public record NewVideo(
        VideoPlatform platform,
        String videoId,
        String url,
        VideoStatus status,
        Long addedByUserId
) {
}
