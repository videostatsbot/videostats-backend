package org.example.telegram;

import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;
import org.example.model.StoredVideo;

import java.util.List;

public class BotKeyboardFactory {
    private final BotViewFormatter formatter;

    public BotKeyboardFactory(BotViewFormatter formatter) {
        this.formatter = formatter;
    }

    public InlineKeyboardMarkup mainKeyboard() {
        return new InlineKeyboardMarkup(
                new InlineKeyboardButton[]{
                        new InlineKeyboardButton("Список").callbackData("show_list"),
                        new InlineKeyboardButton("Статистика").callbackData("show_stats")
                },
                new InlineKeyboardButton[]{
                        new InlineKeyboardButton("YouTube-видео").callbackData("show_youtube_list"),
                        new InlineKeyboardButton("Rutube-видео").callbackData("show_rutube_list")
                },
                new InlineKeyboardButton[]{
                        new InlineKeyboardButton("🔄 Обновить статистику").callbackData("refresh_all")
                },
                new InlineKeyboardButton[]{
                        new InlineKeyboardButton("Справка").callbackData("show_help")
                }
        );
    }

    public InlineKeyboardMarkup statsKeyboard() {
        return new InlineKeyboardMarkup(
                new InlineKeyboardButton[]{
                        new InlineKeyboardButton("🔄 Обновить статистику").callbackData("refresh_all")
                },
                new InlineKeyboardButton[]{
                        new InlineKeyboardButton("Назад").callbackData("back_main")
                }
        );
    }

    public InlineKeyboardMarkup helpKeyboard() {
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

    public InlineKeyboardMarkup helpDetailsKeyboard() {
        return new InlineKeyboardMarkup(
                new InlineKeyboardButton[]{
                        new InlineKeyboardButton("Назад").callbackData("help_back"),
                        new InlineKeyboardButton("В меню").callbackData("back_main")
                }
        );
    }

    public InlineKeyboardMarkup listKeyboard(List<StoredVideo> videos, int page, CardContext context) {
        if (videos.isEmpty()) {
            return mainKeyboard();
        }

        int totalPages = formatter.totalPages(videos.size());
        int normalizedPage = formatter.normalizePage(page, totalPages);
        int fromIndex = normalizedPage * formatter.pageSize();
        int toIndex = Math.min(fromIndex + formatter.pageSize(), videos.size());

        InlineKeyboardButton[][] rows = new InlineKeyboardButton[(toIndex - fromIndex) + 2][];
        int rowIndex = 0;

        for (int i = fromIndex; i < toIndex; i++) {
            StoredVideo video = videos.get(i);
            rows[rowIndex++] = new InlineKeyboardButton[]{
                    new InlineKeyboardButton("Открыть ID " + video.id())
                            .callbackData("open_video:" + video.id() + ":" + context.type() + ":" + normalizedPage)
            };
        }

        rows[rowIndex++] = new InlineKeyboardButton[]{
                normalizedPage == 0
                        ? new InlineKeyboardButton("·").callbackData("noop")
                        : new InlineKeyboardButton("<").callbackData(listPageAction(context.type(), normalizedPage - 1)),
                new InlineKeyboardButton((normalizedPage + 1) + "/" + totalPages).callbackData("noop"),
                normalizedPage >= totalPages - 1
                        ? new InlineKeyboardButton("·").callbackData("noop")
                        : new InlineKeyboardButton(">").callbackData(listPageAction(context.type(), normalizedPage + 1))
        };

        rows[rowIndex] = new InlineKeyboardButton[]{
                new InlineKeyboardButton("Назад").callbackData("back_main")
        };

        return new InlineKeyboardMarkup(rows);
    }

    public InlineKeyboardMarkup searchKeyboard(List<StoredVideo> videos, String token, int page) {
        if (videos.isEmpty()) {
            return mainKeyboard();
        }

        int totalPages = formatter.totalPages(videos.size());
        int normalizedPage = formatter.normalizePage(page, totalPages);
        int fromIndex = normalizedPage * formatter.pageSize();
        int toIndex = Math.min(fromIndex + formatter.pageSize(), videos.size());

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

    public InlineKeyboardMarkup videoCardKeyboard(long id, CardContext context) {
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

    public InlineKeyboardMarkup backKeyboard(CardContext context) {
        return new InlineKeyboardMarkup(new InlineKeyboardButton[]{
                backButton(context)
        });
    }

    private InlineKeyboardButton backButton(CardContext context) {
        return switch (context.type()) {
            case "list_all", "list_youtube", "list_rutube" ->
                    new InlineKeyboardButton("Назад к списку").callbackData(listPageAction(context.type(), context.page()));
            case "search" -> new InlineKeyboardButton("Назад к поиску").callbackData("search_page:" + context.token() + ":" + context.page());
            default -> new InlineKeyboardButton("В меню").callbackData("back_main");
        };
    }

    private String contextualAction(String prefix, long id, CardContext context) {
        return switch (context.type()) {
            case "list_all", "list_youtube", "list_rutube" -> prefix + ":" + id + ":" + context.type() + ":" + context.page();
            case "search" -> prefix + ":" + id + ":search:" + context.token() + ":" + context.page();
            default -> prefix + ":" + id + ":main";
        };
    }

    private String listPageAction(String listType, int page) {
        return switch (listType) {
            case "list_youtube" -> "list_youtube_page:" + page;
            case "list_rutube" -> "list_rutube_page:" + page;
            default -> "list_page:" + page;
        };
    }
}
