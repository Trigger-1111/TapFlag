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

    private int     elapsedSeconds  = 0;
    private boolean running         = false;
    private boolean gameOver        = false;
    private boolean forceCapture    = false;
    private boolean playtestMode    = false;
    private boolean lastWasCapture  = false;
    private BukkitTask timerTask;

    // 플레이테스트 단계 길이 (초)
    static final int PT_STABLE  = 600;   // 안정 시간 10분
    static final int PT_CAPTURE = 300;   // 점령 시간 5분
    static final int PT_CYCLE   = PT_STABLE + PT_CAPTURE;

    public GameTimer(TapFlagPlugin plugin) {
        this.plugin = plugin;
        this.totalSeconds = plugin.getConfig().getInt("game.total-duration-seconds", 10800);
        this.capturePhaseDuration = plugin.getConfig().getInt("game.capture-phase-duration-seconds", 3600);
    }

    // ─── 시작 ─────────────────────────────────────────────────────────────────

    public void start() {
        if (running) return;
        running = true;
        elapsedSeconds = 0;
        gameOver = false;

        timerTask = new BukkitRunnable() {
            @Override
            public void run() {
                elapsedSeconds++;

                if (playtestMode) {
                    tickPlaytest();
                } else {
                    tickNormal();
                    if (elapsedSeconds >= totalSeconds) {
                        endGame();
                        cancel();
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    /** 플레이테스트 모드로 시작 (10분 안정→5분 점령 반복) */
    public void startPlaytest() {
        playtestMode   = true;
        lastWasCapture = false;
        bcast(ChatColor.YELLOW + "[플레이테스트] 안정 시간 시작! (점령 불가, 10분)");
        start();
    }

    // ─── 틱 처리 ──────────────────────────────────────────────────────────────

    private void tickNormal() {
        int remaining    = totalSeconds - elapsedSeconds;
        int captureStart = totalSeconds - capturePhaseDuration;

        if (elapsedSeconds == captureStart) {
            bcast(ChatColor.GREEN + "⚔ 점령 가능 시간이 시작되었습니다! (남은 시간: 1시간)");
        }
        if (remaining == 1800 || remaining == 600 || remaining == 300 || remaining == 60) {
            plugin.getServer().broadcastMessage(
                MessageUtil.warn("게임 종료까지 " + formatTime(remaining) + " 남았습니다."));
        }
    }

    private void tickPlaytest() {
        boolean nowCapture = isCapturePhase();

        // 단계 전환 감지
        if (!lastWasCapture && nowCapture) {
            // 안정 → 점령
            bcast(ChatColor.GREEN + "⚔ 점령 시간 시작! (5분간 깃발 점령 가능)");
            var um = plugin.getFlagUpgradeManager();
            if (um != null) um.distributeFlagpoints();
        } else if (lastWasCapture && !nowCapture) {
            // 점령 → 안정
            bcast(ChatColor.RED + "안정 시간 시작! (10분간 점령 불가)");
        }
        lastWasCapture = nowCapture;

        // 카운트다운 경고
        int phaseRemaining = getPhaseRemainingSeconds();
        String next = nowCapture ? "안정 시간" : "점령 시간";
        switch (phaseRemaining) {
            case 60 -> bcast(next + "까지 1분!");
            case 30 -> bcast(next + "까지 30초!");
            case 10 -> bcast(next + "까지 10초!");
            case  5, 4, 3, 2, 1 -> bcast(next + "까지 " + phaseRemaining + "초!");
        }
    }

    // ─── 게임 종료 ────────────────────────────────────────────────────────────

    private void endGame() {
        running  = false;
        gameOver = true;

        bcast(ChatColor.GOLD + "" + ChatColor.BOLD + "게임이 종료되었습니다!");

        var um = plugin.getFlagUpgradeManager();
        if (um != null) um.distributeFlagpoints();

        plugin.getServer().broadcastMessage(
            MessageUtil.warn("5분간 마무리 시간입니다. 전황·피해를 확인하세요. (점령 불가)"));
        startShutdownCountdown();
    }

    private void startShutdownCountdown() {
        new BukkitRunnable() {
            int secondsLeft = 300;

            @Override
            public void run() {
                secondsLeft--;
                switch (secondsLeft) {
                    case 240 -> shutdownBcast("서버 종료까지 4분...");
                    case 180 -> shutdownBcast("서버 종료까지 3분...");
                    case 120 -> shutdownBcast("서버 종료까지 2분...");
                    case  60 -> shutdownBcast("서버 종료까지 1분!");
                    case  30 -> shutdownBcast("서버 종료까지 30초!");
                    case  10 -> shutdownBcast("서버 종료까지 10초!");
                    case   5, 4, 3, 2, 1 -> shutdownBcast("서버 종료까지 " + secondsLeft + "초!");
                }
                if (secondsLeft <= 0) {
                    plugin.getServer().broadcastMessage(
                        MessageUtil.prefix() + ChatColor.RED + "" + ChatColor.BOLD
                        + "서버를 종료합니다. 수고하셨습니다!");
                    cancel();
                    plugin.getServer().shutdown();
                }
            }

            private void shutdownBcast(String msg) {
                plugin.getServer().broadcastMessage(MessageUtil.warn("[마무리] " + msg));
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    // ─── 정지 ─────────────────────────────────────────────────────────────────

    public void stop() {
        running        = false;
        playtestMode   = false;
        lastWasCapture = false;
        if (timerTask != null) {
            timerTask.cancel();
            timerTask = null;
        }
    }

    // ─── 플레이테스트 타이머 제어 ──────────────────────────────────────────────

    /** N초만큼 타이머를 앞으로 건너뜀 */
    public void skipTime(int seconds) {
        elapsedSeconds = Math.max(0, elapsedSeconds + seconds);
        // 건너뛰기 후 lastWasCapture를 현재 상태로 동기화 (이중 전환 방지)
        lastWasCapture = isCapturePhaseRaw();
    }

    // ─── 상태 조회 ────────────────────────────────────────────────────────────

    public boolean isRunning()         { return running; }
    public boolean isGameOver()        { return gameOver; }
    public boolean isPlaytestMode()    { return playtestMode; }
    public int     getElapsedSeconds() { return elapsedSeconds; }

    public int getRemainingSeconds() {
        if (playtestMode) return getPhaseRemainingSeconds();
        return Math.max(0, totalSeconds - elapsedSeconds);
    }

    /** 현재 단계의 남은 초 (플레이테스트 전용) */
    public int getPhaseRemainingSeconds() {
        int pos = elapsedSeconds % PT_CYCLE;
        return pos < PT_STABLE ? PT_STABLE - pos : PT_CYCLE - pos;
    }

    /** 현재 단계의 총 길이 (플레이테스트 전용) */
    public int getPhaseTotalSeconds() {
        int pos = elapsedSeconds % PT_CYCLE;
        return pos < PT_STABLE ? PT_STABLE : PT_CAPTURE;
    }

    public boolean isCapturePhase() {
        if (forceCapture) return true;
        return isCapturePhaseRaw();
    }

    private boolean isCapturePhaseRaw() {
        if (!running) return false;
        if (playtestMode) return (elapsedSeconds % PT_CYCLE) >= PT_STABLE;
        return elapsedSeconds >= (totalSeconds - capturePhaseDuration);
    }

    public void    setForceCapture(boolean force) { this.forceCapture = force; }
    public boolean isForceCapture()               { return forceCapture; }
    public int     getTotalSeconds()              { return totalSeconds; }
    public int     getCapturePhaseDuration()      { return capturePhaseDuration; }

    public String getStatusDisplay() {
        if (!running) return ChatColor.GRAY + "타이머 미실행";
        String phase = isCapturePhase()
            ? ChatColor.GREEN + "점령 가능"
            : ChatColor.RED   + "점령 불가";
        if (playtestMode) {
            return "[플레이테스트] 경과: " + formatTime(elapsedSeconds)
                + " | 단계 남은: " + formatTime(getPhaseRemainingSeconds())
                + " | " + phase;
        }
        return "경과: " + formatTime(elapsedSeconds)
            + " | 남은: " + formatTime(getRemainingSeconds())
            + " | " + phase;
    }

    // ─── 유틸리티 ─────────────────────────────────────────────────────────────

    private void bcast(String msg) {
        plugin.getServer().broadcastMessage(MessageUtil.prefix() + msg);
    }

    public static String formatTime(int seconds) {
        int h = seconds / 3600;
        int m = (seconds % 3600) / 60;
        int s = seconds % 60;
        if (h > 0) return String.format("%d시간 %d분 %d초", h, m, s);
        if (m > 0) return String.format("%d분 %d초", m, s);
        return s + "초";
    }

}
