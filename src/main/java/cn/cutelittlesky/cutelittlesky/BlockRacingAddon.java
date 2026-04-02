package cn.cutelittlesky.cutelittlesky;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Damageable;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import top.lqsnow.blockracing.api.BlockRacingAPI;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.FurnaceRecipe;

public class BlockRacingAddon extends JavaPlugin implements Listener {

    private Boolean lastGameStarted = null;
    private Boolean lastNetherModeEnabled = null;
    // 下界模式相关：用于注册/移除炉石配方
    private NamespacedKey gravelToGlassKey = null;
    // 通知标记：每局每条只发送一次
    private boolean notifiedWitherOrBeacon = false;
    private boolean notifiedCopperGolem = false;
    private boolean notifiedReinforced = false;

    // 语言配置
    private FileConfiguration lang;

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("BlockRacingAddon enabled!");

        // 如果 BlockRacing 已加载，启动状态检测任务
        if (getServer().getPluginManager().getPlugin("BlockRacing") != null) {
            getLogger().info("Detected BlockRacing plugin, starting event/state monitoring.");
            // 加载配置与语言，导出语言文件到插件数据目录以便管理员编辑
            saveDefaultConfig();
            saveResource("languages/zh_cn.yml", false);
            saveResource("languages/en_us.yml", false);
            loadLanguage();
            startBlockRacingStateWatcher();
        } else {
            getLogger().warning("BlockRacing plugin not detected, skipping event/state monitoring.");
        }
    }

    @Override
    public void onDisable() {
        getLogger().info("BlockRacingAddon disabled!");
    }

    /**
     * 通过 BlockRacingAPI 轮询状态变化，检测游戏开始和下界模式开关。
     * - 检测游戏是否开始（isGameStarted 状态改变）
     * - 检测下界模式开关变化（isNetherModeEnabled 状态改变）
     */
    private void startBlockRacingStateWatcher() {
        new BukkitRunnable() {
            @Override
            public void run() {
                // 若插件在运行过程中被卸载，直接停止任务
                if (getServer().getPluginManager().getPlugin("BlockRacing") == null) {
                    this.cancel();
                    return;
                }

                try {
                    Boolean gameStarted = BlockRacingAPI.isGameStarted();
                    Boolean netherEnabled = BlockRacingAPI.isNetherModeEnabled();

                    // 首次赋值，不触发日志，仅记录当前状态；在 PREGAME->INGAME 时触发方块检测
                    if (lastGameStarted == null) {
                        lastGameStarted = gameStarted;
                        if (Boolean.TRUE.equals(gameStarted)) {
                            // 刚启用时若已在游戏中，执行检测
                            checkAndNotifyBlocks();
                        }
                    } else if (!lastGameStarted.equals(gameStarted)) {
                        // 状态变化触发日志
                        if (Boolean.TRUE.equals(gameStarted)) {
                            getLogger().info("Detected BlockRacing game start (INGAME).");
                            notifiedWitherOrBeacon = false;
                            notifiedCopperGolem = false;
                            notifiedReinforced = false;
                            // 检测并发送一次性提示
                            checkAndNotifyBlocks();
                        }
                        lastGameStarted = gameStarted;
                    }

                    if (lastNetherModeEnabled == null) {
                        lastNetherModeEnabled = netherEnabled;
                        // 启动时若已为下界模式，注册下界专用的配方
                        if (Boolean.TRUE.equals(netherEnabled)) {
                            registerNetherRecipes(true);
                        }
                    } else if (!lastNetherModeEnabled.equals(netherEnabled)) {
                        if (Boolean.TRUE.equals(netherEnabled)) {
                            getLogger().info("Detected BlockRacing Nether mode enabled.");
                            registerNetherRecipes(true);
                        } else {
                            getLogger().info("Detected BlockRacing Nether mode disabled.");
                            registerNetherRecipes(false);
                        }
                        lastNetherModeEnabled = netherEnabled;
                    }
                    // 如果处于下界模式，持续检测任务方块（这样 beacon/withers 相关提示在下界模式开启时总是可用）
                    if (Boolean.TRUE.equals(netherEnabled)) {
                        checkAndNotifyBlocks();
                    }
                } catch (Exception e) {
                    getLogger().warning("Failed to call BlockRacingAPI: " + e.getMessage());
                    this.cancel();
                }
            }
        }.runTaskTimer(this, 0L, 20L); // 每秒轮询一次
    }

    // 检查双方当前需要完成的方块并发送一次性提示（每局每条仅发送一次）
    private void checkAndNotifyBlocks() {
        try {
            String red = BlockRacingAPI.getRedTeamCurrentBlock();
            String blue = BlockRacingAPI.getBlueTeamCurrentBlock();

            if (!notifiedWitherOrBeacon) {
                if (isBeacon(red) || isBeacon(blue)) {
                    sendLangMessage("message.beacon");
                    notifiedWitherOrBeacon = true;
                } else if (isWither(red) || isWither(blue)) {
                    sendLangMessage("message.wither");
                    notifiedWitherOrBeacon = true;
                }
            }

            if (!notifiedCopperGolem) {
                if (isCopperGolem(red) || isCopperGolem(blue)) {
                    sendLangMessage("message.copper_golem");
                    notifiedCopperGolem = true;
                }
            }

            if (!notifiedReinforced) {
                if (isReinforced(red) || isReinforced(blue)) {
                    sendLangMessage("message.reinforced_deepslate");
                    notifiedReinforced = true;
                }
            }
        } catch (Throwable t) {
            getLogger().warning("Error checking block information: " + t.getMessage());
        }
    }

    private boolean isWitherOrBeacon(String blockName) {
        if (blockName == null) return false;
        String n = blockName.trim().toUpperCase();
        return n.contains("WITHER") || n.contains("SKULL") || n.contains("BEACON");
    }

    private boolean isBeacon(String blockName) {
        if (blockName == null) return false;
        String n = blockName.trim().toUpperCase();
        return n.contains("BEACON");
    }

    private boolean isWither(String blockName) {
        if (blockName == null) return false;
        String n = blockName.trim().toUpperCase();
        return n.contains("WITHER") || n.contains("SKULL");
    }

    // 注册或移除下界模式专用的炉石配方：砂砾 -> 玻璃
    private void registerNetherRecipes(boolean enable) {
        try {
            if (gravelToGlassKey == null) gravelToGlassKey = new NamespacedKey(this, "gravel_to_glass");
            if (enable) {
                // 200 ticks 炉时（默认）
                FurnaceRecipe recipe = new FurnaceRecipe(gravelToGlassKey, new ItemStack(Material.GLASS), Material.GRAVEL, 0.1f, 200);
                // 先尝试移除已存在的同名配方
                try { getServer().removeRecipe(gravelToGlassKey); } catch (Throwable ignore) {}
                getServer().addRecipe(recipe);
                sendLangMessage("message.nether_recipe_enabled");
                getLogger().info("Registered nether-mode furnace recipe: gravel -> glass");
            } else {
                boolean removed = false;
                try { removed = getServer().removeRecipe(gravelToGlassKey); } catch (Throwable ignore) {}
                if (removed) {
                    sendLangMessage("message.nether_recipe_disabled");
                    getLogger().info("Unregistered nether-mode furnace recipe: gravel -> glass");
                }
            }
        } catch (Throwable t) {
            getLogger().warning("Failed to (un)register nether recipes: " + t.getMessage());
        }
    }

    private boolean isCopperGolem(String blockName) {
        if (blockName == null) return false;
        String n = blockName.trim().toUpperCase();
        return n.contains("COPPER_GOLEM") || n.contains("OXIDIZED_COPPER_GOLEM") || n.contains("COPPER_GOLEM_STATUE");
    }

    private boolean isReinforced(String blockName) {
        if (blockName == null) return false;
        String n = blockName.trim().toUpperCase();
        return n.contains("REINFORCED") || n.contains("REINFORCED_DEEPSLATE");
    }

    private void loadLanguage() {
        try {
            String langKey = getConfig().getString("lang", "zh_cn");
            String path = "languages/" + langKey + ".yml";
            InputStreamReader r = new InputStreamReader(getResource(path), StandardCharsets.UTF_8);
            lang = YamlConfiguration.loadConfiguration(r);
        } catch (Exception e) {
            getLogger().warning("Failed to load the language file, using the built-in default language: " + e.getMessage());
            lang = YamlConfiguration.loadConfiguration(new InputStreamReader(getResource("languages/zh_cn.yml"), StandardCharsets.UTF_8));
        }
    }

    private void sendLangMessage(String key) {
        if (lang == null) return;
        String msg = lang.getString(key, "");
        if (msg == null || msg.isEmpty()) return;
        Bukkit.broadcastMessage(msg);
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        // 仅在 BlockRacing 游戏进行时启用
        if (!BlockRacingAPI.isGameStarted()) {
            return;
        }
        
        // 提高凋零骷髅头掉落率至20%
        if (event.getEntityType() == EntityType.WITHER_SKELETON) {
            // 移除原版掉落物中的骷髅头
            event.getDrops().removeIf(item -> item.getType() == Material.WITHER_SKELETON_SKULL);
            
            // 20%基础掉落率
            double baseChance = 0.2;
            
            // 计算抢夺附魔加成（每级+3%）
            if (event.getEntity().getKiller() != null) {
                int lootingLevel = event.getEntity().getKiller().getInventory()
                    .getItemInMainHand().getEnchantmentLevel(org.bukkit.enchantments.Enchantment.LOOTING);
                baseChance += lootingLevel * 0.05;
            }
            
            // 应用最终掉落率
            if (Math.random() < baseChance) {
                event.getDrops().add(new ItemStack(Material.WITHER_SKELETON_SKULL, 1));
            }
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        // 仅在 BlockRacing 游戏进行时启用
        if (!BlockRacingAPI.isGameStarted()) {
            return;
        }
        
        // 处理强化深板岩掉落
        if (event.getBlock().getType() == Material.REINFORCED_DEEPSLATE) {
            event.setDropItems(false); // 由插件控制掉落

            ItemStack tool = event.getPlayer().getInventory().getItemInMainHand();
            boolean hasSilkTouch = tool != null && tool.containsEnchantment(Enchantment.SILK_TOUCH);

            if (hasSilkTouch) {
                event.getBlock().getWorld().dropItemNaturally(
                    event.getBlock().getLocation(),
                    new ItemStack(Material.REINFORCED_DEEPSLATE, 1)
                );
            }
        }
    }

    @EventHandler
    public void onSnifferDeath(EntityDeathEvent event) {
        // 仅在 BlockRacing 游戏进行时启用
        if (!BlockRacingAPI.isGameStarted()) {
            return;
        }
        
        if (event.getEntityType() == EntityType.SNIFFER) {
            event.getDrops().add(new ItemStack(Material.TORCHFLOWER, 1));
        }
    }

    @EventHandler
    public void onCopperGolemInteract(PlayerInteractAtEntityEvent event) {
        // 仅在 BlockRacing 游戏进行时启用
        if (!BlockRacingAPI.isGameStarted()) {
            return;
        }
        
        if (event.getHand() != EquipmentSlot.HAND) {
            return; // 仅处理主手，避免左右手双触发
        }

        ItemStack inHand = event.getPlayer().getInventory().getItem(event.getHand());
        if (inHand == null || inHand.getType() != Material.OXIDIZED_COPPER || inHand.getAmount() <= 0) {
            return;
        }

        if (event.getRightClicked().getType() != EntityType.COPPER_GOLEM) {
            return;
        }

        event.setCancelled(true);

        Location dropLocation = event.getRightClicked().getLocation();

        // 消耗一块氧化铜
        inHand.setAmount(inHand.getAmount() - 1);

        // 让铜傀儡死亡/移除
        if (event.getRightClicked() instanceof Damageable) {
            ((Damageable) event.getRightClicked()).setHealth(0);
        } else {
            event.getRightClicked().remove();
        }

        // 掉落雕像物品
        if (dropLocation.getWorld() != null) {
            dropLocation.getWorld().dropItemNaturally(
                dropLocation,
                new ItemStack(Material.OXIDIZED_COPPER_GOLEM_STATUE, 1)
            );
        }
    }

}
