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
import java.util.UUID;

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

        // 처치자 금화 강탈 (5%)
        Player killer = player.getKiller();
        if (killer != null) applyGoldSteal(killer, player);

        int banMinutes = plugin.getConfig().getInt("ban.death-ban-minutes", 20);
        String reason = plugin.getConfig().getString("ban.ban-reason", "사망으로 인한 자동 밴");

        long expiryMs = System.currentTimeMillis() + (long) banMinutes * 60 * 1000;
        hudManager.recordDeath(player.getUniqueId(), expiryMs);

        applyBukkitBan(player, banMinutes, reason);
        plugin.getLogger().info("[DeathBan] 밴 적용: " + player.getName());

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

    private void applyBukkitBan(Player player, int minutes, String reason) {
        Date expiry = new Date(System.currentTimeMillis() + (long) minutes * 60 * 1000);
        plugin.getServer().getBanList(BanList.Type.NAME)
            .addBan(player.getName(), reason, expiry, "TapFlag");
    }

    private void applyGoldSteal(Player killer, Player victim) {
        var tm = plugin.getTeamManager();
        var killerTeam = tm.getTeamByPlayer(killer.getUniqueId());
        var victimTeam = tm.getTeamByPlayer(victim.getUniqueId());
        if (killerTeam == null || victimTeam == null) return;
        if (killerTeam.getId().equals(victimTeam.getId())) return; // 팀킬 제외

        int stolen = (int) Math.floor(victimTeam.getPoint() * 0.05);
        if (stolen <= 0) return;

        victimTeam.addPoint(-stolen);
        killerTeam.addPoint(stolen);
        tm.save();

        killer.sendMessage(MessageUtil.success(
            "[처치] " + victim.getName() + " 처치! 포인트 +" + stolen));
        victim.sendMessage(MessageUtil.warn(
            "[처치] " + killer.getName() + "에게 처치당해 포인트 -" + stolen));
    }
}
