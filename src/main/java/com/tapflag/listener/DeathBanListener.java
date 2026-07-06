package com.tapflag.listener;

import com.tapflag.TapFlagPlugin;
import com.tapflag.hud.HudManager;
import com.tapflag.util.MessageUtil;
import org.bukkit.BanList;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.util.Date;

/**
 * 사망 시 자동 밴 처리.
 * LiteBans 플러그인이 있으면 LiteBans API 사용, 없으면 Bukkit BanList 폴백.
 */
public class DeathBanListener implements Listener {

    private final TapFlagPlugin plugin;
    private final HudManager    hudManager;

    public DeathBanListener(TapFlagPlugin plugin, HudManager hudManager) {
        this.plugin      = plugin;
        this.hudManager  = hudManager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        int banMinutes = plugin.getConfig().getInt("ban.death-ban-minutes", 20);
        String reason = plugin.getConfig().getString("ban.ban-reason", "사망으로 인한 자동 밴");
        boolean useLiteBans = plugin.getConfig().getBoolean("ban.use-litebans", true);

        long expiryMs = System.currentTimeMillis() + (long) banMinutes * 60 * 1000;
        hudManager.recordDeath(player.getUniqueId(), expiryMs);

        if (useLiteBans && applyLiteBan(player, banMinutes, reason)) {
            plugin.getLogger().info("[DeathBan] LiteBans 밴 적용: " + player.getName());
        } else {
            applyBukkitBan(player, banMinutes, reason);
            plugin.getLogger().info("[DeathBan] Bukkit 밴 적용: " + player.getName());
        }

        // 사망 화면 2초 후 킥
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                player.kickPlayer(
                    ChatColor.RED + reason + "\n"
                    + ChatColor.YELLOW + banMinutes + "분 후 재접속 가능합니다."
                );
            }
        }, 40L);
    }

    private boolean applyLiteBan(Player player, int minutes, String reason) {
        if (plugin.getServer().getPluginManager().getPlugin("LiteBans") == null) return false;
        try {
            // LiteBans API: litebans.api.Schedule 를 리플렉션으로 호출
            // (컴파일 타임 의존성 없이 런타임 연동)
            Class<?> schedClass = Class.forName("litebans.api.Schedule");
            // 실제 LiteBans API 버전에 따라 메서드 시그니처가 다를 수 있음
            // TODO: LiteBans jar 추가 후 직접 API 호출로 교체
            plugin.getLogger().warning("LiteBans 감지됨 — 직접 API 연동 필요 (DeathBanListener 참고)");
            return false;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private void applyBukkitBan(Player player, int minutes, String reason) {
        Date expiry = new Date(System.currentTimeMillis() + (long) minutes * 60 * 1000);
        plugin.getServer().getBanList(BanList.Type.NAME)
            .addBan(player.getName(), reason, expiry, "TapFlag");
    }
}
