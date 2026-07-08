package com.revertplugin.database;

import com.revertplugin.config.ConfigManager;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DatabaseManager {

    private static final String TABLE_NAME = "last_server_data";

    private final Logger logger;
    private HikariDataSource dataSource;

    public DatabaseManager(ConfigManager config, Logger logger) {
        this.logger = logger;

        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(String.format(
                "jdbc:mysql://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=utf8",
                config.getMysqlHost(),
                config.getMysqlPort(),
                config.getMysqlDatabase()
        ));
        hikariConfig.setUsername(config.getMysqlUsername());
        hikariConfig.setPassword(config.getMysqlPassword());
        hikariConfig.setMaximumPoolSize(config.getMysqlMaxPoolSize());
        hikariConfig.setConnectionTimeout(config.getMysqlConnectionTimeout());
        hikariConfig.setMinimumIdle(2);
        hikariConfig.setIdleTimeout(30_000);
        hikariConfig.setMaxLifetime(600_000);
        hikariConfig.setPoolName("RevertPlugin-Hikari");

        try {
            dataSource = new HikariDataSource(hikariConfig);
            createTable();
            logger.info("MySQL connection pool initialized successfully.");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to initialize MySQL connection pool!", e);
        }
    }

    private void createTable() {
        String sql = "CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " ("
                + "uuid VARCHAR(36) PRIMARY KEY, "
                + "last_server VARCHAR(64) NOT NULL DEFAULT '', "
                + "auto_return BOOLEAN NOT NULL DEFAULT TRUE, "
                + "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.execute();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to create table!", e);
        }
    }

    private Connection getConnection() throws SQLException {
        if (dataSource == null || dataSource.isClosed()) {
            throw new SQLException("DataSource is not initialized or is closed.");
        }
        return dataSource.getConnection();
    }

    public CompletableFuture<Void> saveLastServer(UUID uuid, String serverName) {
        return CompletableFuture.runAsync(() -> {
            String sql = "INSERT INTO " + TABLE_NAME + " (uuid, last_server) VALUES (?, ?) "
                    + "ON DUPLICATE KEY UPDATE last_server = ?, updated_at = CURRENT_TIMESTAMP";

            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, uuid.toString());
                stmt.setString(2, serverName);
                stmt.setString(3, serverName);
                stmt.executeUpdate();
            } catch (SQLException e) {
                logger.log(Level.WARNING, "Failed to save last server for " + uuid, e);
            }
        });
    }

    public CompletableFuture<PlayerData> getPlayerData(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT last_server, auto_return FROM " + TABLE_NAME + " WHERE uuid = ?";

            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, uuid.toString());

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        String lastServer = rs.getString("last_server");
                        boolean autoReturn = rs.getBoolean("auto_return");
                        return new PlayerData(lastServer, autoReturn);
                    }
                }
            } catch (SQLException e) {
                logger.log(Level.WARNING, "Failed to get player data for " + uuid, e);
            }

            return new PlayerData("", true);
        });
    }

    public CompletableFuture<Void> setAutoReturn(UUID uuid, boolean enabled) {
        return CompletableFuture.runAsync(() -> {
            String sql = "INSERT INTO " + TABLE_NAME + " (uuid, last_server, auto_return) VALUES (?, '', ?) "
                    + "ON DUPLICATE KEY UPDATE auto_return = ?, updated_at = CURRENT_TIMESTAMP";

            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, uuid.toString());
                stmt.setBoolean(2, enabled);
                stmt.setBoolean(3, enabled);
                stmt.executeUpdate();
            } catch (SQLException e) {
                logger.log(Level.WARNING, "Failed to set auto_return for " + uuid, e);
            }
        });
    }

    public CompletableFuture<Boolean> hasPlayerData(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT 1 FROM " + TABLE_NAME + " WHERE uuid = ?";

            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, uuid.toString());

                try (ResultSet rs = stmt.executeQuery()) {
                    return rs.next();
                }
            } catch (SQLException e) {
                logger.log(Level.WARNING, "Failed to check player data for " + uuid, e);
                return false;
            }
        });
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            logger.info("MySQL connection pool closed.");
        }
    }

    public record PlayerData(String lastServer, boolean autoReturn) {
        public boolean hasLastServer() {
            return lastServer != null && !lastServer.isEmpty();
        }
    }
}
