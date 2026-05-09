package org.example.telegram;

import org.example.model.StoredVideo;
import org.example.model.VideoHistoryEntry;
import org.example.model.VideoPlatform;
import org.example.model.VideoSummary;
import org.example.model.VideoStatus;
import org.example.service.VideoService;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

public class BotViewFormatter {
    private static final int PAGE_SIZE = 5;
    private static final int HISTORY_LIMIT = 5;
    private static final DateTimeFormatter HISTORY_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    public int pageSize() {
        return PAGE_SIZE;
    }

    public int historyLimit() {
        return HISTORY_LIMIT;
    }

    public String startMessage() {
        return """
                <b><tg-emoji emoji-id="5409085785427163977">👋</tg-emoji><tg-emoji emoji-id="5409219084032163788">😃</tg-emoji> Бот запущен и готов к работе.</b>

                Что можно делать:
                • просто отправить ссылку на YouTube или Rutube
                • открыть список видео
                • посмотреть общую статистику и отдельно по платформам
                • искать видео по названию через <code>/search</code>

                Команды:
                <code>/delete &lt;id&gt;</code> — удалить видео по id
                <code>/list</code> — показать список всех видео
                <code>/list_youtube</code> — показать список YouTube-видео
                <code>/list_rutube</code> — показать список Rutube-видео
                <code>/stats</code> — общая статистика
                <code>/update</code> — обновить статистику по всем видео
                <code>/search &lt;запрос&gt;</code> — поиск по названию
                <code>/help</code> — справка
                """;
    }

    public String helpMessage() {
        return """
                <b><tg-emoji emoji-id="5271619747891388291">ℹ️</tg-emoji>️ Справка</b>

                Выбери нужный раздел ниже.
                """;
    }

    public String helpAddMessage() {
        return """
                <b>➕ Как добавить видео</b>

                Просто отправь в чат ссылку на YouTube или Rutube.
                Например:

                <code>https://www.youtube.com/watch?v=XXXXXXXXXXX</code>
                <code>https://rutube.ru/video/xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx/</code>
                """;
    }

    public String helpDeleteMessage() {
        return """
                <b>🗑️ Как удалить видео</b>

                Вариант 1:
                открой список, зайди в карточку видео и нажми кнопку удаления.

                Вариант 2:
                используй команду
                <code>/delete 3</code>
                """;
    }

    public String helpSearchMessage() {
        return """
                <b>🔎 Как искать видео</b>

                Используй команду:
                <code>/search часть названия</code>

                Поиск работает без учёта регистра и старается поднимать более точные совпадения выше.
                """;
    }

    public String unknownMessage() {
        return """
                <b>⚠️ Не удалось понять сообщение.</b>

                Просто отправь ссылку на YouTube или Rutube
                или используй <code>/search &lt;запрос&gt;</code> для поиска по названию.
                """;
    }

    public String missingVideoMessage() {
        return """
                <b>⚠️ Видео не найдено.</b>
                Возможно, оно уже было удалено.
                """;
    }

    public String formatVideoListPage(List<StoredVideo> videos, int page) {
        if (videos.isEmpty()) {
            return """
                    <b>📂 Список пуст.</b>
                    Просто отправь ссылку на YouTube или Rutube, и я добавлю её автоматически.
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
                    .append("Платформа: <b>").append(platformPlainLabel(video.platform())).append("</b>\n")
                    .append("ID: <code>").append(video.id()).append("</code>\n")
                    .append("Статус: ").append(formatStatus(video.status())).append("\n")
                    .append("Просмотры: <b>")
                    .append(video.lastViewCount() == null ? "нет данных" : formatNumber(video.lastViewCount()))
                    .append("</b>\n\n");
        }
        return builder.toString().trim();
    }

    public String formatSearchPage(List<StoredVideo> videos, String query, int page) {
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

    public String formatVideoCard(StoredVideo video, List<VideoHistoryEntry> history) {
        StringBuilder builder = new StringBuilder("""
                <b>🎬 Карточка видео</b>

                <b>%s</b>
                Платформа: <b>%s</b>
                ID: <code>%d</code>
                Статус: %s
                Просмотры: <b>%s</b>
                Ссылка: %s

                <b>📈 История обновлений</b>
                """.formatted(
                buildDisplayLabel(video),
                platformLabel(video.platform()),
                video.id(),
                formatStatus(video.status()),
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
                        .append(formatStatus(entry.status()));

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

    public String formatSummary(VideoSummary summary) {
        return """
                <b>📊 Общая статистика</b>
                Все платформы:
                Ссылок: <b>%d</b>
                Суммарные просмотры: <b>%s</b>

                YouTube:
                Ссылок: <b>%d</b>
                Суммарные просмотры: <b>%s</b>

                Rutube:
                Ссылок: <b>%d</b>
                Суммарные просмотры: <b>%s</b>
                """.formatted(
                summary.totalVideos(),
                formatNumber(summary.totalViews()),
                summary.youtubeVideos(),
                formatNumber(summary.youtubeViews()),
                summary.rutubeVideos(),
                formatNumber(summary.rutubeViews())
        );
    }

    public String formatRefreshResult(VideoService.RefreshAllResult result) {
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

    public String formatRefreshSingleResult(VideoService.RefreshSingleResult result, StoredVideo refreshedVideo) {
        if (refreshedVideo == null) {
            return missingVideoMessage();
        }

        return """
                <b>🎬 Карточка видео</b>

                <b>%s</b>
                Платформа: <b>%s</b>
                ID: <code>%d</code>
                Статус: %s
                Просмотры: <b>%s</b>

                <b>🔄 Последнее обновление:</b> %s
                """.formatted(
                buildDisplayLabel(refreshedVideo),
                platformLabel(refreshedVideo.platform()),
                refreshedVideo.id(),
                formatStatus(refreshedVideo.status()),
                refreshedVideo.lastViewCount() == null ? "нет данных" : formatNumber(refreshedVideo.lastViewCount()),
                escapeHtml(result.message())
        );
    }

    public String formatAddResult(VideoService.AddVideoResult result) {
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

    public String formatDeleteResult(VideoService.DeleteVideoResult result) {
        return """
                <b>🗑️ Результат удаления</b>

                %s
                """.formatted(escapeHtml(result.message()));
    }

    public int totalPages(int totalItems) {
        return Math.max(1, (int) Math.ceil((double) totalItems / PAGE_SIZE));
    }

    public int normalizePage(int page, int totalPages) {
        if (totalPages <= 0) {
            return 0;
        }
        return Math.max(0, Math.min(page, totalPages - 1));
    }

    private String buildDisplayLabel(StoredVideo video) {
        String baseTitle = video.title() == null || video.title().isBlank()
                ? "Без названия"
                : video.title().trim();
        String shortenedTitle = baseTitle.length() > 36
                ? baseTitle.substring(0, 36).trim() + "..."
                : baseTitle;
        return platformIcon(video.platform()) + " " + escapeHtml(shortenedTitle);
    }

    private String formatStatus(VideoStatus status) {
        return switch (status) {
            case ACTIVE -> "<b>Доступно</b>";
            case INVALID_LINK -> "<b>Ссылка недоступна</b>";
            case API_ERROR -> "<b>Ошибка API</b>";
            case UNAVAILABLE -> "<b>Временно недоступно</b>";
        };
    }

    private String formatNumber(long value) {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(new Locale("ru", "RU"));
        symbols.setGroupingSeparator(' ');
        DecimalFormat format = new DecimalFormat("#,###", symbols);
        format.setGroupingUsed(true);
        return format.format(value);
    }

    private String escapeHtml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private String platformLabel(VideoPlatform platform) {
        return switch (platform) {
            case YOUTUBE -> "<tg-emoji emoji-id=\"5278611117130653414\">\u25B6\uFE0F️</tg-emoji>️ YouTube";
            case RUTUBE -> "<tg-emoji emoji-id=\"5298747646096187189\">\uD83D\uDCFA</tg-emoji>";
        };
    }

    private String platformIcon(VideoPlatform platform) {
        return switch (platform) {
            case YOUTUBE -> "<tg-emoji emoji-id=\"5278611117130653414\">\u25B6\uFE0F️</tg-emoji>";
            case RUTUBE -> "<tg-emoji emoji-id=\"5298747646096187189\">\uD83D\uDCFA</tg-emoji>";
        };
    }

    private String platformPlainLabel(VideoPlatform platform) {
        return switch (platform) {
            case YOUTUBE -> "YouTube";
            case RUTUBE -> "Rutube";
        };
    }
}
