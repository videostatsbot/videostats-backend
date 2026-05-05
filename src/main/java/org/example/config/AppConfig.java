package org.example.config;

import io.github.cdimascio.dotenv.Dotenv;

import java.util.LinkedHashSet;
import java.util.Set;

public record AppConfig(
        String telegramBotToken,
        String youtubeApiKey,
        String databaseUrl,
        String databaseUser,
        String databasePassword,
        Set<Long> allowedTelegramUserIds
) {
    public static AppConfig fromEnvironment() {

        Dotenv dotenv = Dotenv.configure()
                .ignoreIfMissing()
                .load();

        return new AppConfig(
                requireEnv("TELEGRAM_BOT_TOKEN", dotenv),
                requireEnv("YOUTUBE_API_KEY", dotenv),
                requireEnv("DATABASE_URL", dotenv),
                requireEnv("DATABASE_USER", dotenv),
                requireEnv("DATABASE_PASSWORD", dotenv),
                parseLongSet(optionalEnv("TELEGRAM_ALLOWED_USER_IDS", dotenv))
        );
    }

    private static String requireEnv(String name, Dotenv dotenv) {
        String value = dotenv.get(name);
        if (value == null || value.isBlank()) value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is not set");
        }
        return value;
    }

    private static String optionalEnv(String name, Dotenv dotenv) {
        String value = dotenv.get(name);
        if (value == null || value.isBlank()) value = System.getenv(name);
        return value;
    }

    private static Set<Long> parseLongSet(String value) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }

        Set<Long> result = new LinkedHashSet<>();
        for (String item : value.split(",")) {
            String trimmed = item.trim();
            if (!trimmed.isEmpty()) {
                result.add(Long.parseLong(trimmed));
            }
        }
        return Set.copyOf(result);
    }
}
