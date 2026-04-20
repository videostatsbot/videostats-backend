package org.example.service;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Optional;

public class YoutubeUrlParser {
    public Optional<String> extractVideoId(String rawUrl) {
        try {
            URI uri = new URI(rawUrl.trim());
            String host = normalizeHost(uri.getHost());
            String path = uri.getPath() == null ? "" : uri.getPath();

            if ("youtu.be".equals(host)) {
                return extractFromShortUrl(path);
            }

            if ("youtube.com".equals(host) || "www.youtube.com".equals(host) || "m.youtube.com".equals(host)) {
                if ("/watch".equals(path)) {
                    return extractQueryParam(uri.getQuery(), "v");
                }
                if (path.startsWith("/shorts/")) {
                    return extractPathSegment(path, 2);
                }
                if (path.startsWith("/embed/")) {
                    return extractPathSegment(path, 2);
                }
            }

            return Optional.empty();
        } catch (URISyntaxException e) {
            return Optional.empty();
        }
    }

    private String normalizeHost(String host) {
        return host == null ? "" : host.toLowerCase();
    }

    private Optional<String> extractFromShortUrl(String path) {
        if (path == null || path.isBlank() || "/".equals(path)) {
            return Optional.empty();
        }
        return Optional.of(path.startsWith("/") ? path.substring(1) : path)
                .map(this::stripExtraPath)
                .filter(this::isLikelyVideoId);
    }

    private Optional<String> extractPathSegment(String path, int index) {
        String[] segments = Arrays.stream(path.split("/"))
                .filter(segment -> !segment.isBlank())
                .toArray(String[]::new);
        if (segments.length <= index - 1) {
            return Optional.empty();
        }
        String candidate = segments[index - 1];
        return isLikelyVideoId(candidate) ? Optional.of(candidate) : Optional.empty();
    }

    private Optional<String> extractQueryParam(String query, String paramName) {
        if (query == null || query.isBlank()) {
            return Optional.empty();
        }

        return Arrays.stream(query.split("&"))
                .map(part -> part.split("=", 2))
                .filter(parts -> parts.length == 2)
                .filter(parts -> paramName.equals(parts[0]))
                .map(parts -> parts[1])
                .filter(this::isLikelyVideoId)
                .findFirst();
    }

    private String stripExtraPath(String value) {
        int slashIndex = value.indexOf('/');
        return slashIndex >= 0 ? value.substring(0, slashIndex) : value;
    }

    private boolean isLikelyVideoId(String value) {
        return value != null && value.matches("[A-Za-z0-9_-]{11}");
    }
}
