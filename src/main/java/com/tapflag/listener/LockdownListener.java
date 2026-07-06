package com.tapflag.listener;

import com.tapflag.GameManager;
import com.tapflag.TapFlagPlugin;
import com.tapflag.timer.GameTimer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * 점령 불가 시간(capture phase 이전)에 모든 플레이어에게 약화 + 채굴 피로 적용.
 * 주기적 태스크로 재적용해 효과가 끊기지 않도록 함.
 */
public class LockdownListener implements Listener {

    private static final int TICK_INTERVAL = 80;   // 4초마다 재적용
    private static final int DURATION = 100;        // 효과 지속 5초 (TICK_INTERVAL + 여유)

    private final TapFlagPlugin plugin;
    private final GameTimer gameTimer;
    private final GameManager gameManager;
    private final int weaknessAmp;
    private final int fatigueAmp;

    public LockdownListener(TapFlagPlugin plugin, GameTimer gameTimer, GameManager gameManager) {
        this.plugin = plugin;
        this.gameTimer = gameTimer;
        this.gameManager = gameManager;
        this.weaknessAmp = plugin.getConfig().getInt("lockdown.weakness-amplifier", 1);
        this.fatigueAmp  = plugin.getConfig().getInt("lockdown.fatigue-amplifier", 2);

        startScheduler();
    }

    private void startScheduler() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!gameManager.isRunning()) return;
                boolean isCapture = gameTimer.isCapturePhase();
                for (Player p : plugin.getServer().getOnlinePlayers()) {
                    if (isCapture) {
                        // 점령 가능 시간 — 디버프 제거
                        p.removePotionEffect(PotionEffectType.WEAKNESS);
                        p.removePotionEffect(PotionEffectType.MINING_FATIGUE);
                    } else {
                        // 점령 불가 시간 — 디버프 적용
                        p.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS,     DURATION, weaknessAmp - 1, true, false));
                        p.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, DURATION, fatigueAmp - 1, true, false));
                    }
                }
            }
        }.runTaskTimer(plugin, TICK_INTERVAL, TICK_INTERVAL);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!gameManager.isRunning()) return;
        if (gameTimer.isCapturePhase()) return;
        Player p = event.getPlayer();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            p.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS,     DURATION, weaknessAmp - 1, true, false));
            p.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, DURATION, fatigueAmp - 1, true, false));
        }, 20L);
    }
}
