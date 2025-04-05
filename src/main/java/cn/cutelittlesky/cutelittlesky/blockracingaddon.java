package cn.cutelittlesky.cutelittlesky;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public class blockracingaddon extends JavaPlugin implements Listener {

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("blockracingaddon 已启用!");
    }

    @Override
    public void onDisable() {
        getLogger().info("blockracingaddon 已禁用!");
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
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
        // 处理强化深板岩掉落
        if (event.getBlock().getType() == Material.REINFORCED_DEEPSLATE) {
            // 确保掉落1个强化深板岩
            event.getBlock().getWorld().dropItemNaturally(
            event.getBlock().getLocation(),
            new ItemStack(Material.REINFORCED_DEEPSLATE, 1)
                );
            }
        }
    }