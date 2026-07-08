package com.revertplugin.listener;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.revertplugin.RevertPlugin;
import com.revertplugin.config.ConfigManager;
import com.revertplugin.database.DatabaseManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerListener implements Listener {

    private final RevertPlugin plugin;
    private final DatabaseManager database;
    private final ConfigManager config;

    private final Set<UUID> pendingRedirects = ConcurrentHashMap.newKeySet();

    public PlayerListener(RevertPlugin plugin, DatabaseManager database, ConfigManager config) {
        this.plugin = plugin;
        this.database = database;
        this.config = config;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        String serverName = config.getServerName();

        if (config.isDebug()) {
            plugin.getLogger().info("[Debug] " + player.getName() + " joined. Server: " + serverName
                    + ", isLobby: " + config.isLobby());
        }

        if (config.isLobby()) {
            scheduleRedirectCheck(player);
        } else {
            database.saveLastServer(uuid, serverName);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (pendingRedirects.remove(uuid)) {
            if (config.isDebug()) {
                plugin.getLogger().info("[Debug] Removed pending redirect for " + player.getName()
                        + " (player quit)");
            }
        }
    }

    private void scheduleRedirectCheck(Player player) {
        UUID uuid = player.getUniqueId();

        database.getPlayerData(uuid).thenAccept(data -> {
            if (!player.isOnline()) {
                return;
            }

            if (!data.autoReturn()) {
                if (config.isDebug()) {
                    plugin.getLogger().info("[Debug] " + player.getName()
                            + " has auto-return disabled. Staying in lobby.");
                }
                saveAndStay(player, uuid);
                return;
            }

            String lastServer = data.lastServer();

            if (!data.hasLastServer()) {
                if (config.isDebug()) {
                    plugin.getLogger().info("[Debug] " + player.getName()
                            + " has no last server. Staying in lobby.");
                }
                saveAndStay(player, uuid);
                return;
            }

            if (lastServer.equalsIgnoreCase(config.getServerName())) {
                if (config.isDebug()) {
                    plugin.getLogger().info("[Debug] " + player.getName()
                            + " was last on this server. Staying.");
                }
                saveAndStay(player, uuid);
                return;
            }

            if (!config.getAllowedServers().contains(lastServer)) {
                if (config.isDebug()) {
                    plugin.getLogger().info("[Debug] " + player.getName()
                            + " last server '" + lastServer + "' is not in allowed list.");
                }
                saveAndStay(player, uuid);
                return;
            }

            scheduleRedirect(player, lastServer);

        }).exceptionally(throwable -> {
            plugin.getLogger().warning("Error checking player data for " + player.getName()
                    + ": " + throwable.getMessage());
            return null;
        });
    }

    private void saveAndStay(Player player, UUID uuid) {
        database.saveLastServer(uuid, config.getServerName());
    }

    private void scheduleRedirect(Player player, String serverName) {
        UUID uuid = player.getUniqueId();

        if (!pendingRedirects.add(uuid)) {
            return;
        }

        int delayTicks = config.getRedirectDelayTicks();

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                pendingRedirects.remove(uuid);
                return;
            }

            if (!pendingRedirects.contains(uuid)) {
                return;
            }

            pendingRedirects.remove(uuid);

            try {
                ByteArrayDataOutput out = ByteStreams.newDataOutput();
                out.writeUTF("Connect");
                out.writeUTF(serverName);
                player.sendPluginMessage(plugin, "BungeeCord", out.toByteArray());

                plugin.getLogger().info("Redirected " + player.getName()
                        + " to " + serverName + " via BungeeCord");
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to redirect " + player.getName()
                        + " to " + serverName + ": " + e.getMessage());
            }
        }, delayTicks);
    }
}
