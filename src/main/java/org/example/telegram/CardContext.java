package org.example.telegram;

public record CardContext(String type, String token, int page) {
    public static CardContext main() {
        return new CardContext("main", "", 0);
    }

    public static CardContext allList(int page) {
        return new CardContext("list_all", "", page);
    }

    public static CardContext youtubeList(int page) {
        return new CardContext("list_youtube", "", page);
    }

    public static CardContext rutubeList(int page) {
        return new CardContext("list_rutube", "", page);
    }

    public static CardContext search(String token, int page) {
        return new CardContext("search", token, page);
    }
}
