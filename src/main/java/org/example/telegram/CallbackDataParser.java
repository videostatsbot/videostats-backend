package org.example.telegram;

public final class CallbackDataParser {
    private CallbackDataParser() {
    }

    public static int parseInt(String data, int index, int defaultValue) {
        try {
            return Integer.parseInt(parsePart(data, index));
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public static long parseLong(String data, int index, long defaultValue) {
        try {
            return Long.parseLong(parsePart(data, index));
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public static String parsePart(String data, int index) {
        String[] parts = data.split(":");
        return parts.length > index ? parts[index] : "";
    }

    public static CardContext parseCardContext(String data, int startIndex) {
        String[] parts = data.split(":");
        if (parts.length <= startIndex) {
            return CardContext.main();
        }

        String type = parts[startIndex];
        if ("list_all".equals(type) && parts.length > startIndex + 1) {
            return CardContext.allList(parseSafeInt(parts[startIndex + 1], 0));
        }

        if ("list_youtube".equals(type) && parts.length > startIndex + 1) {
            return CardContext.youtubeList(parseSafeInt(parts[startIndex + 1], 0));
        }

        if ("list_rutube".equals(type) && parts.length > startIndex + 1) {
            return CardContext.rutubeList(parseSafeInt(parts[startIndex + 1], 0));
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
}
