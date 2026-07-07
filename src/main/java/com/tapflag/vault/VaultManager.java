package com.tapflag.vault;

import com.tapflag.TapFlagPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 팀별 공유창고 (54칸) 관리.
 * vault_<teamId>.yml 로 영속.
 */
public class VaultManager {

    private static final int VAULT_SIZE = 54;

    // 이전 버그로 저장됐을 수 있는 UI 전용 아이템 — 재료 기반 필터
    private static final Set<Material> UI_MATERIALS = Set.of(
        Material.GRAY_STAINED_GLASS_PANE,
        Material.YELLOW_STAINED_GLASS_PANE,
        Material.LIME_STAINED_GLASS_PANE,
        Material.WHITE_STAINED_GLASS_PANE,
        Material.BARRIER
    );

    // FlagMenuListener가 모든 UI 아이템에 부착하는 PDC 태그 키
    private static final NamespacedKey UI_TAG_KEY = new NamespacedKey("tapflag", "ui_item");

    private final TapFlagPlugin plugin;
    private final Map<String, ItemStack[]> cache = new HashMap<>();

    public VaultManager(TapFlagPlugin plugin) {
        this.plugin = plugin;
    }

    public Inventory createInventory(String teamId) {
        Inventory inv = Bukkit.createInventory(null, VAULT_SIZE,
            ChatColor.DARK_GREEN + "공유창고 [" + teamId + "]");
        inv.setContents(loadContents(teamId));
        return inv;
    }

    public void saveVault(String teamId, Inventory inv) {
        ItemStack[] contents = inv.getContents().clone();
        // UI 아이템이 섞여 있으면 제거 (이전 버그 방어)
        for (int i = 0; i < contents.length; i++) {
            if (contents[i] != null && UI_MATERIALS.contains(contents[i].getType())) {
                contents[i] = null;
            }
        }
        cache.put(teamId, contents);
        saveToFile(teamId, contents);
    }

    public void clearCache(String teamId) {
        cache.remove(teamId);
    }

    // ─── 내부 ────────────────────────────────────────────────────────────────

    private ItemStack[] loadContents(String teamId) {
        if (cache.containsKey(teamId)) {
            ItemStack[] cached = cache.get(teamId).clone();
            filterUiItems(cached);
            return cached;
        }

        File f = vaultFile(teamId);
        ItemStack[] contents = new ItemStack[VAULT_SIZE];
        if (!f.exists()) return contents;

        YamlConfiguration config = YamlConfiguration.loadConfiguration(f);
        for (int i = 0; i < VAULT_SIZE; i++) {
            contents[i] = config.getItemStack("slot." + i, null);
        }
        filterUiItems(contents);
        cache.put(teamId, contents.clone());
        return contents;
    }

    private static void filterUiItems(ItemStack[] contents) {
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item == null) continue;
            // 재료 기반 필터
            if (UI_MATERIALS.contains(item.getType())) { contents[i] = null; continue; }
            // PDC 태그 기반 필터 (FlagMenuListener가 모든 UI 아이템에 부착)
            ItemMeta meta = item.getItemMeta();
            if (meta != null && meta.getPersistentDataContainer()
                    .has(UI_TAG_KEY, PersistentDataType.BYTE)) {
                contents[i] = null;
            }
        }
    }

    private void saveToFile(String teamId, ItemStack[] contents) {
        File f = vaultFile(teamId);
        YamlConfiguration config = new YamlConfiguration();
        for (int i = 0; i < contents.length; i++) {
            if (contents[i] != null) config.set("slot." + i, contents[i]);
        }
        try { config.save(f); }
        catch (IOException e) { plugin.getLogger().severe("공유창고 저장 실패 [" + teamId + "]: " + e.getMessage()); }
    }

    private File vaultFile(String teamId) {
        return new File(plugin.getDataFolder(), "vault_" + teamId + ".yml");
    }
}
