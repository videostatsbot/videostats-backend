package org.example.model;

public record StoredVideo(
        long id,
        VideoPlatform platform,
        String videoId,
        String url,
        String title,
        VideoStatus status,
        Long lastViewCount
) {
}
