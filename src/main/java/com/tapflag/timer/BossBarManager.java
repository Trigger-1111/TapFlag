package com.tapflag.timer;

import com.tapflag.TapFlagPlugin;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * 점령 가능/불가 상태와 남은 시간을 보스바로 표시.
 * - 점령 불가 (RED) : 점령 시작까지 남은 시간 + 바 채워짐
 * - 점령 가능 (GREEN): 남은 점령 시간 + 바 닳음
 * - 수동 제어 (YELLOW): 플레이테스트 명령어 제어 중
 */
public class BossBarManager extends BukkitRunnable {

    private final TapFlagPlugin plugin;
    private final BossBar bar;

    public BossBarManager(TapFlagPlugin plugin) {
        this.plugin = plugin;
        this.bar = Bukkit.createBossBar(" ", BarColor.WHITE, BarStyle.SOLID);
        this.bar.setVisible(false);
    }

    /** 플레이어 접속 시 호출 */
    public void addPlayer(Player player) {
        bar.addPlayer(player);
    }

    @Override
    public void run() {
        var gm    = plugin.getGameManager();
        var timer = plugin.getGameTimer();
        if (gm == null || timer == null) return;

        if (!gm.isRunning()) {
            if (bar.isVisible()) {
                bar.setVisible(false);
                bar.removeAll();
            }
            return;
        }

        // 온라인 플레이어가 모두 바에 포함되도록 동기화
        for (Player p : Bukkit.getOnlinePlayers()) bar.addPlayer(p);
        bar.setVisible(true);

        boolean capture  = timer.isCapturePhase();
        boolean forced   = timer.isForceCapture();
        boolean playtest = timer.isPlaytestMode();

        if (forced) {
            bar.setColor(BarColor.YELLOW);
            bar.setTitle(capture
                ? "[점령 가능] 수동 제어 — /tapflag playtest capture off 으로 비활성화"
                : "[점령 불가] 수동 제어 — /tapflag playtest capture on 으로 활성화");
            bar.setProgress(capture ? 1.0 : 0.0);
        } else if (playtest) {
            int phaseRemaining = timer.getPhaseRemainingSeconds();
            int phaseDuration  = timer.getPhaseTotalSeconds();
            double progress = phaseDuration > 0
                ? (double) phaseRemaining / phaseDuration : 1.0;
            if (capture) {
                bar.setColor(BarColor.GREEN);
                bar.setTitle("[점령 가능]  남은 시간: " + GameTimer.formatTime(phaseRemaining));
            } else {
                bar.setColor(BarColor.RED);
                bar.setTitle("[점령 불가]  점령까지: " + GameTimer.formatTime(phaseRemaining));
            }
            bar.setProgress(Math.max(0.0, Math.min(1.0, progress)));
        } else {
            int total        = timer.getTotalSeconds();
            int elapsed      = timer.getElapsedSeconds();
            int remaining    = timer.getRemainingSeconds();
            int captureDur   = timer.getCapturePhaseDuration();
            int captureStart = total - captureDur;

            if (capture) {
                bar.setColor(BarColor.GREEN);
                bar.setTitle("[점령 가능]  남은 시간: " + GameTimer.formatTime(remaining));
                double progress = captureDur > 0 ? (double) remaining / captureDur : 1.0;
                bar.setProgress(Math.max(0.0, Math.min(1.0, progress)));
            } else {
                int untilCapture = Math.max(0, captureStart - elapsed);
                bar.setColor(BarColor.RED);
                bar.setTitle("[점령 불가]  점령 시작까지: " + GameTimer.formatTime(untilCapture));
                double progress = captureStart > 0 ? (double) elapsed / captureStart : 0.0;
                bar.setProgress(Math.max(0.0, Math.min(1.0, progress)));
            }
        }
    }
}
