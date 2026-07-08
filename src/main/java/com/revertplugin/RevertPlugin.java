package com.revertplugin;

import com.revertplugin.command.LastServerCommand;
import com.revertplugin.config.ConfigManager;
import com.revertplugin.database.DatabaseManager;
import com.revertplugin.listener.PlayerListener;
import org.bukkit.plugin.java.JavaPlugin;

public final class RevertPlugin extends JavaPlugin {

    private ConfigManager configManager;
    private DatabaseManager databaseManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.configManager = new ConfigManager(getConfig());
        getLogger().info("Configuration loaded. Server: " + configManager.getServerName()
                + ", isLobby: " + configManager.isLobby());

        this.databaseManager = new DatabaseManager(configManager, getLogger());

        PlayerListener playerListener = new PlayerListener(this, databaseManager, configManager);
        getServer().getPluginManager().registerEvents(playerListener, this);

        LastServerCommand command = new LastServerCommand(databaseManager, configManager, getLogger());
        getCommand("lastserver").setExecutor(command);
        getCommand("lastserver").setTabCompleter(command);

        getLogger().info("RevertPlugin enabled successfully!");
    }

    @Override
    public void onDisable() {
        if (databaseManager != null) {
            databaseManager.close();
        }
        getLogger().info("RevertPlugin disabled.");
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }
}
