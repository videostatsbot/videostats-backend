package org.example;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.model.CallbackQuery;
import com.pengrad.telegrambot.model.LinkPreviewOptions;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;
import com.pengrad.telegrambot.model.request.ParseMode;
import com.pengrad.telegrambot.request.AnswerCallbackQuery;
import com.pengrad.telegrambot.request.BaseRequest;
import com.pengrad.telegrambot.request.DeleteWebhook;
import com.pengrad.telegrambot.request.EditMessageText;
import com.pengrad.telegrambot.request.SendMessage;
import org.example.config.AppConfig;
import org.example.db.DatabaseFactory;
import org.example.db.DatabaseMigrator;
import org.example.model.StoredVideo;
import org.example.model.VideoHistoryEntry;
import org.example.model.VideoSummary;
import org.example.repository.VideoRepository;
import org.example.service.VideoService;
import org.example.service.YoutubeClient;
import org.example.service.YoutubeUrlParser;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class Main {
    private static final int PAGE_SIZE = 5;
    private static final int HISTORY_LIMIT = 5;
    private static final DateTimeFormatter HISTORY_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private static final Map<String, SearchSession> SEARCH_SESSIONS = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        AppConfig config = AppConfig.fromEnvironment();
        var dataSource = DatabaseFactory.createDataSource(config);

        DatabaseFactory.checkConnection(dataSource);
        DatabaseMigrator.migrate(dataSource);

        Runtime.getRuntime().addShutdownHook(new Thread(dataSource::close));

        VideoService videoService = new VideoService(
                new YoutubeUrlParser(),
                new VideoRepository(dataSource),
                new YoutubeClient(config.youtubeApiKey())
        );

        TelegramBot bot = new TelegramBot(config.telegramBotToken());
        safeExecute(bot, new DeleteWebhook().dropPendingUpdates(true), "Не удалось удалить webhook Telegram.");

        bot.setUpdatesListener(updates -> {
            for (Update update : updates) {
                try {
                    if (update.callbackQuery() != null) {
                        handleCallback(bot, update.callbackQuery(), videoService);
                        continue;
                    }

                    if (update.message() == null || update.message().text() == null) {
                        continue;
                    }

                    String text = update.message().text().trim();
                    long chatId = update.message().chat().id();

                    if ("/start".equals(text)) {
                        safeExecute(bot, createMessage(chatId, startMessage()).replyMarkup(mainKeyboard()), "Не удалось отправить стартовое сообщение.");
                    } else if ("/help".equals(text)) {
                        safeExecute(bot, createMessage(chatId, helpMessage()).replyMarkup(helpKeyboard()), "Не удалось отправить справку.");
                    } else if (text.startsWith("/delete")) {
                        var result = videoService.deleteVideo(extractArgument(text));
                        safeExecute(bot, createMessage(chatId, result.message()).replyMarkup(mainKeyboard()), "Не удалось отправить результат удаления.");
                    } else if ("/list".equals(text)) {
                        showList(bot, chatId, videoService, 0);
                    } else if ("/stats".equals(text)) {
                        safeExecute(bot, createMessage(chatId, formatSummary(videoService.getSummary())).replyMarkup(statsKeyboard()), "Не удалось отправить статистику.");
                    } else if ("/update".equals(text)) {
                        var refreshResult = videoService.refreshAllVideos();
                        safeExecute(bot, createMessage(chatId, formatRefreshResult(refreshResult)).replyMarkup(mainKeyboard()), "Не удалось отправить результат обновления.");
                    } else if (text.startsWith("/search")) {
                        handleSearchCommand(bot, chatId, videoService, extractArgument(text));
                    } else if (looksLikeUrl(text)) {
                        var result = videoService.addYoutubeVideo(text);
                        safeExecute(bot, createMessage(chatId, formatAddResult(result)).replyMarkup(mainKeyboard()), "Не удалось отправить результат добавления видео.");
                    } else {
                        safeExecute(bot, createMessage(chatId, unknownMessage()).replyMarkup(mainKeyboard()), "Не удалось отправить сообщение о неизвестной команде.");
                    }
                } catch (Exception e) {
                    System.err.println("Ошибка при обработке обновления Telegram: " + e.getMessage());
                }
            }
            return UpdatesListener.CONFIRMED_UPDATES_ALL;
        });
    }

    private static void handleSearchCommand(TelegramBot bot, long chatId, VideoService videoService, String query) {
        var searchResult = videoService.searchVideos(query);
        if (!searchResult.valid()) {
            safeExecute(bot, createMessage(chatId, searchResult.message()).replyMarkup(mainKeyboard()), "Не удалось отправить результат поиска.");
            return;
        }

        String token = createSearchSession(searchResult.query());
        safeExecute(bot, createMessage(chatId, formatSearchPage(searchResult.videos(), searchResult.query(), 0))
                .replyMarkup(searchKeyboard(searchResult.videos(), token, 0)), "Не удалось отправить результат поиска.");
    }

    private static void handleCallback(TelegramBot bot, CallbackQuery callbackQuery, VideoService videoService) {
        String data = callbackQuery.data();
        long chatId = callbackQuery.message().chat().id();
        int messageId = callbackQuery.message().messageId();

        if ("show_list".equals(data)) {
            editList(bot, chatId, messageId, videoService, 0);
            safeAnswerCallback(bot, callbackQuery.id(), "Показываю список");
            return;
        }

        if ("show_stats".equals(data)) {
            safeExecute(bot, editMessage(chatId, messageId, formatSummary(videoService.getSummary())).replyMarkup(statsKeyboard()), "Не удалось обновить экран статистики.");
            safeAnswerCallback(bot, callbackQuery.id(), "Показываю статистику");
            return;
        }

        if ("show_help".equals(data)) {
            safeExecute(bot, editMessage(chatId, messageId, helpMessage()).replyMarkup(helpKeyboard()), "Не удалось открыть справку.");
            safeAnswerCallback(bot, callbackQuery.id(), "Открываю справку");
            return;
        }

        if ("help_add".equals(data)) {
            safeExecute(bot, editMessage(chatId, messageId, helpAddMessage()).replyMarkup(helpDetailsKeyboard()), "Не удалось открыть инструкцию по добавлению.");
            safeAnswerCallback(bot, callbackQuery.id(), "Показываю инструкцию");
            return;
        }

        if ("help_delete".equals(data)) {
            safeExecute(bot, editMessage(chatId, messageId, helpDeleteMessage()).replyMarkup(helpDetailsKeyboard()), "Не удалось открыть инструкцию по удалению.");
            safeAnswerCallback(bot, callbackQuery.id(), "Показываю инструкцию");
            return;
        }

        if ("help_search".equals(data)) {
            safeExecute(bot, editMessage(chatId, messageId, helpSearchMessage()).replyMarkup(helpDetailsKeyboard()), "Не удалось открыть инструкцию по поиску.");
            safeAnswerCallback(bot, callbackQuery.id(), "Показываю инструкцию");
            return;
        }

        if ("help_back".equals(data)) {
            safeExecute(bot, editMessage(chatId, messageId, helpMessage()).replyMarkup(helpKeyboard()), "Не удалось вернуться к справке.");
            safeAnswerCallback(bot, callbackQuery.id(), "Возвращаюсь назад");
            return;
        }

        if ("back_main".equals(data)) {
            safeExecute(bot, editMessage(chatId, messageId, startMessage()).replyMarkup(mainKeyboard()), "Не удалось вернуться в главное меню.");
            safeAnswerCallback(bot, callbackQuery.id(), "Возвращаюсь в меню");
            return;
        }

        if ("noop".equals(data)) {
            safeAnswerCallback(bot, callbackQuery.id(), "Здесь больше нечего открывать");
            return;
        }

        if (data != null && data.startsWith("list_page:")) {
            int page = parseCallbackInt(data, 1, 0);
            editList(bot, chatId, messageId, videoService, page);
            safeAnswerCallback(bot, callbackQuery.id(), null);
            return;
        }

        if (data != null && data.startsWith("search_page:")) {
            String token = parseCallbackPart(data, 1);
            int page = parseCallbackInt(data, 2, 0);
            SearchSession session = SEARCH_SESSIONS.get(token);
            if (session == null) {
                safeAnswerCallback(bot, callbackQuery.id(), "Поиск устарел, повтори команду /search");
                return;
            }

            var searchResult = videoService.searchVideos(session.query());
            safeExecute(bot, editMessage(chatId, messageId, formatSearchPage(searchResult.videos(), session.query(), page))
                    .replyMarkup(searchKeyboard(searchResult.videos(), token, page)), "Не удалось перелистнуть результаты поиска.");
            safeAnswerCallback(bot, callbackQuery.id(), null);
            return;
        }

        if (data != null && data.startsWith("open_video:")) {
            long id = parseCallbackLong(data, 1, -1);
            CardContext context = parseCardContext(data, 2);
            editCard(bot, chatId, messageId, videoService, id, context);
            safeAnswerCallback(bot, callbackQuery.id(), "Открываю карточку");
            return;
        }

        if (data != null && data.startsWith("refresh_video:")) {
            long id = parseCallbackLong(data, 1, -1);
            CardContext context = parseCardContext(data, 2);
            var result = videoService.refreshVideoById(id);
            safeExecute(bot, editMessage(chatId, messageId, formatRefreshSingleResult(result, id, videoService))
                    .replyMarkup(videoCardKeyboard(id, context)), "Не удалось обновить карточку видео.");
            safeAnswerCallback(bot, callbackQuery.id(), "Видео обновлено");
            return;
        }

        if (data != null && data.startsWith("delete_from_card:")) {
            long id = parseCallbackLong(data, 1, -1);
            CardContext context = parseCardContext(data, 2);
            var result = videoService.deleteVideo(String.valueOf(id));
            safeExecute(bot, editMessage(chatId, messageId, formatDeleteResult(result)).replyMarkup(backKeyboard(context)), "Не удалось обновить экран после удаления.");
            safeAnswerCallback(bot, callbackQuery.id(), "Видео удалено");
            return;
        }

        if ("refresh_all".equals(data)) {
            var refreshResult = videoService.refreshAllVideos();
            safeExecute(bot, editMessage(chatId, messageId, formatRefreshResult(refreshResult)).replyMarkup(mainKeyboard()), "Не удалось обновить статистику.");
            safeAnswerCallback(bot, callbackQuery.id(), "Статистика обновлена");
            return;
        }

        safeAnswerCallback(bot, callbackQuery.id(), "Неизвестное действие");
    }

    private static void showList(TelegramBot bot, long chatId, VideoService videoService, int page) {
        List<StoredVideo> videos = videoService.getAllVideos();
        safeExecute(bot, createMessage(chatId, formatVideoListPage(videos, page)).replyMarkup(listKeyboard(videos, page)), "Не удалось отправить список видео.");
    }

    private static void editList(TelegramBot bot, long chatId, int messageId, VideoService videoService, int page) {
        List<StoredVideo> videos = videoService.getAllVideos();
        safeExecute(bot, editMessage(chatId, messageId, formatVideoListPage(videos, page)).replyMarkup(listKeyboard(videos, page)), "Не удалось обновить список видео.");
    }

    private static void editCard(TelegramBot bot, long chatId, int messageId, VideoService videoService, long id, CardContext context) {
        StoredVideo video = videoService.getVideoById(id);
        if (video == null) {
            safeExecute(bot, editMessage(chatId, messageId, """
                    <b>⚠️ Видео не найдено.</b>
                    Возможно, оно уже было удалено.
                    """).replyMarkup(backKeyboard(context)), "Не удалось показать сообщение об отсутствии видео.");
            return;
        }

        safeExecute(bot, editMessage(chatId, messageId, formatVideoCard(video, videoService.getVideoHistory(video.id(), HISTORY_LIMIT)))
                .replyMarkup(videoCardKeyboard(video.id(), context)), "Не удалось открыть карточку видео.");
    }

    private static String extractArgument(String commandText) {
        int firstSpaceIndex = commandText.indexOf(' ');
        if (firstSpaceIndex < 0 || firstSpaceIndex == commandText.length() - 1) {
            return "";
        }
        return commandText.substring(firstSpaceIndex + 1).trim();
    }

    private static boolean looksLikeUrl(String text) {
        return text.startsWith("http://") || text.startsWith("https://");
    }

    private static String formatVideoListPage(List<StoredVideo> videos, int page) {
        if (videos.isEmpty()) {
            return """
                    <b>📂 Список пуст.</b>
                    Просто отправь ссылку на YouTube, и я добавлю её автоматически.
                    """;
        }

        int totalPages = totalPages(videos.size());
        int normalizedPage = normalizePage(page, totalPages);
        int fromIndex = normalizedPage * PAGE_SIZE;
        int toIndex = Math.min(fromIndex + PAGE_SIZE, videos.size());

        StringBuilder builder = new StringBuilder("""
                <b>📂 Сохранённые видео</b>
                Страница <b>%d</b> из <b>%d</b>

                """.formatted(normalizedPage + 1, totalPages));

        for (int i = fromIndex; i < toIndex; i++) {
            StoredVideo video = videos.get(i);
            builder.append(i + 1)
                    .append(". <b>")
                    .append(buildDisplayLabel(video))
                    .append("</b>\n")
                    .append("ID: <code>").append(video.id()).append("</code>\n")
                    .append("Статус: ").append(formatStatus(video.status().name())).append("\n")
                    .append("Просмотры: <b>")
                    .append(video.lastViewCount() == null ? "нет данных" : formatNumber(video.lastViewCount()))
                    .append("</b>\n\n");
        }
        return builder.toString().trim();
    }

    private static String formatSearchPage(List<StoredVideo> videos, String query, int page) {
        if (videos.isEmpty()) {
            return """
                    <b>🔎 Ничего не найдено.</b>
                    Попробуй уточнить запрос и повторить <code>/search</code>.
                    """;
        }

        int totalPages = totalPages(videos.size());
        int normalizedPage = normalizePage(page, totalPages);
        int fromIndex = normalizedPage * PAGE_SIZE;
        int toIndex = Math.min(fromIndex + PAGE_SIZE, videos.size());

        StringBuilder builder = new StringBuilder("""
                <b>🔎 Результаты поиска</b>
                Запрос: <b>%s</b>
                Страница <b>%d</b> из <b>%d</b>

                """.formatted(escapeHtml(query), normalizedPage + 1, totalPages));

        for (int i = fromIndex; i < toIndex; i++) {
            StoredVideo video = videos.get(i);
            builder.append(i + 1)
                    .append(". <b>")
                    .append(buildDisplayLabel(video))
                    .append("</b>\n")
                    .append("ID: <code>").append(video.id()).append("</code>\n")
                    .append("Просмотры: <b>")
                    .append(video.lastViewCount() == null ? "нет данных" : formatNumber(video.lastViewCount()))
                    .append("</b>\n\n");
        }
        return builder.toString().trim();
    }

    private static String formatVideoCard(StoredVideo video, List<VideoHistoryEntry> history) {
        StringBuilder builder = new StringBuilder("""
                <b>🎬 Карточка видео</b>

                <b>%s</b>
                Платформа: <b>▶️ YouTube</b>
                ID: <code>%d</code>
                Статус: %s
                Просмотры: <b>%s</b>
                Ссылка: %s

                <b>📈 История обновлений</b>
                """.formatted(
                buildDisplayLabel(video),
                video.id(),
                formatStatus(video.status().name()),
                video.lastViewCount() == null ? "нет данных" : formatNumber(video.lastViewCount()),
                escapeHtml(video.url())
        ));

        if (history.isEmpty()) {
            builder.append("Пока нет записей об обновлениях.");
        } else {
            for (VideoHistoryEntry entry : history) {
                builder.append("• ")
                        .append(HISTORY_TIME_FORMATTER.format(entry.checkedAt()))
                        .append(" — ")
                        .append(formatStatus(entry.status().name()));

                if (entry.viewCount() != null) {
                    builder.append(", просмотры: <b>")
                            .append(formatNumber(entry.viewCount()))
                            .append("</b>");
                }

                if (entry.errorMessage() != null && !entry.errorMessage().isBlank()) {
                    builder.append(", причина: ")
                            .append(escapeHtml(entry.errorMessage()));
                }
                builder.append("\n");
            }
        }

        return builder.toString().trim();
    }

    private static String formatSummary(VideoSummary summary) {
        return """
                <b>📊 Общая статистика</b>
                Ссылок: <b>%d</b>
                Суммарные просмотры: <b>%s</b>
                """.formatted(summary.totalVideos(), formatNumber(summary.totalViews()));
    }

    private static String formatRefreshResult(VideoService.RefreshAllResult result) {
        StringBuilder builder = new StringBuilder("""
                <b>✅ Обновление статистики завершено</b>
                Всего видео: <b>%d</b>
                Успешно обновлено: <b>%d</b>

                """.formatted(result.totalVideos(), result.updatedVideos()));

        for (String message : result.messages()) {
            builder.append("• ").append(escapeHtml(message)).append("\n");
        }
        return builder.toString().trim();
    }

    private static String formatRefreshSingleResult(VideoService.RefreshSingleResult result, long id, VideoService videoService) {
        StoredVideo refreshedVideo = videoService.getVideoById(id);
        if (refreshedVideo == null) {
            return """
                    <b>⚠️ Видео не найдено.</b>
                    Возможно, оно уже было удалено.
                    """;
        }

        return """
                <b>🎬 Карточка видео</b>

                <b>%s</b>
                Платформа: <b>▶️ YouTube</b>
                ID: <code>%d</code>
                Статус: %s
                Просмотры: <b>%s</b>

                <b>🔄 Последнее обновление:</b> %s
                """.formatted(
                buildDisplayLabel(refreshedVideo),
                refreshedVideo.id(),
                formatStatus(refreshedVideo.status().name()),
                refreshedVideo.lastViewCount() == null ? "нет данных" : formatNumber(refreshedVideo.lastViewCount()),
                escapeHtml(result.message())
        );
    }

    private static String formatAddResult(VideoService.AddVideoResult result) {
        return """
                <b>%s</b>

                %s
                """.formatted(
                switch (result.status()) {
                    case SAVED -> "✅ Результат";
                    case ALREADY_EXISTS -> "ℹ️ Видео уже есть";
                    case INVALID -> "⚠️ Не удалось добавить";
                },
                escapeHtml(result.message())
        );
    }

    private static String formatDeleteResult(VideoService.DeleteVideoResult result) {
        return """
                <b>🗑️ Результат удаления</b>

                %s
                """.formatted(escapeHtml(result.message()));
    }

    private static InlineKeyboardMarkup listKeyboard(List<StoredVideo> videos, int page) {
        if (videos.isEmpty()) {
            return mainKeyboard();
        }

        int totalPages = totalPages(videos.size());
        int normalizedPage = normalizePage(page, totalPages);
        int fromIndex = normalizedPage * PAGE_SIZE;
        int toIndex = Math.min(fromIndex + PAGE_SIZE, videos.size());

        InlineKeyboardButton[][] rows = new InlineKeyboardButton[(toIndex - fromIndex) + 2][];
        int rowIndex = 0;

        for (int i = fromIndex; i < toIndex; i++) {
            StoredVideo video = videos.get(i);
            rows[rowIndex++] = new InlineKeyboardButton[]{
                    new InlineKeyboardButton("Открыть ID " + video.id())
                            .callbackData("open_video:" + video.id() + ":list:" + normalizedPage)
            };
        }

        rows[rowIndex++] = new InlineKeyboardButton[]{
                normalizedPage == 0
                        ? new InlineKeyboardButton("·").callbackData("noop")
                        : new InlineKeyboardButton("<").callbackData("list_page:" + (normalizedPage - 1)),
                new InlineKeyboardButton((normalizedPage + 1) + "/" + totalPages).callbackData("noop"),
                normalizedPage >= totalPages - 1
                        ? new InlineKeyboardButton("·").callbackData("noop")
                        : new InlineKeyboardButton(">").callbackData("list_page:" + (normalizedPage + 1))
        };

        rows[rowIndex] = new InlineKeyboardButton[]{
                new InlineKeyboardButton("Назад").callbackData("back_main")
        };

        return new InlineKeyboardMarkup(rows);
    }

    private static InlineKeyboardMarkup searchKeyboard(List<StoredVideo> videos, String token, int page) {
        if (videos.isEmpty()) {
            return mainKeyboard();
        }

        int totalPages = totalPages(videos.size());
        int normalizedPage = normalizePage(page, totalPages);
        int fromIndex = normalizedPage * PAGE_SIZE;
        int toIndex = Math.min(fromIndex + PAGE_SIZE, videos.size());

        InlineKeyboardButton[][] rows = new InlineKeyboardButton[(toIndex - fromIndex) + 2][];
        int rowIndex = 0;

        for (int i = fromIndex; i < toIndex; i++) {
            StoredVideo video = videos.get(i);
            rows[rowIndex++] = new InlineKeyboardButton[]{
                    new InlineKeyboardButton("Открыть ID " + video.id())
                            .callbackData("open_video:" + video.id() + ":search:" + token + ":" + normalizedPage)
            };
        }

        rows[rowIndex++] = new InlineKeyboardButton[]{
                normalizedPage == 0
                        ? new InlineKeyboardButton("·").callbackData("noop")
                        : new InlineKeyboardButton("<").callbackData("search_page:" + token + ":" + (normalizedPage - 1)),
                new InlineKeyboardButton((normalizedPage + 1) + "/" + totalPages).callbackData("noop"),
                normalizedPage >= totalPages - 1
                        ? new InlineKeyboardButton("·").callbackData("noop")
                        : new InlineKeyboardButton(">").callbackData("search_page:" + token + ":" + (normalizedPage + 1))
        };

        rows[rowIndex] = new InlineKeyboardButton[]{
                new InlineKeyboardButton("Назад").callbackData("back_main")
        };

        return new InlineKeyboardMarkup(rows);
    }

    private static InlineKeyboardMarkup videoCardKeyboard(long id, CardContext context) {
        return new InlineKeyboardMarkup(
                new InlineKeyboardButton[]{
                        new InlineKeyboardButton("🔄 Обновить").callbackData(contextualAction("refresh_video", id, context)),
                        new InlineKeyboardButton("🗑️ Удалить").callbackData(contextualAction("delete_from_card", id, context))
                },
                new InlineKeyboardButton[]{
                        backButton(context)
                }
        );
    }

    private static InlineKeyboardMarkup backKeyboard(CardContext context) {
        return new InlineKeyboardMarkup(new InlineKeyboardButton[]{
                backButton(context)
        });
    }

    private static InlineKeyboardButton backButton(CardContext context) {
        return switch (context.type()) {
            case "list" -> new InlineKeyboardButton("Назад к списку").callbackData("list_page:" + context.page());
            case "search" -> new InlineKeyboardButton("Назад к поиску").callbackData("search_page:" + context.token() + ":" + context.page());
            default -> new InlineKeyboardButton("В меню").callbackData("back_main");
        };
    }

    private static String contextualAction(String prefix, long id, CardContext context) {
        return switch (context.type()) {
            case "list" -> prefix + ":" + id + ":list:" + context.page();
            case "search" -> prefix + ":" + id + ":search:" + context.token() + ":" + context.page();
            default -> prefix + ":" + id + ":main";
        };
    }

    private static InlineKeyboardMarkup mainKeyboard() {
        return new InlineKeyboardMarkup(
                new InlineKeyboardButton[]{
                        new InlineKeyboardButton("Список").callbackData("show_list"),
                        new InlineKeyboardButton("Статистика").callbackData("show_stats")
                },
                new InlineKeyboardButton[]{
                        new InlineKeyboardButton("🔄 Обновить статистику").callbackData("refresh_all")
                },
                new InlineKeyboardButton[]{
                        new InlineKeyboardButton("Справка").callbackData("show_help")
                }
        );
    }

    private static InlineKeyboardMarkup statsKeyboard() {
        return new InlineKeyboardMarkup(
                new InlineKeyboardButton[]{
                        new InlineKeyboardButton("🔄 Обновить статистику").callbackData("refresh_all")
                },
                new InlineKeyboardButton[]{
                        new InlineKeyboardButton("Назад").callbackData("back_main")
                }
        );
    }

    private static InlineKeyboardMarkup helpKeyboard() {
        return new InlineKeyboardMarkup(
                new InlineKeyboardButton[]{
                        new InlineKeyboardButton("Как добавить").callbackData("help_add"),
                        new InlineKeyboardButton("Как удалить").callbackData("help_delete")
                },
                new InlineKeyboardButton[]{
                        new InlineKeyboardButton("Как искать").callbackData("help_search")
                },
                new InlineKeyboardButton[]{
                        new InlineKeyboardButton("Назад").callbackData("back_main")
                }
        );
    }

    private static InlineKeyboardMarkup helpDetailsKeyboard() {
        return new InlineKeyboardMarkup(
                new InlineKeyboardButton[]{
                        new InlineKeyboardButton("Назад").callbackData("help_back"),
                        new InlineKeyboardButton("В меню").callbackData("back_main")
                }
        );
    }

    private static SendMessage createMessage(long chatId, String text) {
        return new SendMessage(chatId, text)
                .parseMode(ParseMode.HTML)
                .linkPreviewOptions(new LinkPreviewOptions().isDisabled(true));
    }

    private static EditMessageText editMessage(long chatId, int messageId, String text) {
        return new EditMessageText(chatId, messageId, text)
                .parseMode(ParseMode.HTML)
                .linkPreviewOptions(new LinkPreviewOptions().isDisabled(true));
    }

    private static String startMessage() {
        return """
                <b>👋 Бот запущен и готов к работе.</b>

                Что можно делать:
                • просто отправить ссылку на YouTube
                • открыть список видео
                • посмотреть общую статистику
                • искать видео по названию через <code>/search</code>

                Команды:
                <code>/delete &lt;id&gt;</code> — удалить видео по id
                <code>/list</code> — показать список
                <code>/stats</code> — общая статистика
                <code>/update</code> — обновить статистику по всем видео
                <code>/search &lt;запрос&gt;</code> — поиск по названию
                <code>/help</code> — справка
                """;
    }

    private static String helpMessage() {
        return """
                <b>ℹ️ Справка</b>

                Выбери нужный раздел ниже.
                """;
    }

    private static String helpAddMessage() {
        return """
                <b>➕ Как добавить видео</b>

                Просто отправь в чат ссылку на YouTube.
                Например:

                <code>https://www.youtube.com/watch?v=XXXXXXXXXXX</code>
                """;
    }

    private static String helpDeleteMessage() {
        return """
                <b>🗑️ Как удалить видео</b>

                Вариант 1:
                открой список, зайди в карточку видео и нажми кнопку удаления.

                Вариант 2:
                используй команду
                <code>/delete 3</code>
                """;
    }

    private static String helpSearchMessage() {
        return """
                <b>🔎 Как искать видео</b>

                Используй команду:
                <code>/search часть названия</code>

                Поиск работает без учёта регистра и старается поднимать более точные совпадения выше.
                """;
    }

    private static String unknownMessage() {
        return """
                <b>⚠️ Не удалось понять сообщение.</b>

                Просто отправь ссылку на YouTube
                или используй <code>/search &lt;запрос&gt;</code> для поиска по названию.
                """;
    }

    private static String buildDisplayLabel(StoredVideo video) {
        String baseTitle = video.title() == null || video.title().isBlank()
                ? "Без названия"
                : video.title().trim();
        String shortenedTitle = baseTitle.length() > 36
                ? baseTitle.substring(0, 36).trim() + "..."
                : baseTitle;
        return "▶️ " + escapeHtml(shortenedTitle);
    }

    private static String formatStatus(String status) {
        return switch (status) {
            case "ACTIVE" -> "<b>Доступно</b>";
            case "INVALID_LINK" -> "<b>Ссылка недоступна</b>";
            case "API_ERROR" -> "<b>Ошибка API</b>";
            case "UNAVAILABLE" -> "<b>Временно недоступно</b>";
            default -> "<b>" + escapeHtml(status) + "</b>";
        };
    }

    private static String formatNumber(long value) {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(new Locale("ru", "RU"));
        symbols.setGroupingSeparator(' ');
        DecimalFormat format = new DecimalFormat("#,###", symbols);
        format.setGroupingUsed(true);
        return format.format(value);
    }

    private static String escapeHtml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private static int totalPages(int totalItems) {
        return Math.max(1, (int) Math.ceil((double) totalItems / PAGE_SIZE));
    }

    private static int normalizePage(int page, int totalPages) {
        if (totalPages <= 0) {
            return 0;
        }
        return Math.max(0, Math.min(page, totalPages - 1));
    }

    private static int parseCallbackInt(String data, int index, int defaultValue) {
        try {
            return Integer.parseInt(parseCallbackPart(data, index));
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private static long parseCallbackLong(String data, int index, long defaultValue) {
        try {
            return Long.parseLong(parseCallbackPart(data, index));
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private static String parseCallbackPart(String data, int index) {
        String[] parts = data.split(":");
        return parts.length > index ? parts[index] : "";
    }

    private static CardContext parseCardContext(String data, int startIndex) {
        String[] parts = data.split(":");
        if (parts.length <= startIndex) {
            return CardContext.main();
        }

        String type = parts[startIndex];
        if ("list".equals(type) && parts.length > startIndex + 1) {
            return CardContext.list(parseSafeInt(parts[startIndex + 1], 0));
        }

        if ("search".equals(type) && parts.length > startIndex + 2) {
            return CardContext.search(parts[startIndex + 1], parseSafeInt(parts[startIndex + 2], 0));
        }

        return CardContext.main();
    }

    private static int parseSafeInt(String value, int defaultValue) {
        try {
            return Integer.parseInt(value);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private static String createSearchSession(String query) {
        String token = UUID.randomUUID().toString().substring(0, 8);
        SEARCH_SESSIONS.put(token, new SearchSession(query));
        return token;
    }

    private static void safeExecute(TelegramBot bot, BaseRequest<?, ?> request, String errorMessage) {
        try {
            bot.execute(request);
        } catch (Exception e) {
            System.err.println(errorMessage + " Причина: " + e.getMessage());
        }
    }

    private static void safeAnswerCallback(TelegramBot bot, String callbackId, String text) {
        try {
            AnswerCallbackQuery request = new AnswerCallbackQuery(callbackId);
            if (text != null && !text.isBlank()) {
                request.text(text);
            }
            bot.execute(request);
        } catch (Exception e) {
            System.err.println("Не удалось ответить на callback Telegram. Причина: " + e.getMessage());
        }
    }

    private record CardContext(String type, String token, int page) {
        private static CardContext main() {
            return new CardContext("main", "", 0);
        }

        private static CardContext list(int page) {
            return new CardContext("list", "", page);
        }

        private static CardContext search(String token, int page) {
            return new CardContext("search", token, page);
        }
    }

    private record SearchSession(String query) {
    }
}
