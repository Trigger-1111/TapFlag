package com.tapflag.timer;

import com.tapflag.TapFlagPlugin;
import com.tapflag.util.MessageUtil;
import org.bukkit.ChatColor;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

public class GameTimer {

    private final TapFlagPlugin plugin;
    private final int totalSeconds;
    private final int capturePhaseDuration;

    private int elapsedSeconds = 0;
    private boolean running = false;
    private boolean gameOver = false;
    private boolean forceCapture = false;   // 테스트용 강제 점령 단계 설정
    private BukkitTask timerTask;

    public GameTimer(TapFlagPlugin plugin) {
        this.plugin = plugin;
        this.totalSeconds = plugin.getConfig().getInt("game.total-duration-seconds", 10800);
        this.capturePhaseDuration = plugin.getConfig().getInt("game.capture-phase-duration-seconds", 3600);
    }

    public void start() {
        if (running) return;
        running = true;
        elapsedSeconds = 0;
        gameOver = false;

        timerTask = new BukkitRunnable() {
            @Override
            public void run() {
                elapsedSeconds++;
                int remaining = totalSeconds - elapsedSeconds;
                int captureStart = totalSeconds - capturePhaseDuration;

                // 점령 단계 진입 알림
                if (elapsedSeconds == captureStart) {
                    plugin.getServer().broadcastMessage(
                        MessageUtil.prefix() + ChatColor.GREEN + "⚔ 점령 가능 시간이 시작되었습니다! (남은 시간: 1시간)"
                    );
                }

                // 주요 시점 알림
                if (remaining == 1800 || remaining == 600 || remaining == 300 || remaining == 60) {
                    plugin.getServer().broadcastMessage(
                        MessageUtil.warn("게임 종료까지 " + formatTime(remaining) + " 남았습니다.")
                    );
                }

                if (elapsedSeconds >= totalSeconds) {
                    endGame();
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private void endGame() {
        running = false;
        gameOver = true;
        plugin.getServer().broadcastMessage(
            MessageUtil.prefix() + ChatColor.GOLD + "" + ChatColor.BOLD + "게임이 종료되었습니다!"
        );
    }

    public void stop() {
        running = false;
        if (timerTask != null) {
            timerTask.cancel();
            timerTask = null;
        }
    }

    // --- 상태 조회 ---
    public boolean isRunning()    { return running; }
    public boolean isGameOver()   { return gameOver; }
    public int getElapsedSeconds(){ return elapsedSeconds; }
    public int getRemainingSeconds() { return Math.max(0, totalSeconds - elapsedSeconds); }

    public boolean isCapturePhase() {
        if (forceCapture) return true;
        if (!running) return false;
        return elapsedSeconds >= (totalSeconds - capturePhaseDuration);
    }

    /** 테스트용 — 점령 단계 강제 on/off */
    public void setForceCapture(boolean force) { this.forceCapture = force; }

    public String getStatusDisplay() {
        if (!running) return ChatColor.GRAY + "타이머 미실행";
        String phase = isCapturePhase()
            ? ChatColor.GREEN + "점령 가능"
            : ChatColor.RED + "점령 불가";
        return "경과: " + formatTime(elapsedSeconds)
            + " | 남은: " + formatTime(getRemainingSeconds())
            + " | " + phase;
    }

    /** 순수 로직 — static이므로 JUnit 테스트 가능 */
    public static String formatTime(int seconds) {
        int h = seconds / 3600;
        int m = (seconds % 3600) / 60;
        int s = seconds % 60;
        if (h > 0) return String.format("%d시간 %d분 %d초", h, m, s);
        if (m > 0) return String.format("%d분 %d초", m, s);
        return s + "초";
    }

    /** 순수 로직 — 주어진 경과시간 기준으로 점령 단계 여부 판단 (테스트용) */
    public static boolean isCapturePhase(int elapsed, int total, int capturePhase) {
        return elapsed >= (total - capturePhase);
    }
}
