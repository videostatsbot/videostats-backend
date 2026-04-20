package org.example.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.example.config.AppConfig;

import javax.sql.DataSource;

public final class DatabaseFactory {
    private DatabaseFactory() {
    }

    public static HikariDataSource createDataSource(AppConfig config) {
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(config.databaseUrl());
        hikariConfig.setUsername(config.databaseUser());
        hikariConfig.setPassword(config.databasePassword());
        hikariConfig.setMaximumPoolSize(5);
        hikariConfig.setMinimumIdle(1);
        hikariConfig.setPoolName("tg-bot-video-stats-pool");

        return new HikariDataSource(hikariConfig);
    }

    public static void checkConnection(DataSource dataSource) {
        try (var connection = dataSource.getConnection()) {
            // Opening a connection is enough to verify the database is reachable.
        } catch (Exception e) {
            throw new IllegalStateException("Failed to connect to PostgreSQL", e);
        }
    }
}
