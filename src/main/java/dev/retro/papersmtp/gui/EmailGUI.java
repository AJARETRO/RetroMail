package dev.retro.papersmtp.gui;

import dev.retro.papersmtp.PaperSMTP;
import dev.retro.papersmtp.compatibility.CompatibilityUtil;
import dev.retro.papersmtp.database.SubscriptionState;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class EmailGUI {
    private final PaperSMTP plugin;

    public EmailGUI(PaperSMTP plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        String title = plugin.getPluginConfig().getMessage("gui.title", "&0Email Settings");
        Inventory inv = Bukkit.createInventory(new EmailGUIHolder(), 27, title);

        // Fill background with glass panes
        String glassName = plugin.getPluginConfig().getMessage("gui.glass-pane", " ");
        ItemStack pane = CompatibilityUtil.createItem("GRAY_STAINED_GLASS_PANE", 7, glassName, null);
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, pane);
        }

        // Center item (slot 4) represents status
        SubscriptionState state = plugin.getDatabaseManager().getSubscriptionState(player.getUniqueId());
        ItemStack statusItem;

        if (state.getType() == SubscriptionState.Type.NONE) {
            String name = plugin.getPluginConfig().getMessage("gui.subscribe.name", "&a&lSubscribe to Newsletters");
            List<String> lore = plugin.getPluginConfig().getMessageList("gui.subscribe.lore");
            statusItem = CompatibilityUtil.createItem("RED_WOOL", 14, name, lore);
        } else if (state.getType() == SubscriptionState.Type.PENDING) {
            String name = plugin.getPluginConfig().getMessage("gui.verification-pending.name", "&e&lVerification Pending");
            List<String> rawLore = plugin.getPluginConfig().getMessageList("gui.verification-pending.lore");
            List<String> lore = new ArrayList<>();
            for (String line : rawLore) {
                lore.add(line.replace("{email}", state.getEmail()));
            }
            statusItem = CompatibilityUtil.createItem("ORANGE_WOOL", 1, name, lore);
        } else {
            String name = plugin.getPluginConfig().getMessage("gui.subscribed.name", "&a&lSubscribed");
            List<String> rawLore = plugin.getPluginConfig().getMessageList("gui.subscribed.lore");
            List<String> lore = new ArrayList<>();
            for (String line : rawLore) {
                lore.add(line.replace("{email}", state.getEmail()));
            }
            statusItem = CompatibilityUtil.createItem("GREEN_WOOL", 5, name, lore);
        }

        inv.setItem(4, statusItem);

        // Row 1: Interactive preference toggles (only meaningful if they are subscribed or pending, but we allow changing preference anytime)
        // 1. Slot 11: Network News Toggle
        String newsStatus = state.isNewsEnabled() ? "§a§lENABLED" : "§c§lDISABLED";
        List<String> newsLore = new ArrayList<>();
        newsLore.add("§7Receive major updates, patch logs,");
        newsLore.add("§7and announcements directly in your mail.");
        newsLore.add("§7");
        newsLore.add("§7Status: " + newsStatus);
        newsLore.add("§7");
        newsLore.add("§eClick to Toggle.");
        ItemStack newsItem = CompatibilityUtil.createItem("PAPER", 0, "§b§lNetwork News", newsLore);
        inv.setItem(11, newsItem);

        // 2. Slot 13: Server Surveys Toggle
        String surveysStatus = state.isSurveysEnabled() ? "§a§lENABLED" : "§c§lDISABLED";
        List<String> surveysLore = new ArrayList<>();
        surveysLore.add("§7Participate in feedback, polls,");
        surveysLore.add("§7and give opinions to earn extra rewards.");
        surveysLore.add("§7");
        surveysLore.add("§7Status: " + surveysStatus);
        surveysLore.add("§7");
        surveysLore.add("§eClick to Toggle.");
        ItemStack surveysItem = CompatibilityUtil.createItem("WRITABLE_BOOK", 0, "§e§lServer Surveys", surveysLore);
        inv.setItem(13, surveysItem);

        // 3. Slot 15: Store Sales Toggle
        String salesStatus = state.isSalesEnabled() ? "§a§lENABLED" : "§c§lDISABLED";
        List<String> salesLore = new ArrayList<>();
        salesLore.add("§7Get notified about premium shop sales,");
        salesLore.add("§7ranks, keys, and special discount codes.");
        salesLore.add("§7");
        salesLore.add("§7Status: " + salesStatus);
        salesLore.add("§7");
        salesLore.add("§eClick to Toggle.");
        ItemStack salesItem = CompatibilityUtil.createItem("GOLD_INGOT", 0, "§6§lStore Sales", salesLore);
        inv.setItem(15, salesItem);

        // Developer item at slot 22
        String devName = plugin.getPluginConfig().getMessage("gui.developer.name", "&d&lDeveloper Profile");
        List<String> devLore = plugin.getPluginConfig().getMessageList("gui.developer.lore");
        ItemStack brandingItem = CompatibilityUtil.createItem("BOOK", 0, devName, devLore);
        inv.setItem(22, brandingItem);

        dev.retro.papersmtp.compatibility.SchedulerUtil.runForPlayer(plugin, player, () -> {
            player.openInventory(inv);
            CompatibilityUtil.playSound(player, "BLOCK_NOTE_BLOCK_PLING", "NOTE_PLING");
        });
    }
}
