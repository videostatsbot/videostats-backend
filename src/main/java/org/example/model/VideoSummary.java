package org.example.model;

public record VideoSummary(
        int totalVideos,
        long totalViews,
        int youtubeVideos,
        long youtubeViews,
        int rutubeVideos,
        long rutubeViews
) {
}
