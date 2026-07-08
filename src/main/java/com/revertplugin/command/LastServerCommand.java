package com.revertplugin.command;

import com.revertplugin.config.ConfigManager;
import com.revertplugin.database.DatabaseManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class LastServerCommand implements TabExecutor {

    private static final List<String> SUBCOMMANDS = Arrays.asList("disable", "enable", "status");

    private final DatabaseManager database;
    private final ConfigManager config;
    private final Logger logger;

    public LastServerCommand(DatabaseManager database, ConfigManager config, Logger logger) {
        this.database = database;
        this.config = config;
        this.logger = logger;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {

        if (!sender.hasPermission("revertplugin.admin")) {
            sender.sendMessage("§cУ вас нет прав на использование этой команды.");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage("§6Использование: /lastserver <disable|enable|status> <игрок>");
            return true;
        }

        String subCommand = args[0].toLowerCase();
        String playerName = args[1];

        if (!SUBCOMMANDS.contains(subCommand)) {
            sender.sendMessage("§6Неизвестная субкоманда. Используйте: disable, enable, status");
            return true;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayerIfCached(playerName);
        if (target == null) {
            Player online = Bukkit.getPlayer(playerName);
            if (online == null) {
                sender.sendMessage("§cИгрок §e" + playerName + " §cне найден (никогда не заходил на сервер).");
                return true;
            }
            target = online;
        }

        UUID uuid = target.getUniqueId();

        switch (subCommand) {
            case "disable" -> handleDisable(sender, uuid, playerName);
            case "enable" -> handleEnable(sender, uuid, playerName);
            case "status" -> handleStatus(sender, uuid, playerName);
        }

        return true;
    }

    private void handleDisable(CommandSender sender, UUID uuid, String playerName) {
        database.setAutoReturn(uuid, false).thenRun(() -> {
            sender.sendMessage("§aАвтоматический возврат для игрока §e" + playerName + " §aотключён.");

            Player online = Bukkit.getPlayer(uuid);
            if (online != null) {
                online.sendMessage("§cАвтоматический возврат на последний сервер отключён.");
            }

            if (config.isDebug()) {
                logger.info("[Debug] Auto-return disabled for " + playerName + " (" + uuid + ")");
            }
        }).exceptionally(throwable -> {
            sender.sendMessage("§cОшибка при отключении авто-возврата для §e" + playerName
                    + "§c: " + throwable.getMessage());
            return null;
        });
    }

    private void handleEnable(CommandSender sender, UUID uuid, String playerName) {
        database.setAutoReturn(uuid, true).thenRun(() -> {
            sender.sendMessage("§aАвтоматический возврат для игрока §e" + playerName + " §aвключён.");

            Player online = Bukkit.getPlayer(uuid);
            if (online != null) {
                online.sendMessage("§aАвтоматический возврат на последний сервер включён.");
            }

            if (config.isDebug()) {
                logger.info("[Debug] Auto-return enabled for " + playerName + " (" + uuid + ")");
            }
        }).exceptionally(throwable -> {
            sender.sendMessage("§cОшибка при включении авто-возврата для §e" + playerName
                    + "§c: " + throwable.getMessage());
            return null;
        });
    }

    private void handleStatus(CommandSender sender, UUID uuid, String playerName) {
        database.getPlayerData(uuid).thenAccept(data -> {
            sender.sendMessage("§6=== Статус игрока §e" + playerName + " §6===");
            if (data.hasLastServer()) {
                sender.sendMessage("§6Последний сервер: §e" + data.lastServer());
            } else {
                sender.sendMessage("§6Последний сервер: §cне найден");
            }

            if (data.autoReturn()) {
                sender.sendMessage("§6Авто-возврат: §aвключён");
            } else {
                sender.sendMessage("§6Авто-возврат: §cотключён");
            }

            if (data.hasLastServer() && data.autoReturn()) {
                if (config.getAllowedServers().contains(data.lastServer())) {
                    sender.sendMessage("§6Будет перенаправлен на §e" + data.lastServer()
                            + " §a(разрешён)");
                } else {
                    sender.sendMessage("§6Сервер §e" + data.lastServer()
                            + " §cне в списке разрешённых для перенаправления");
                }
            }

            if (config.isDebug()) {
                logger.info("[Debug] Status checked for " + playerName + " (" + uuid + ")");
            }
        }).exceptionally(throwable -> {
            sender.sendMessage("§cОшибка при получении статуса для §e" + playerName
                    + "§c: " + throwable.getMessage());
            return null;
        });
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender,
                                                 @NotNull Command command,
                                                 @NotNull String label,
                                                 @NotNull String[] args) {
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            return SUBCOMMANDS.stream()
                    .filter(s -> s.startsWith(partial))
                    .collect(Collectors.toList());
        }

        if (args.length == 2) {
            String partial = args[1].toLowerCase();
            List<String> names = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase().startsWith(partial)) {
                    names.add(player.getName());
                }
            }
            return names;
        }

        return Collections.emptyList();
    }
}
