package com.tapflag.listener;

import com.tapflag.GameManager;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * 지형 기준 수직 이동 제한.
 * - 지표면 아래 3칸 초과 하강 차단
 * - 지표면 위  50칸 초과 상승 차단
 * 지형마다 높이가 다르므로 현재 X/Z의 최고 블록 Y를 기준으로 계산.
 */
public class MovementLimitListener implements Listener {

    private static final int DEPTH_LIMIT  = 3;
    private static final int HEIGHT_LIMIT = 50;

    private final GameManager gameManager;
    // X/Z 컬럼별 지표 Y 캐시 (게임 중 지형이 크게 바뀌지 않으므로 영속)
    private final Map<Long, Integer> surfaceCache = new HashMap<>();

    public MovementLimitListener(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!gameManager.isRunning()) return;

        Location from = event.getFrom();
        Location to   = event.getTo();
        if (to == null) return;
        // Y 좌표가 바뀔 때만 검사 (수평 이동 무시)
        if (from.getBlockY() == to.getBlockY()) return;

        Player player = event.getPlayer();
        // op는 제한 없음
        if (player.isOp()) return;

        int surfaceY = getSurfaceY(to.getWorld(), to.getBlockX(), to.getBlockZ());
        double minY = surfaceY - DEPTH_LIMIT;
        double maxY = surfaceY + HEIGHT_LIMIT;

        if (to.getY() < minY) {
            Location safe = to.clone();
            safe.setY(minY);
            event.setTo(safe);
        } else if (to.getY() > maxY) {
            Location safe = to.clone();
            safe.setY(maxY);
            event.setTo(safe);
        }
    }

    private int getSurfaceY(World world, int x, int z) {
        long key = ((long) x << 32) | (z & 0xFFFFFFFFL);
        return surfaceCache.computeIfAbsent(key, k ->
            world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES));
    }

    /** 게임 종료 시 캐시 초기화 */
    public void clearCache() {
        surfaceCache.clear();
    }
}
