package cn.cutelittlesky.cutelittlesky;

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

public class BlockRacingAddon extends JavaPlugin implements Listener {

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("BlockRacingAddon enabled!");
    }

    @Override
    public void onDisable() {
        getLogger().info("BlockRacingAddon disabled!");
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
        if (event.getEntityType() == EntityType.SNIFFER) {
            event.getDrops().add(new ItemStack(Material.TORCHFLOWER, 1));
        }
    }

    @EventHandler
    public void onCopperGolemInteract(PlayerInteractAtEntityEvent event) {
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