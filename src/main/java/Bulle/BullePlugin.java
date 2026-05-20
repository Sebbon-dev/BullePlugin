package bulle;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class BullePlugin extends JavaPlugin implements Listener, CommandExecutor {

    private NamespacedKey bulleKey;
    private NamespacedKey levelKey;

    private final Map<UUID, Integer> swordHits = new HashMap<>();
    private final Map<UUID, Long> maceCooldown = new HashMap<>();
    private final long MACE_COOLDOWN_TIME = 8000;

    private File economyFile;
    private FileConfiguration economyConfig;

    @Override
    public void onEnable() {
        bulleKey = new NamespacedKey(this, "is_bulle_item");
        levelKey = new NamespacedKey(this, "bulle_level");

        Objects.requireNonNull(getCommand("bulle")).setExecutor(this);
        Objects.requireNonNull(getCommand("upgrade")).setExecutor(this);
        Objects.requireNonNull(getCommand("bullar")).setExecutor(this);

        getServer().getPluginManager().registerEvents(this, this);

        loadEconomy();
        getLogger().info("Bulle Monster-Plugin v4.2 (Crates Removed) Started!");
    }

    // --- EKONOMI-SYSTEM ---
    private void loadEconomy() {
        economyFile = new File(getDataFolder(), "bullar_data.yml");
        if (!economyFile.exists()) {
            economyFile.getParentFile().mkdirs();
            try {
                economyFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        economyConfig = YamlConfiguration.loadConfiguration(economyFile);
    }

    private void saveEconomy() {
        try {
            economyConfig.save(economyFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private int getBullar(UUID uuid) {
        return economyConfig.getInt(uuid.toString() + ".bullar", 0);
    }

    private void setBullar(UUID uuid, int amount) {
        economyConfig.set(uuid.toString() + ".bullar", amount);
        saveEconomy();
    }

    // --- UPGRADE GUI ---
    public void openUpgradeMenu(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (!isBulleItem(item)) {
            player.sendMessage(Component.text("Du måste hålla i ett Bulle-verktyg för att uppgradera!").color(NamedTextColor.RED));
            return;
        }

        int currentLvl = getBulleLevel(item);
        Inventory gui = Bukkit.createInventory(null, 27, Component.text("Bulle Upgrade Station").color(NamedTextColor.DARK_GRAY).decoration(TextDecoration.BOLD, true));

        ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta glassMeta = glass.getItemMeta();
        if (glassMeta != null) glassMeta.displayName(Component.text(" "));
        glass.setItemMeta(glassMeta);
        for (int i = 0; i < 27; i++) gui.setItem(i, glass);

        gui.setItem(13, item.clone());

        if (currentLvl < 3) {
            int price = (currentLvl == 1) ? 150 : 300;
            ItemStack buyButton = new ItemStack(Material.EMERALD_BLOCK);
            ItemMeta buyMeta = buyButton.getItemMeta();
            if (buyMeta != null) {
                buyMeta.displayName(Component.text("KÖP UPPGRADERING").color(NamedTextColor.GREEN).decoration(TextDecoration.BOLD, true));
                List<Component> buyLore = new ArrayList<>();
                buyLore.add(Component.text("Uppgradera till Level " + (currentLvl + 1)).color(NamedTextColor.GRAY));
                buyLore.add(Component.text("Pris: " + price + " Bullar").color(NamedTextColor.GOLD).decoration(TextDecoration.BOLD, true));
                buyMeta.lore(buyLore);
                buyButton.setItemMeta(buyMeta);
            }
            gui.setItem(11, buyButton);
        } else {
            ItemStack maxButton = new ItemStack(Material.BEDROCK);
            ItemMeta maxMeta = maxButton.getItemMeta();
            if (maxMeta != null) maxMeta.displayName(Component.text("MAXAD LEVEL (LVL 3)").color(NamedTextColor.GOLD).decoration(TextDecoration.BOLD, true));
            maxButton.setItemMeta(maxMeta);
            gui.setItem(11, maxButton);
        }

        ItemStack cancelButton = new ItemStack(Material.BARRIER);
        ItemMeta cancelMeta = cancelButton.getItemMeta();
        if (cancelMeta != null) cancelMeta.displayName(Component.text("STÄNG MENY").color(NamedTextColor.RED).decoration(TextDecoration.BOLD, true));
        cancelButton.setItemMeta(cancelMeta);
        gui.setItem(15, cancelButton);

        player.openInventory(gui);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().title().toString().contains("Bulle Upgrade Station")) return;
        event.setCancelled(true);

        Player player = (Player) event.getWhoClicked();
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null) return;

        if (clicked.getType() == Material.BARRIER) {
            player.closeInventory();
        }

        if (clicked.getType() == Material.EMERALD_BLOCK) {
            ItemStack item = player.getInventory().getItemInMainHand();
            if (!isBulleItem(item)) return;

            int currentLvl = getBulleLevel(item);
            int price = (currentLvl == 1) ? 150 : 300;
            int playerBullar = getBullar(player.getUniqueId());

            if (playerBullar < price) {
                player.sendMessage(Component.text("Du har inte råd! Du saknar " + (price - playerBullar) + " Bullar.").color(NamedTextColor.RED));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                return;
            }

            setBullar(player.getUniqueId(), playerBullar - price);
            upgradeBulleItem(item, currentLvl + 1);

            player.sendMessage(Component.text("DIN BULLE BLEV STARKARE! (Level " + (currentLvl + 1) + ")").color(NamedTextColor.GREEN).decoration(TextDecoration.BOLD, true));
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 1.2f);
            player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, player.getLocation(), 40, 0.5, 1.0, 0.5, 0.2);

            player.closeInventory();
        }
    }

    // --- DURABILITY SYSTEM ---
    @EventHandler
    public void onItemDamage(PlayerItemDamageEvent event) {
        ItemStack item = event.getItem();
        if (isBulleItem(item)) {
            ItemMeta meta = item.getItemMeta();
            if (meta instanceof Damageable damageable) {
                if (damageable.getDamage() >= item.getType().getMaxDurability() - 1) {
                    event.setDamage(0);
                    if (!hasBrokenTag(item)) {
                        List<Component> lore = meta.hasLore() ? meta.lore() : new ArrayList<>();
                        if (lore == null) lore = new ArrayList<>();
                        lore.add(Component.text("TRASIG").color(NamedTextColor.RED).decoration(TextDecoration.BOLD, true));
                        meta.lore(lore);
                        item.setItemMeta(meta);
                    }
                }
            }
        }
    }

    // --- COMBAT LOGIC ---
    @EventHandler
    public void onCombat(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        ItemStack item = player.getInventory().getItemInMainHand();

        if (!isBulleItem(item) || hasBrokenTag(item)) return;
        int lvl = getBulleLevel(item);

        if (item.getType() == Material.MACE && player.getFallDistance() > 1.3) {
            long now = System.currentTimeMillis();
            if (maceCooldown.getOrDefault(player.getUniqueId(), 0L) > now) {
                player.sendMessage(Component.text("Mace cooldown pågår!").color(NamedTextColor.RED));
                event.setCancelled(true);
                return;
            }

            double radius = (lvl == 1) ? 5.0 : (lvl == 2) ? 10.0 : 15.0;
            spawnShockwave(event.getEntity().getLocation(), radius);

            int densityLevel = item.hasItemMeta() ? item.getItemMeta().getEnchantLevel(Enchantment.DENSITY) : 0;
            double finalDamage = 5.0 + (densityLevel * 25.0);

            for (Entity e : event.getEntity().getNearbyEntities(radius, 3, radius)) {
                if (e instanceof LivingEntity victim && e != player) {
                    victim.damage(finalDamage, player);
                }
            }
            maceCooldown.put(player.getUniqueId(), now + MACE_COOLDOWN_TIME);
        }

        if (item.getType() == Material.NETHERITE_SWORD) {
            UUID id = player.getUniqueId();
            int hits = swordHits.getOrDefault(id, 0) + 1;

            if (hits >= 4) {
                double multiplier = (lvl == 1) ? 1.5 : (lvl == 2) ? 2.0 : 2.5;
                event.setDamage(event.getDamage() * multiplier);
                player.sendMessage(Component.text("BULLE BLAST HUGGET!").color(NamedTextColor.GOLD).decoration(TextDecoration.BOLD, true));
                player.getWorld().spawnParticle(Particle.FLAME, event.getEntity().getLocation(), 30, 0.5, 0.5, 0.5, 0.1);
                swordHits.put(id, 0);
            } else {
                event.setDamage(event.getDamage() * 0.6);
                swordHits.put(id, hits);
            }
        }
    }

    // --- MINING LOGIC ---
    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (!isBulleItem(item) || hasBrokenTag(item)) return;
        int lvl = getBulleLevel(item);
        Block center = event.getBlock();

        if (item.getType() == Material.NETHERITE_PICKAXE) {
            for (int x = -1; x <= 1; x++) {
                for (int y = -1; y <= 1; y++) {
                    for (int z = -1; z <= 1; z++) {
                        Block b = center.getRelative(x, y, z);
                        if (b.getType() != Material.BEDROCK && b.getType() != Material.AIR) {
                            b.breakNaturally(item);
                        }
                    }
                }
            }
        }

        if (item.getType() == Material.NETHERITE_AXE && center.getType().name().contains("LOG")) {
            int maxLogs = (lvl == 1) ? 20 : (lvl == 2) ? 40 : 9999;
            breakTree(center, item, new HashSet<>(), maxLogs);
            ItemMeta meta = item.getItemMeta();
            if (meta instanceof Damageable damageable) {
                damageable.setDamage(damageable.getDamage() + 15);
                item.setItemMeta(meta);
            }
        }

        if (item.getType() == Material.NETHERITE_SHOVEL) {
            player.getWorld().playSound(center.getLocation(), Sound.BLOCK_SAND_BREAK, 1.0f, 1.2f);
        }
    }

    @EventHandler
    public void onHarvest(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (!isBulleItem(item) || item.getType() != Material.NETHERITE_HOE || hasBrokenTag(item)) return;
        Block center = Objects.requireNonNull(event.getClickedBlock());

        if (center.getType() == Material.WHEAT || center.getType() == Material.CARROTS || center.getType() == Material.POTATOES) {
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    Block b = center.getRelative(x, 0, z);
                    if (b.getType() == center.getType()) {
                        b.breakNaturally(item);
                    }
                }
            }
        }
    }

    private void breakTree(Block block, ItemStack tool, Set<Block> visited, int maxLogs) {
        if (visited.size() >= maxLogs || !block.getType().name().contains("LOG") || visited.contains(block)) return;
        visited.add(block);
        block.breakNaturally(tool);
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 2; y++) {
                for (int z = -1; z <= 1; z++) {
                    breakTree(block.getRelative(x, y, z), tool, visited, maxLogs);
                }
            }
        }
    }

    // --- COMMANDS HANDLER ---
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) return false;

        if (command.getName().equalsIgnoreCase("upgrade")) {
            openUpgradeMenu(player);
            return true;
        }

        if (command.getName().equalsIgnoreCase("bullar")) {
            if (args.length > 0 && args[0].equalsIgnoreCase("ge") && player.isOp()) {
                if (args.length < 3) {
                    player.sendMessage("Användning: /bullar ge <spelare> <antal>");
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) return true;
                int amount = Integer.parseInt(args[2]);
                setBullar(target.getUniqueId(), getBullar(target.getUniqueId()) + amount);
                player.sendMessage("Gav " + amount + " bullar till " + target.getName());
                return true;
            }

            player.sendMessage(Component.text("Ditt saldo: ").color(NamedTextColor.YELLOW)
                    .append(Component.text(getBullar(player.getUniqueId()) + " Bullar").color(NamedTextColor.GOLD).decoration(TextDecoration.BOLD, true)));
            return true;
        }

        if (args.length == 0) return false;

        // Endast admin commands kvar för att spawna bas-vapnen
        if (player.isOp()) {
            switch (args[0].toLowerCase()) {
                case "mace" -> player.getInventory().addItem(createBulleItem(Material.MACE, "Bulle Mace", 2613));
                case "pickaxe" -> player.getInventory().addItem(createBulleItem(Material.NETHERITE_PICKAXE, "Bulle Pickaxe", 2614));
                case "sword" -> player.getInventory().addItem(createBulleItem(Material.NETHERITE_SWORD, "Bulle Sword", 2615));
                case "axe" -> player.getInventory().addItem(createBulleItem(Material.NETHERITE_AXE, "Bulle Axe", 2611));
                case "shovel" -> player.getInventory().addItem(createBulleItem(Material.NETHERITE_SHOVEL, "Bulle Shovel", 2612));
                case "hoe" -> player.getInventory().addItem(createBulleItem(Material.NETHERITE_HOE, "Bulle Hoe", 2610));
            }
        }
        return true;
    }

    private ItemStack createBulleItem(Material material, String name, int model) {
        ItemStack item = new ItemStack(material);
        upgradeBulleItem(item, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(name).color(NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
            meta.setCustomModelData(model);
            item.setItemMeta(meta);
        }
        return item;
    }

    private void upgradeBulleItem(ItemStack item, int newLevel) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        meta.getPersistentDataContainer().set(bulleKey, PersistentDataType.BYTE, (byte) 1);
        meta.getPersistentDataContainer().set(levelKey, PersistentDataType.INTEGER, newLevel);

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Tier: Bulle-Vapen").color(NamedTextColor.DARK_PURPLE));
        lore.add(Component.text("Level: " + newLevel + " / 3").color(NamedTextColor.AQUA).decoration(TextDecoration.BOLD, true));
        meta.lore(lore);

        meta.removeEnchant(Enchantment.EFFICIENCY);
        meta.removeEnchant(Enchantment.SHARPNESS);

        if (item.getType() == Material.MACE) {
            meta.addEnchant(Enchantment.DENSITY, 3, true);
            meta.addEnchant(Enchantment.WIND_BURST, 2, true);
        } else if (item.getType() == Material.NETHERITE_PICKAXE) {
            int effLvl = (newLevel == 1) ? 1 : (newLevel == 2) ? 3 : 6;
            meta.addEnchant(Enchantment.EFFICIENCY, effLvl, true);
        } else if (item.getType() == Material.NETHERITE_SHOVEL) {
            meta.addEnchant(Enchantment.EFFICIENCY, 10, true);
        }

        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        item.setItemMeta(meta);
    }

    private int getBulleLevel(ItemStack item) {
        if (!isBulleItem(item)) return 1;
        return item.getItemMeta().getPersistentDataContainer().getOrDefault(levelKey, PersistentDataType.INTEGER, 1);
    }

    private void spawnShockwave(Location loc, double radius) {
        for (double t = 0; t < Math.PI * 2; t += Math.PI / 16) {
            double x = radius * Math.cos(t);
            double z = radius * Math.sin(t);
            loc.add(x, 0.2, z);
            loc.getWorld().spawnParticle(Particle.CLOUD, loc, 1, 0, 0, 0, 0.05);
            loc.subtract(x, 0.2, z);
        }
        loc.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.7f);
    }

    private boolean isBulleItem(ItemStack item) {
        return item != null && item.hasItemMeta() &&
                item.getItemMeta().getPersistentDataContainer().has(bulleKey, PersistentDataType.BYTE);
    }

    private boolean hasBrokenTag(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasLore()) return false;
        List<Component> lore = meta.lore();
        if (lore == null) return false;
        for (Component line : lore) {
            if (line.toString().contains("TRASIG")) return true;
        }
        return false;
    }

    @EventHandler
    public void onAnvilPrepare(PrepareAnvilEvent event) {
        ItemStack firstItem = event.getInventory().getItem(0);
        ItemStack secondItem = event.getInventory().getItem(1);

        if (isBulleItem(firstItem) && secondItem != null && secondItem.getType() == Material.ENCHANTED_BOOK) {
            event.setResult(null);
        }
    }

    @EventHandler
    public void onEnchantItem(EnchantItemEvent event) {
        if (isBulleItem(event.getItem())) {
            event.setCancelled(true);
        }
    }
}
