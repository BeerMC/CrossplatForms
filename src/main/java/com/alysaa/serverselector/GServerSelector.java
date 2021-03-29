package com.alysaa.serverselector;

import com.alysaa.serverselector.command.SelectorCommand;
import com.alysaa.serverselector.listeners.CompassOnJoin;
import org.bukkit.Bukkit;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;

public class GServerSelector extends JavaPlugin {
    public static GServerSelector plugin;

    @Override
    public void onEnable() {
        plugin = this;
        createFiles();
        this.getCommand("servers").setExecutor(new SelectorCommand());
        getLogger().info("Plugin has been enabled");
        Bukkit.getServer().getPluginManager().registerEvents(new CompassOnJoin(), this);
    }

    @Override
    public void onDisable() {

    }

    private void createFiles() {
        File configFile = new File(getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            configFile.getParentFile().mkdirs();
            saveResource("config.yml", false);
        }
        FileConfiguration config = new YamlConfiguration();
        try {
            config.load(configFile);
        } catch (IOException | InvalidConfigurationException e) {
            e.printStackTrace();
        }
        File PathDir = new File("plugins/GServerSelector/ButtonImages/");
        if (!PathDir.exists()) {
            try {
                PathDir.mkdirs();
            } catch (Exception ignored) {
            }
        }
    }
}