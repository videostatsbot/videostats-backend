package org.example.config;

import io.github.cdimascio.dotenv.Dotenv;

public record AppConfig(
        String telegramBotToken,
        String youtubeApiKey,
        String databaseUrl,
        String databaseUser,
        String databasePassword
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
                requireEnv("DATABASE_PASSWORD", dotenv)
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
}
