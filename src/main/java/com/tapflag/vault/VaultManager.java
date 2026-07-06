package com.tapflag.vault;

import com.tapflag.TapFlagPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * 팀별 금고(54칸 공유 보관소) 관리.
 * vault_<teamId>.yml 로 영속.
 */
public class VaultManager {

    private static final int VAULT_SIZE = 54;

    private final TapFlagPlugin plugin;
    private final Map<String, ItemStack[]> cache = new HashMap<>();

    public VaultManager(TapFlagPlugin plugin) {
        this.plugin = plugin;
    }

    /** 팀 금고 인벤토리 생성 (저장된 내용 복원) */
    public Inventory createInventory(String teamId) {
        Inventory inv = Bukkit.createInventory(null, VAULT_SIZE,
            ChatColor.DARK_GREEN + "금고 [" + teamId + "]");
        ItemStack[] contents = loadContents(teamId);
        inv.setContents(contents);
        return inv;
    }

    /** 금고 내용 저장 (플레이어가 인벤토리 닫을 때 호출) */
    public void saveVault(String teamId, Inventory inv) {
        ItemStack[] contents = inv.getContents().clone();
        cache.put(teamId, contents);
        saveToFile(teamId, contents);
    }

    /** 서버 재시작 없이 팀 해체 시 캐시 정리 */
    public void clearCache(String teamId) {
        cache.remove(teamId);
    }

    // ─── 내부 ────────────────────────────────────────────────────────────────

    private ItemStack[] loadContents(String teamId) {
        if (cache.containsKey(teamId)) return cache.get(teamId).clone();

        File f = vaultFile(teamId);
        ItemStack[] contents = new ItemStack[VAULT_SIZE];
        if (!f.exists()) return contents;

        YamlConfiguration config = YamlConfiguration.loadConfiguration(f);
        for (int i = 0; i < VAULT_SIZE; i++) {
            contents[i] = config.getItemStack("slot." + i, null);
        }
        cache.put(teamId, contents.clone());
        return contents;
    }

    private void saveToFile(String teamId, ItemStack[] contents) {
        File f = vaultFile(teamId);
        YamlConfiguration config = new YamlConfiguration();
        for (int i = 0; i < contents.length; i++) {
            if (contents[i] != null) config.set("slot." + i, contents[i]);
        }
        try { config.save(f); }
        catch (IOException e) { plugin.getLogger().severe("금고 저장 실패 [" + teamId + "]: " + e.getMessage()); }
    }

    private File vaultFile(String teamId) {
        return new File(plugin.getDataFolder(), "vault_" + teamId + ".yml");
    }
}
