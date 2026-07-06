package com.tapflag.listener;

import com.tapflag.GameManager;
import com.tapflag.TapFlagPlugin;
import com.tapflag.flag.Flag;
import com.tapflag.flag.FlagManager;
import com.tapflag.flag.FlagZoneDisplay;
import com.tapflag.team.TeamManager;
import com.tapflag.timer.GameTimer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * 점령 불가 시간(lockdown)에 적 감지영역(50블록) 내 플레이어에게
 * 나약함 + 채굴 피로 최대치(레벨 128)를 적용.
 */
public class LockdownListener implements Listener {

    private static final int    TICK_INTERVAL  = 80;   // 4초마다 재적용
    private static final int    DURATION       = 100;  // 효과 지속 5초
    private static final double DETECT_HALF_SQ =
        FlagZoneDisplay.DETECT_HALF * FlagZoneDisplay.DETECT_HALF;

    private final TapFlagPlugin plugin;
    private final GameTimer     gameTimer;
    private final GameManager   gameManager;
    private final FlagManager   flagManager;
    private final TeamManager   teamManager;

    public LockdownListener(TapFlagPlugin plugin, GameTimer gameTimer,
                            GameManager gameManager,
                            FlagManager flagManager, TeamManager teamManager) {
        this.plugin      = plugin;
        this.gameTimer   = gameTimer;
        this.gameManager = gameManager;
        this.flagManager = flagManager;
        this.teamManager = teamManager;
        startScheduler();
    }

    private void startScheduler() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!gameManager.isRunning()) return;
                boolean isCapture = gameTimer.isCapturePhase();

                for (Player p : plugin.getServer().getOnlinePlayers()) {
                    if (isCapture || !isInEnemyDetectZone(p)) {
                        // 점령 가능 시간이거나, 적 영역 밖 → 디버프 해제
                        p.removePotionEffect(PotionEffectType.WEAKNESS);
                        p.removePotionEffect(PotionEffectType.MINING_FATIGUE);
                    } else {
                        // 점령 불가 시간 + 적 감지영역 내 → 최대 디버프
                        p.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS,     DURATION, 127, true, false));
                        p.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, DURATION, 127, true, false));
                    }
                }
            }
        }.runTaskTimer(plugin, TICK_INTERVAL, TICK_INTERVAL);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!gameManager.isRunning() || gameTimer.isCapturePhase()) return;
        Player p = event.getPlayer();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (isInEnemyDetectZone(p)) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS,     DURATION, 127, true, false));
                p.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, DURATION, 127, true, false));
            }
        }, 20L);
    }

    /** 플레이어가 적 감지영역(50블록 반변) 안에 있는지 확인. */
    private boolean isInEnemyDetectZone(Player p) {
        var pTeam = teamManager.getTeamByPlayer(p.getUniqueId());
        for (Flag flag : flagManager.getAllFlags().values()) {
            if (flag.isNeutral()) continue;
            boolean isEnemy = (pTeam == null) || !flag.isOwnedBy(pTeam.getId());
            if (!isEnemy) continue;
            if (p.getLocation().distanceSquared(flag.getLocation()) <= DETECT_HALF_SQ) return true;
        }
        return false;
    }
}
