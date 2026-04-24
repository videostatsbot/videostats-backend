package org.example.telegram;

import java.util.Set;

// TG-ID юзеров, кому даем доступ к использованию. Остальные в игнор
public final class AuthManager {
    private static final Set<Long> ALLOWED_USERS = Set.of(
            1000000001L,
            1000000002L
    );

    private AuthManager() {
    }

    public static boolean isAllowed(long telegramUserId) {
        return ALLOWED_USERS.contains(telegramUserId);
    }
}