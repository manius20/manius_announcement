package com.example.maniusannouncements;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;

public final class ManiusAnnouncements extends JavaPlugin implements CommandExecutor {

    private final List<BukkitTask> activeTasks = new ArrayList<>();
    private final List<BossBar> activeBossBars = new ArrayList<>();
    private BukkitTask activeActionBarTask = null;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        registerCommand("maniusactionbar");
        registerCommand("maniusbossbar");
        registerCommand("maniustitle");
        registerCommand("maniusannouncements");
        registerCommand("maniusbossbarusun");
        registerCommand("maniusactionbarusun");

        startAutoAnnouncers();
    }

    private void registerCommand(String name) {
        if (getCommand(name) != null) {
            getCommand(name).setExecutor(this);
        }
    }

    @Override
    public void onDisable() {
        stopAutoAnnouncers();
        clearActiveBossBars();
        stopActiveActionBar();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("manius.admin")) {
            sender.sendMessage(parseColor(getConfig().getString("prefix", "") + getConfig().getString("messages.no-permission")));
            return true;
        }

        String cmd = command.getName().toLowerCase();

        if (cmd.equals("maniusbossbarusun")) {
            clearActiveBossBars();
            sender.sendMessage(parseColor(getConfig().getString("prefix", "") + "&aPomyślnie usunięto wszystkie aktywne BossBary."));
            return true;
        }

        if (cmd.equals("maniusactionbarusun")) {
            stopActiveActionBar();
            sender.sendMessage(parseColor(getConfig().getString("prefix", "") + "&aPomyślnie zatrzymano wyświetlanie ActionBara."));
            return true;
        }

        if (cmd.equals("maniusannouncements")) {
            if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
                reloadConfig();
                stopAutoAnnouncers();
                clearActiveBossBars();
                stopActiveActionBar();
                startAutoAnnouncers();
                sender.sendMessage(parseColor(getConfig().getString("prefix", "") + getConfig().getString("messages.config-reloaded")));
                return true;
            }

            if (args.length > 1 && args[0].equalsIgnoreCase("history")) {
                String type = args[1].toLowerCase();
                List<String> hist = getConfig().getStringList("history." + type);
                if (hist.isEmpty()) {
                    sender.sendMessage(parseColor(getConfig().getString("prefix", "") + getConfig().getString("messages.history-empty")));
                    return true;
                }
                sender.sendMessage(parseColor("&b=== Historia ogłoszeń (" + type + ") ==="));
                for (int i = 0; i < hist.size(); i++) {
                    sender.sendMessage(parseColor("&3" + (i + 1) + ". &f" + hist.get(i)));
                }
                return true;
            }

            sender.sendMessage(parseColor("&b=== ManiusAnnouncements Komendy ==="));
            sender.sendMessage(parseColor("&3/maniusactionbar <tekst> [czas s/m/h] &7- Odpala actionbar"));
            sender.sendMessage(parseColor("&3/maniusactionbarusun &7- Usuwa obecny actionbar"));
            sender.sendMessage(parseColor("&3/maniusbossbar <tekst> [czas s/m/h] &7- Odpala bossbar"));
            sender.sendMessage(parseColor("&3/maniusbossbarusun &7- Usuwa obecny bossbar"));
            sender.sendMessage(parseColor("&3/maniustitle <tekst> &7- Odpala ogłoszenie na czacie"));
            sender.sendMessage(parseColor("&3/mannounce history <actionbar/bossbar/chat> &7- Wyświetla historię"));
            sender.sendMessage(parseColor("&3/mannounce reload &7- Przeładowuje config"));
            return true;
        }

        if (args.length == 0) {
            String usageKey = cmd.replace("manius", "");
            sender.sendMessage(parseColor(getConfig().getString("prefix", "") + getConfig().getString("messages.usage-" + usageKey)));
            return true;
        }

        if (cmd.equals("maniusactionbar")) {
            ParsedMessage pm = parseMessageAndDuration(args, getConfig().getInt("auto-announcer.actionbar.duration", 5));
            sendActionBarToAll(pm.message, pm.seconds);
            saveToHistory("actionbar", pm.message);
        } else if (cmd.equals("maniusbossbar")) {
            ParsedMessage pm = parseMessageAndDuration(args, getConfig().getInt("auto-announcer.bossbar.duration", 10));
            sendBossBarToAll(pm.message, pm.seconds);
            saveToHistory("bossbar", pm.message);
        } else if (cmd.equals("maniustitle")) {
            String message = String.join(" ", args);
            sendChatAnnouncementToAll(message);
            saveToHistory("chat", message);
        }

        sender.sendMessage(parseColor(getConfig().getString("prefix", "") + getConfig().getString("messages.broadcast-sent")));
        return true;
    }

    public void sendActionBarToAll(String text, int seconds) {
        stopActiveActionBar();
        String format = getConfig().getString("formats.actionbar-format", "{message}");
        String formattedText = parseColor(format.replace("{message}", text));

        activeActionBarTask = new BukkitRunnable() {
            int left = seconds;

            @Override
            public void run() {
                if (left <= 0) {
                    stopActiveActionBar();
                    return;
                }
                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(formattedText));
                }
                left--;
            }
        }.runTaskTimer(this, 0L, 20L);
    }

    public void sendBossBarToAll(String text, int seconds) {
        clearActiveBossBars();
        String format = getConfig().getString("formats.bossbar-format", "{message}");
        String formattedText = parseColor(format.replace("{message}", text));

        BarColor color;
        BarStyle style;

        try {
            color = BarColor.valueOf(getConfig().getString("auto-announcer.bossbar.color", "BLUE").toUpperCase());
        } catch (Exception e) {
            color = BarColor.BLUE;
        }

        try {
            style = BarStyle.valueOf(getConfig().getString("auto-announcer.bossbar.style", "SOLID").toUpperCase());
        } catch (Exception e) {
            style = BarStyle.SOLID;
        }

        BossBar bar = Bukkit.createBossBar(formattedText, color, style);
        bar.setProgress(1.0);

        for (Player p : Bukkit.getOnlinePlayers()) {
            bar.addPlayer(p);
        }

        activeBossBars.add(bar);

        new BukkitRunnable() {
            int totalTicks = seconds * 20;
            int currentTicks = 0;

            @Override
            public void run() {
                currentTicks += 2;
                double progress = 1.0 - ((double) currentTicks / totalTicks);
                if (progress <= 0.0 || !activeBossBars.contains(bar)) {
                    bar.removeAll();
                    activeBossBars.remove(bar);
                    cancel();
                    return;
                }
                bar.setProgress(Math.max(0.0, progress));
            }
        }.runTaskTimer(this, 0L, 2L);
    }

    public void sendChatAnnouncementToAll(String text) {
        String header = getConfig().getString("formats.chat-header", "&b&l[OGŁOSZENIE]");
        String chatFormat = getConfig().getString("formats.chat-format", "{header}\n&f{message}");

        String formatted = parseColor(chatFormat.replace("{header}", header).replace("{message}", text));
        String[] lines = formatted.split("\n");

        for (Player p : Bukkit.getOnlinePlayers()) {
            for (String line : lines) {
                p.sendMessage(line);
            }
        }
    }

    private ParsedMessage parseMessageAndDuration(String[] args, int defaultSeconds) {
        if (args.length == 0) {
            return new ParsedMessage("", defaultSeconds);
        }

        String lastArg = args[args.length - 1].toLowerCase();
        int parsedSeconds = parseTimeString(lastArg);

        if (parsedSeconds > 0 && args.length > 1) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < args.length - 1; i++) {
                sb.append(args[i]).append(" ");
            }
            return new ParsedMessage(sb.toString().trim(), parsedSeconds);
        } else {
            return new ParsedMessage(String.join(" ", args), defaultSeconds);
        }
    }

    private int parseTimeString(String input) {
        try {
            if (input.endsWith("s")) {
                return Integer.parseInt(input.substring(0, input.length() - 1));
            } else if (input.endsWith("m")) {
                return Integer.parseInt(input.substring(0, input.length() - 1)) * 60;
            } else if (input.endsWith("h")) {
                return Integer.parseInt(input.substring(0, input.length() - 1)) * 3600;
            }
        } catch (NumberFormatException ignored) {}
        return -1;
    }

    private static class ParsedMessage {
        final String message;
        final int seconds;

        ParsedMessage(String message, int seconds) {
            this.message = message;
            this.seconds = seconds;
        }
    }

    private void clearActiveBossBars() {
        for (BossBar bar : new ArrayList<>(activeBossBars)) {
            bar.removeAll();
        }
        activeBossBars.clear();
    }

    private void stopActiveActionBar() {
        if (activeActionBarTask != null) {
            activeActionBarTask.cancel();
            activeActionBarTask = null;
        }
    }

    private void saveToHistory(String type, String message) {
        List<String> hist = getConfig().getStringList("history." + type);
        hist.add(message);
        if (hist.size() > 10) hist.remove(0);
        getConfig().set("history." + type, hist);
        saveConfig();
    }

    private void startAutoAnnouncers() {
        if (getConfig().getBoolean("auto-announcer.chat.enabled", true)) {
            int interval = getConfig().getInt("auto-announcer.chat.interval", 300) * 20;
            List<String> msgs = getConfig().getStringList("auto-announcer.chat.messages");
            if (!msgs.isEmpty()) {
                activeTasks.add(new BukkitRunnable() {
                    int index = 0;

                    @Override
                    public void run() {
                        sendChatAnnouncementToAll(msgs.get(index));
                        index = (index + 1) % msgs.size();
                    }
                }.runTaskTimer(this, interval, interval));
            }
        }

        if (getConfig().getBoolean("auto-announcer.actionbar.enabled", true)) {
            int interval = getConfig().getInt("auto-announcer.actionbar.interval", 180) * 20;
            int duration = getConfig().getInt("auto-announcer.actionbar.duration", 5);
            List<String> msgs = getConfig().getStringList("auto-announcer.actionbar.messages");
            if (!msgs.isEmpty()) {
                activeTasks.add(new BukkitRunnable() {
                    int index = 0;

                    @Override
                    public void run() {
                        sendActionBarToAll(msgs.get(index), duration);
                        index = (index + 1) % msgs.size();
                    }
                }.runTaskTimer(this, interval, interval));
            }
        }

        if (getConfig().getBoolean("auto-announcer.bossbar.enabled", true)) {
            int interval = getConfig().getInt("auto-announcer.bossbar.interval", 240) * 20;
            int duration = getConfig().getInt("auto-announcer.bossbar.duration", 10);
            List<String> msgs = getConfig().getStringList("auto-announcer.bossbar.messages");
            if (!msgs.isEmpty()) {
                activeTasks.add(new BukkitRunnable() {
                    int index = 0;

                    @Override
                    public void run() {
                        sendBossBarToAll(msgs.get(index), duration);
                        index = (index + 1) % msgs.size();
                    }
                }.runTaskTimer(this, interval, interval));
            }
        }
    }

    private void stopAutoAnnouncers() {
        for (BukkitTask task : activeTasks) {
            task.cancel();
        }
        activeTasks.clear();
    }

    private String parseColor(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}
