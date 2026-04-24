package org.example.service;

import java.net.URI;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RutubeUrlParser {
    private static final Pattern VIDEO_PATH_PATTERN = Pattern.compile("^/video/([a-fA-F0-9]{32})(?:/.*)?$");
    private static final Pattern EMBED_PATH_PATTERN = Pattern.compile("^/play/embed/([a-fA-F0-9]{32})(?:/.*)?$");

    public Optional<String> extractVideoId(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return Optional.empty();
        }

        try {
            URI uri = URI.create(rawUrl.trim());
            String host = uri.getHost();
            if (host == null) {
                return Optional.empty();
            }

            String normalizedHost = host.toLowerCase();
            if (!normalizedHost.equals("rutube.ru") && !normalizedHost.endsWith(".rutube.ru")) {
                return Optional.empty();
            }

            String path = uri.getPath();
            if (path == null || path.isBlank()) {
                return Optional.empty();
            }

            return extractFromPath(path);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private Optional<String> extractFromPath(String path) {
        Matcher videoMatcher = VIDEO_PATH_PATTERN.matcher(path);
        if (videoMatcher.matches()) {
            return Optional.of(videoMatcher.group(1).toLowerCase());
        }

        Matcher embedMatcher = EMBED_PATH_PATTERN.matcher(path);
        if (embedMatcher.matches()) {
            return Optional.of(embedMatcher.group(1).toLowerCase());
        }

        return Optional.empty();
    }
}
