package org.example.model;

import java.time.LocalDateTime;

public record VideoHistoryEntry(
        Long viewCount,
        LocalDateTime checkedAt,
        VideoStatus status,
        String errorMessage
) {
}
