package org.example.telegram;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SearchSessionStore {
    private final Map<String, SearchSession> sessions = new ConcurrentHashMap<>();

    public String create(String query) {
        String token = UUID.randomUUID().toString().substring(0, 8);
        sessions.put(token, new SearchSession(query));
        return token;
    }

    public String getQuery(String token) {
        SearchSession session = sessions.get(token);
        return session == null ? null : session.query();
    }

    private record SearchSession(String query) {
    }
}
