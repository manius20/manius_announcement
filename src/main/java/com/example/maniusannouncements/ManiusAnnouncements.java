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

    @Override
    public void onEnable() {
        saveDefaultConfig();

        if (getCommand("maniusactionbar") != null) getCommand("maniusactionbar").setExecutor(this);
        if (getCommand("maniusbossbar") != null) getCommand("maniusbossbar").setExecutor(this);
        if (getCommand("maniustitle") != null) getCommand("maniustitle").setExecutor(this);
        if (getCommand("maniusannouncements") != null) getCommand("maniusannouncements").setExecutor(this);

        startAutoAnnouncers();
    }

    @Override
    public void onDisable() {
        stopAutoAnnouncers();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("manius.admin")) {
            sender.sendMessage(parseColor(getConfig().getString("prefix", "") + getConfig().getString("messages.no-permission")));
            return true;
        }

        String cmd = command.getName().toLowerCase();

        if (cmd.equals("maniusannouncements")) {
            if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
                reloadConfig();
                stopAutoAnnouncers();
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
            sender.sendMessage(parseColor("&3/maniusactionbar <tekst> &7- Odpala actionbar"));
            sender.sendMessage(parseColor("&3/maniusbossbar <tekst> &7- Odpala bossbar"));
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

        String message = String.join(" ", args);

        if (cmd.equals("maniusactionbar")) {
            int duration = getConfig().getInt("auto-announcer.actionbar.duration", 5);
            sendActionBarToAll(message, duration);
            saveToHistory("actionbar", message);
        } else if (cmd.equals("maniusbossbar")) {
            int duration = getConfig().getInt("auto-announcer.bossbar.duration", 10);
            sendBossBarToAll(message, duration);
            saveToHistory("bossbar", message);
        } else if (cmd.equals("maniustitle")) {
            sendChatAnnouncementToAll(message);
            saveToHistory("chat", message);
        }

        sender.sendMessage(parseColor(getConfig().getString("prefix", "") + getConfig().getString("messages.broadcast-sent")));
        return true;
    }

    public void sendActionBarToAll(String text, int seconds) {
        String coloredText = parseColor(text);
        new BukkitRunnable() {
            int left = seconds;

            @Override
            public void run() {
                if (left <= 0) {
                    cancel();
                    return;
                }
                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(coloredText));
                }
                left--;
            }
        }.runTaskTimer(this, 0L, 20L);
    }

    public void sendBossBarToAll(String text, int seconds) {
        String coloredText = parseColor(text);
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

        BossBar bar = Bukkit.createBossBar(coloredText, color, style);
        bar.setProgress(1.0);

        for (Player p : Bukkit.getOnlinePlayers()) {
            bar.addPlayer(p);
        }

        new BukkitRunnable() {
            int totalTicks = seconds * 20;
            int currentTicks = 0;

            @Override
            public void run() {
                currentTicks += 2;
                double progress = 1.0 - ((double) currentTicks / totalTicks);
                if (progress <= 0.0) {
                    bar.removeAll();
                    cancel();
                    return;
                }
                bar.setProgress(Math.max(0.0, progress));
            }
        }.runTaskTimer(this, 0L, 2L);
    }

    public void sendChatAnnouncementToAll(String text) {
        String border = parseColor("&b&m========================================");
        String msg = parseColor("&f" + text);

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage("");
            p.sendMessage(border);
            p.sendMessage(msg);
            p.sendMessage(border);
            p.sendMessage("");
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
