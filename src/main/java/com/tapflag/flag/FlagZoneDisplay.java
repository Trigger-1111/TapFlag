package com.tapflag.flag;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * 깃발마다 두 개의 원을 파티클로 표시.
 *  - 영역(50블록): 황금색 DUST
 *  - 감지영역(100블록): 하늘색 DUST
 * 근처 플레이어(반경+20블록)에게만 개별 전송.
 */
public class FlagZoneDisplay extends BukkitRunnable {

    private static final double ZONE_RADIUS   = 50.0;
    private static final double DETECT_RADIUS = 100.0;

    // 호(arc) 간격 약 4블록 → 원 길이 / 4
    private static final int ZONE_STEPS   = (int) (2 * Math.PI * ZONE_RADIUS   / 4); // ~78
    private static final int DETECT_STEPS = (int) (2 * Math.PI * DETECT_RADIUS / 4); // ~157

    private static final Particle.DustOptions ZONE_DUST =
        new Particle.DustOptions(Color.fromRGB(255, 200, 0), 1.5f);   // 황금
    private static final Particle.DustOptions DETECT_DUST =
        new Particle.DustOptions(Color.fromRGB(80, 180, 255), 1.0f);  // 하늘

    private final FlagManager flagManager;

    public FlagZoneDisplay(FlagManager flagManager) {
        this.flagManager = flagManager;
    }

    @Override
    public void run() {
        for (Flag flag : flagManager.getAllFlags().values()) {
            Location center = flag.getLocation();
            World world = center.getWorld();
            if (world == null) continue;

            double cx = center.getX();
            double cy = center.getY() + 0.5;  // 지표면 바로 위
            double cz = center.getZ();

            // 점령된 깃발만 구역 표시
            if (flag.isNeutral()) continue;

            for (Player p : world.getPlayers()) {
                double dist2 = p.getLocation().distanceSquared(center);
                if (dist2 > (DETECT_RADIUS + 20) * (DETECT_RADIUS + 20)) continue;

                drawCircle(p, cx, cy, cz, ZONE_RADIUS,   ZONE_STEPS,   ZONE_DUST);
                drawCircle(p, cx, cy, cz, DETECT_RADIUS, DETECT_STEPS, DETECT_DUST);
            }
        }
    }

    private static void drawCircle(Player player,
                                   double cx, double cy, double cz,
                                   double radius, int steps,
                                   Particle.DustOptions dust) {
        double dAngle = 2 * Math.PI / steps;
        for (int i = 0; i < steps; i++) {
            double a = i * dAngle;
            player.spawnParticle(
                Particle.DUST,
                cx + radius * Math.cos(a), cy, cz + radius * Math.sin(a),
                1, 0, 0, 0, 0, dust
            );
        }
    }
}
