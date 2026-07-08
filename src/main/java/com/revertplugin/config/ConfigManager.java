package com.revertplugin.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ConfigManager {

    private final String mysqlHost;
    private final int mysqlPort;
    private final String mysqlDatabase;
    private final String mysqlUsername;
    private final String mysqlPassword;
    private final int mysqlMaxPoolSize;
    private final long mysqlConnectionTimeout;

    private final String serverName;
    private final boolean isLobby;

    private final Map<String, ServerInfo> servers = new HashMap<>();

    private final int redirectDelayTicks;
    private final List<String> allowedServers;

    private final boolean debug;

    public ConfigManager(FileConfiguration config) {
        ConfigurationSection mysqlSection = config.getConfigurationSection("mysql");
        if (mysqlSection != null) {
            this.mysqlHost = mysqlSection.getString("host", "127.0.0.1");
            this.mysqlPort = mysqlSection.getInt("port", 3306);
            this.mysqlDatabase = mysqlSection.getString("database", "minecraft");
            this.mysqlUsername = mysqlSection.getString("username", "root");
            this.mysqlPassword = mysqlSection.getString("password", "");

            ConfigurationSection poolSection = mysqlSection.getConfigurationSection("pool");
            if (poolSection != null) {
                this.mysqlMaxPoolSize = poolSection.getInt("maximum-pool-size", 10);
                this.mysqlConnectionTimeout = poolSection.getLong("connection-timeout", 5000);
            } else {
                this.mysqlMaxPoolSize = 10;
                this.mysqlConnectionTimeout = 5000;
            }
        } else {
            this.mysqlHost = "127.0.0.1";
            this.mysqlPort = 3306;
            this.mysqlDatabase = "minecraft";
            this.mysqlUsername = "root";
            this.mysqlPassword = "";
            this.mysqlMaxPoolSize = 10;
            this.mysqlConnectionTimeout = 5000;
        }

        ConfigurationSection serverSection = config.getConfigurationSection("server");
        if (serverSection != null) {
            this.serverName = serverSection.getString("name", "lobby");
            this.isLobby = serverSection.getBoolean("is-lobby", false);
        } else {
            this.serverName = "lobby";
            this.isLobby = false;
        }

        ConfigurationSection serversSection = config.getConfigurationSection("servers");
        if (serversSection != null) {
            for (String key : serversSection.getKeys(false)) {
                String host = serversSection.getString(key + ".host");
                int port = serversSection.getInt(key + ".port", 25565);
                if (host != null) {
                    servers.put(key.toLowerCase(), new ServerInfo(host, port));
                }
            }
        }

        ConfigurationSection redirectSection = config.getConfigurationSection("redirect");
        if (redirectSection != null) {
            this.redirectDelayTicks = redirectSection.getInt("delay-ticks", 20);
            this.allowedServers = redirectSection.getStringList("allowed-servers");
            this.allowedServers.replaceAll(String::toLowerCase);
        } else {
            this.redirectDelayTicks = 20;
            this.allowedServers = Collections.emptyList();
        }

        this.debug = config.getBoolean("debug", false);
    }

    public String getMysqlHost() {
        return mysqlHost;
    }

    public int getMysqlPort() {
        return mysqlPort;
    }

    public String getMysqlDatabase() {
        return mysqlDatabase;
    }

    public String getMysqlUsername() {
        return mysqlUsername;
    }

    public String getMysqlPassword() {
        return mysqlPassword;
    }

    public int getMysqlMaxPoolSize() {
        return mysqlMaxPoolSize;
    }

    public long getMysqlConnectionTimeout() {
        return mysqlConnectionTimeout;
    }

    public String getServerName() {
        return serverName;
    }

    public boolean isLobby() {
        return isLobby;
    }

    public ServerInfo getServerInfo(String serverName) {
        return servers.get(serverName.toLowerCase());
    }

    public Map<String, ServerInfo> getServers() {
        return Collections.unmodifiableMap(servers);
    }

    public int getRedirectDelayTicks() {
        return redirectDelayTicks;
    }

    public List<String> getAllowedServers() {
        return allowedServers;
    }

    public boolean isDebug() {
        return debug;
    }

    public record ServerInfo(String host, int port) {
    }
}
