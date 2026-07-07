package com.tapflag.flag;

import com.tapflag.TapFlagPlugin;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * 매 40틱(2초)마다 각 깃발 위에 팀 색상 빛기둥(DUST 파티클)을 생성.
 * world.spawnParticle force=true 로 모든 플레이어에게 장거리에서도 표시.
 */
public class FlagBeaconTask extends BukkitRunnable {

    private final TapFlagPlugin plugin;

    public FlagBeaconTask(TapFlagPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        var flagManager = plugin.getFlagManager();
        if (flagManager == null) return;

        for (Flag flag : flagManager.getAllFlags().values()) {
            Location base = flag.getLocation();
            var world = base.getWorld();
            if (world == null) continue;

            Color color = teamColor(flag.getOwningTeamId());
            var dust = new Particle.DustOptions(color, 2.0f);

            double x = base.getX();
            double baseY = base.getY();
            double z = base.getZ();

            // 깃발 위 64블록까지 3블록 간격으로 파티클 기둥 생성
            for (int dy = 2; dy <= 64; dy += 3) {
                world.spawnParticle(Particle.DUST,
                    x, baseY + dy, z,
                    2, 0.15, 0.0, 0.15, 0, dust, true);
            }
        }
    }

    private static Color teamColor(String teamId) {
        if (teamId == null) return Color.WHITE;
        return switch (teamId) {
            case "Red",    "빨강" -> Color.RED;
            case "Blue",   "파랑" -> Color.BLUE;
            case "Yellow", "노랑" -> Color.YELLOW;
            case "Green",  "초록" -> Color.LIME;
            case "Purple", "보라" -> Color.PURPLE;
            case "주황"           -> Color.ORANGE;
            default              -> Color.WHITE;
        };
    }
}
