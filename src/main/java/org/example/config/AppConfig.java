package org.example.config;

public record AppConfig(
        String telegramBotToken,
        String youtubeApiKey,
        String databaseUrl,
        String databaseUser,
        String databasePassword
) {
    public static AppConfig fromEnvironment() {
        return new AppConfig(
                requireEnv("TELEGRAM_BOT_TOKEN"),
                requireEnv("YOUTUBE_API_KEY"),
                requireEnv("DATABASE_URL"),
                requireEnv("DATABASE_USER"),
                requireEnv("DATABASE_PASSWORD")
        );
    }

    private static String requireEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is not set");
        }
        return value;
    }
}
