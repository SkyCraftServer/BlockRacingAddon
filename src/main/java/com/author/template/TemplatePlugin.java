package com.author.template;

import com.author.template.commands.*;
import org.bukkit.plugin.java.JavaPlugin;

public class TemplatePlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        this.getCommand("test").setExecutor(new TestCommand());
        getLogger().info("Plugin Enabled");
    }
}
