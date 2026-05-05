package org.example;

import com.pengrad.telegrambot.TelegramBot;
import org.example.config.AppConfig;
import org.example.db.DatabaseFactory;
import org.example.db.DatabaseMigrator;
import org.example.repository.VideoRepository;
import org.example.service.RutubeClient;
import org.example.service.RutubeUrlParser;
import org.example.service.VideoService;
import org.example.service.YoutubeClient;
import org.example.service.YoutubeUrlParser;
import org.example.telegram.TelegramBotController;

public class Main {
    public static void main(String[] args) {
        AppConfig config = AppConfig.fromEnvironment();
        var dataSource = DatabaseFactory.createDataSource(config);

        DatabaseFactory.checkConnection(dataSource);
        DatabaseMigrator.migrate(dataSource);

        Runtime.getRuntime().addShutdownHook(new Thread(dataSource::close));

        VideoService videoService = new VideoService(
                new YoutubeUrlParser(),
                new RutubeUrlParser(),
                new VideoRepository(dataSource),
                new YoutubeClient(config.youtubeApiKey()),
                new RutubeClient()
        );

        TelegramBot bot = new TelegramBot(config.telegramBotToken());
        new TelegramBotController(bot, videoService, config.allowedTelegramUserIds()).start();
    }
}
