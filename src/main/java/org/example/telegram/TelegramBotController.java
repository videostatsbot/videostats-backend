package org.example.telegram;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.model.CallbackQuery;
import com.pengrad.telegrambot.model.LinkPreviewOptions;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.request.ParseMode;
import com.pengrad.telegrambot.request.AnswerCallbackQuery;
import com.pengrad.telegrambot.request.BaseRequest;
import com.pengrad.telegrambot.request.DeleteWebhook;
import com.pengrad.telegrambot.request.EditMessageText;
import com.pengrad.telegrambot.request.SendMessage;
import org.example.model.StoredVideo;
import org.example.service.VideoService;

import java.util.List;

public class TelegramBotController {
    private final TelegramBot bot;
    private final VideoService videoService;
    private final BotViewFormatter formatter;
    private final BotKeyboardFactory keyboardFactory;
    private final SearchSessionStore searchSessionStore;

    public TelegramBotController(TelegramBot bot, VideoService videoService) {
        this.bot = bot;
        this.videoService = videoService;
        this.formatter = new BotViewFormatter();
        this.keyboardFactory = new BotKeyboardFactory(formatter);
        this.searchSessionStore = new SearchSessionStore();
    }

    public void start() {
        safeExecute(new DeleteWebhook().dropPendingUpdates(true), "Не удалось удалить webhook Telegram.");
        bot.setUpdatesListener(this::handleUpdates);
    }

    private int handleUpdates(List<Update> updates) {
        for (Update update : updates) {
            try {
                if (update.callbackQuery() != null) {
                    handleCallback(update.callbackQuery());
                    continue;
                }

                if (update.message() == null || update.message().text() == null) {
                    continue;
                }

                String text = update.message().text().trim();
                long chatId = update.message().chat().id();

                if ("/start".equals(text)) {
                    safeExecute(createMessage(chatId, formatter.startMessage()).replyMarkup(keyboardFactory.mainKeyboard()), "Не удалось отправить стартовое сообщение.");
                } else if ("/help".equals(text)) {
                    safeExecute(createMessage(chatId, formatter.helpMessage()).replyMarkup(keyboardFactory.helpKeyboard()), "Не удалось отправить справку.");
                } else if (text.startsWith("/delete")) {
                    var result = videoService.deleteVideo(extractArgument(text));
                    safeExecute(createMessage(chatId, formatter.formatDeleteResult(result)).replyMarkup(keyboardFactory.mainKeyboard()), "Не удалось отправить результат удаления.");
                } else if ("/list".equals(text)) {
                    showAllList(chatId, 0);
                } else if ("/list_youtube".equals(text)) {
                    showYoutubeList(chatId, 0);
                } else if ("/list_rutube".equals(text)) {
                    showRutubeList(chatId, 0);
                } else if ("/stats".equals(text)) {
                    safeExecute(createMessage(chatId, formatter.formatSummary(videoService.getSummary())).replyMarkup(keyboardFactory.statsKeyboard()), "Не удалось отправить статистику.");
                } else if ("/update".equals(text)) {
                    var refreshResult = videoService.refreshAllVideos();
                    safeExecute(createMessage(chatId, formatter.formatRefreshResult(refreshResult)).replyMarkup(keyboardFactory.mainKeyboard()), "Не удалось отправить результат обновления.");
                } else if (text.startsWith("/search")) {
                    handleSearchCommand(chatId, extractArgument(text));
                } else if (looksLikeUrl(text)) {
                    var result = videoService.addVideo(text);
                    safeExecute(createMessage(chatId, formatter.formatAddResult(result)).replyMarkup(keyboardFactory.mainKeyboard()), "Не удалось отправить результат добавления видео.");
                } else {
                    safeExecute(createMessage(chatId, formatter.unknownMessage()).replyMarkup(keyboardFactory.mainKeyboard()), "Не удалось отправить сообщение о неизвестной команде.");
                }
            } catch (Exception e) {
                System.err.println("Ошибка при обработке обновления Telegram: " + e.getMessage());
            }
        }
        return UpdatesListener.CONFIRMED_UPDATES_ALL;
    }

    private void handleSearchCommand(long chatId, String query) {
        var searchResult = videoService.searchVideos(query);
        if (!searchResult.valid()) {
            safeExecute(createMessage(chatId, searchResult.message()).replyMarkup(keyboardFactory.mainKeyboard()), "Не удалось отправить результат поиска.");
            return;
        }

        String token = searchSessionStore.create(searchResult.query());
        safeExecute(createMessage(chatId, formatter.formatSearchPage(searchResult.videos(), searchResult.query(), 0))
                .replyMarkup(keyboardFactory.searchKeyboard(searchResult.videos(), token, 0)), "Не удалось отправить результат поиска.");
    }

    private void handleCallback(CallbackQuery callbackQuery) {
        String data = callbackQuery.data();
        long chatId = callbackQuery.message().chat().id();
        int messageId = callbackQuery.message().messageId();

        if ("show_list".equals(data)) {
            editAllList(chatId, messageId, 0);
            safeAnswerCallback(callbackQuery.id(), "Показываю список");
            return;
        }

        if ("show_youtube_list".equals(data)) {
            editYoutubeList(chatId, messageId, 0);
            safeAnswerCallback(callbackQuery.id(), "Показываю YouTube");
            return;
        }

        if ("show_rutube_list".equals(data)) {
            editRutubeList(chatId, messageId, 0);
            safeAnswerCallback(callbackQuery.id(), "Показываю Rutube");
            return;
        }

        if ("show_stats".equals(data)) {
            safeExecute(editMessage(chatId, messageId, formatter.formatSummary(videoService.getSummary())).replyMarkup(keyboardFactory.statsKeyboard()), "Не удалось обновить экран статистики.");
            safeAnswerCallback(callbackQuery.id(), "Показываю статистику");
            return;
        }

        if ("show_help".equals(data)) {
            safeExecute(editMessage(chatId, messageId, formatter.helpMessage()).replyMarkup(keyboardFactory.helpKeyboard()), "Не удалось открыть справку.");
            safeAnswerCallback(callbackQuery.id(), "Открываю справку");
            return;
        }

        if ("help_add".equals(data)) {
            safeExecute(editMessage(chatId, messageId, formatter.helpAddMessage()).replyMarkup(keyboardFactory.helpDetailsKeyboard()), "Не удалось открыть инструкцию по добавлению.");
            safeAnswerCallback(callbackQuery.id(), "Показываю инструкцию");
            return;
        }

        if ("help_delete".equals(data)) {
            safeExecute(editMessage(chatId, messageId, formatter.helpDeleteMessage()).replyMarkup(keyboardFactory.helpDetailsKeyboard()), "Не удалось открыть инструкцию по удалению.");
            safeAnswerCallback(callbackQuery.id(), "Показываю инструкцию");
            return;
        }

        if ("help_search".equals(data)) {
            safeExecute(editMessage(chatId, messageId, formatter.helpSearchMessage()).replyMarkup(keyboardFactory.helpDetailsKeyboard()), "Не удалось открыть инструкцию по поиску.");
            safeAnswerCallback(callbackQuery.id(), "Показываю инструкцию");
            return;
        }

        if ("help_back".equals(data)) {
            safeExecute(editMessage(chatId, messageId, formatter.helpMessage()).replyMarkup(keyboardFactory.helpKeyboard()), "Не удалось вернуться к справке.");
            safeAnswerCallback(callbackQuery.id(), "Возвращаюсь назад");
            return;
        }

        if ("back_main".equals(data)) {
            safeExecute(editMessage(chatId, messageId, formatter.startMessage()).replyMarkup(keyboardFactory.mainKeyboard()), "Не удалось вернуться в главное меню.");
            safeAnswerCallback(callbackQuery.id(), "Возвращаюсь в меню");
            return;
        }

        if ("noop".equals(data)) {
            safeAnswerCallback(callbackQuery.id(), "Здесь больше нечего открывать");
            return;
        }

        if (data != null && data.startsWith("list_page:")) {
            int page = CallbackDataParser.parseInt(data, 1, 0);
            editAllList(chatId, messageId, page);
            safeAnswerCallback(callbackQuery.id(), null);
            return;
        }

        if (data != null && data.startsWith("list_youtube_page:")) {
            int page = CallbackDataParser.parseInt(data, 1, 0);
            editYoutubeList(chatId, messageId, page);
            safeAnswerCallback(callbackQuery.id(), null);
            return;
        }

        if (data != null && data.startsWith("list_rutube_page:")) {
            int page = CallbackDataParser.parseInt(data, 1, 0);
            editRutubeList(chatId, messageId, page);
            safeAnswerCallback(callbackQuery.id(), null);
            return;
        }

        if (data != null && data.startsWith("search_page:")) {
            String token = CallbackDataParser.parsePart(data, 1);
            int page = CallbackDataParser.parseInt(data, 2, 0);
            String query = searchSessionStore.getQuery(token);
            if (query == null) {
                safeAnswerCallback(callbackQuery.id(), "Поиск устарел, повтори команду /search");
                return;
            }

            var searchResult = videoService.searchVideos(query);
            safeExecute(editMessage(chatId, messageId, formatter.formatSearchPage(searchResult.videos(), query, page))
                    .replyMarkup(keyboardFactory.searchKeyboard(searchResult.videos(), token, page)), "Не удалось перелистнуть результаты поиска.");
            safeAnswerCallback(callbackQuery.id(), null);
            return;
        }

        if (data != null && data.startsWith("open_video:")) {
            long id = CallbackDataParser.parseLong(data, 1, -1);
            CardContext context = CallbackDataParser.parseCardContext(data, 2);
            editCard(chatId, messageId, id, context);
            safeAnswerCallback(callbackQuery.id(), "Открываю карточку");
            return;
        }

        if (data != null && data.startsWith("refresh_video:")) {
            long id = CallbackDataParser.parseLong(data, 1, -1);
            CardContext context = CallbackDataParser.parseCardContext(data, 2);
            var result = videoService.refreshVideoById(id);
            safeExecute(editMessage(chatId, messageId, formatter.formatRefreshSingleResult(result, videoService.getVideoById(id)))
                    .replyMarkup(keyboardFactory.videoCardKeyboard(id, context)), "Не удалось обновить карточку видео.");
            safeAnswerCallback(callbackQuery.id(), "Видео обновлено");
            return;
        }

        if (data != null && data.startsWith("delete_from_card:")) {
            long id = CallbackDataParser.parseLong(data, 1, -1);
            CardContext context = CallbackDataParser.parseCardContext(data, 2);
            var result = videoService.deleteVideo(String.valueOf(id));
            safeExecute(editMessage(chatId, messageId, formatter.formatDeleteResult(result)).replyMarkup(keyboardFactory.backKeyboard(context)), "Не удалось обновить экран после удаления.");
            safeAnswerCallback(callbackQuery.id(), "Видео удалено");
            return;
        }

        if ("refresh_all".equals(data)) {
            var refreshResult = videoService.refreshAllVideos();
            safeExecute(editMessage(chatId, messageId, formatter.formatRefreshResult(refreshResult)).replyMarkup(keyboardFactory.mainKeyboard()), "Не удалось обновить статистику.");
            safeAnswerCallback(callbackQuery.id(), "Статистика обновлена");
            return;
        }

        safeAnswerCallback(callbackQuery.id(), "Неизвестное действие");
    }

    private void showAllList(long chatId, int page) {
        List<StoredVideo> videos = videoService.getAllVideos();
        safeExecute(createMessage(chatId, formatter.formatVideoListPage(videos, page)).replyMarkup(keyboardFactory.listKeyboard(videos, page, CardContext.allList(page))), "Не удалось отправить список видео.");
    }

    private void showYoutubeList(long chatId, int page) {
        List<StoredVideo> videos = videoService.getYoutubeVideos();
        safeExecute(createMessage(chatId, formatter.formatVideoListPage(videos, page)).replyMarkup(keyboardFactory.listKeyboard(videos, page, CardContext.youtubeList(page))), "Не удалось отправить список YouTube-видео.");
    }

    private void showRutubeList(long chatId, int page) {
        List<StoredVideo> videos = videoService.getRutubeVideos();
        safeExecute(createMessage(chatId, formatter.formatVideoListPage(videos, page)).replyMarkup(keyboardFactory.listKeyboard(videos, page, CardContext.rutubeList(page))), "Не удалось отправить список Rutube-видео.");
    }

    private void editAllList(long chatId, int messageId, int page) {
        List<StoredVideo> videos = videoService.getAllVideos();
        safeExecute(editMessage(chatId, messageId, formatter.formatVideoListPage(videos, page)).replyMarkup(keyboardFactory.listKeyboard(videos, page, CardContext.allList(page))), "Не удалось обновить список видео.");
    }

    private void editYoutubeList(long chatId, int messageId, int page) {
        List<StoredVideo> videos = videoService.getYoutubeVideos();
        safeExecute(editMessage(chatId, messageId, formatter.formatVideoListPage(videos, page)).replyMarkup(keyboardFactory.listKeyboard(videos, page, CardContext.youtubeList(page))), "Не удалось обновить список YouTube-видео.");
    }

    private void editRutubeList(long chatId, int messageId, int page) {
        List<StoredVideo> videos = videoService.getRutubeVideos();
        safeExecute(editMessage(chatId, messageId, formatter.formatVideoListPage(videos, page)).replyMarkup(keyboardFactory.listKeyboard(videos, page, CardContext.rutubeList(page))), "Не удалось обновить список Rutube-видео.");
    }

    private void editCard(long chatId, int messageId, long id, CardContext context) {
        StoredVideo video = videoService.getVideoById(id);
        if (video == null) {
            safeExecute(editMessage(chatId, messageId, formatter.missingVideoMessage()).replyMarkup(keyboardFactory.backKeyboard(context)), "Не удалось показать сообщение об отсутствии видео.");
            return;
        }

        safeExecute(editMessage(chatId, messageId, formatter.formatVideoCard(video, videoService.getVideoHistory(video.id(), formatter.historyLimit())))
                .replyMarkup(keyboardFactory.videoCardKeyboard(video.id(), context)), "Не удалось открыть карточку видео.");
    }

    private String extractArgument(String commandText) {
        int firstSpaceIndex = commandText.indexOf(' ');
        if (firstSpaceIndex < 0 || firstSpaceIndex == commandText.length() - 1) {
            return "";
        }
        return commandText.substring(firstSpaceIndex + 1).trim();
    }

    private boolean looksLikeUrl(String text) {
        return text.startsWith("http://") || text.startsWith("https://");
    }

    private SendMessage createMessage(long chatId, String text) {
        return new SendMessage(chatId, text)
                .parseMode(ParseMode.HTML)
                .linkPreviewOptions(new LinkPreviewOptions().isDisabled(true));
    }

    private EditMessageText editMessage(long chatId, int messageId, String text) {
        return new EditMessageText(chatId, messageId, text)
                .parseMode(ParseMode.HTML)
                .linkPreviewOptions(new LinkPreviewOptions().isDisabled(true));
    }

    private void safeExecute(BaseRequest<?, ?> request, String errorMessage) {
        try {
            bot.execute(request);
        } catch (Exception e) {
            System.err.println(errorMessage + " Причина: " + e.getMessage());
        }
    }

    private void safeAnswerCallback(String callbackId, String text) {
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
}
