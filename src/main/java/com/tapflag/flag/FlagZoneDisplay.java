package com.tapflag.flag;

import com.tapflag.TapFlagPlugin;
import com.tapflag.team.TeamManager;
import org.bukkit.Color;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;

/**
 * 점령된 깃발마다 두 개의 사각형 테두리를 지형 표면에 파티클로 표시.
 *  - 영역 (반변 25블록): 황금색 DUST
 *  - 감지영역 (반변 50블록): 하늘색 DUST
 * 적 감지영역 진입 시 발광(GLOWING) 효과 적용.
 */
public class FlagZoneDisplay extends BukkitRunnable {

    /** 영역 사각형 반변 (중심 → 각 변까지 블록 수) */
    public static final double ZONE_HALF   = 25.0;
    public static final double DETECT_HALF = 50.0;

    private static final double DETECT_HALF_SQ = DETECT_HALF * DETECT_HALF;

    private static final Particle.DustOptions ZONE_DUST =
        new Particle.DustOptions(Color.fromRGB(255, 200, 0), 2.5f);   // 황금
    private static final Particle.DustOptions DETECT_DUST =
        new Particle.DustOptions(Color.fromRGB(80, 180, 255), 2.0f);  // 하늘

    /** 발광 지속: 80틱(4초) — 60틱 주기보다 여유 있게 */
    private static final int GLOW_DURATION = 80;

    private final TapFlagPlugin plugin;
    private final FlagManager   flagManager;
    private final TeamManager   teamManager;

    /** 지형 Y 높이 캐시: (blockX << 32 | blockZ) → surfaceY */
    private final Map<Long, Integer> heightCache = new HashMap<>();

    public FlagZoneDisplay(TapFlagPlugin plugin, FlagManager flagManager, TeamManager teamManager) {
        this.plugin      = plugin;
        this.flagManager = flagManager;
        this.teamManager = teamManager;
    }

    // ─── BukkitRunnable ──────────────────────────────────────────────────────

    @Override
    public void run() {
        var flags = flagManager.getAllFlags().values();

        for (Player p : plugin.getServer().getOnlinePlayers()) {
            boolean inEnemyDetect = false;

            for (Flag flag : flags) {
                Location center = flag.getLocation();
                World world = center.getWorld();
                if (world == null || !world.equals(p.getWorld())) continue;

                double dist2 = p.getLocation().distanceSquared(center);

                // ── 파티클: 점령된 깃발만, 감지영역+20 이내 플레이어에게 ──
                if (!flag.isNeutral() && dist2 <= (DETECT_HALF + 20) * (DETECT_HALF + 20)) {
                    drawSquare(p, world, center.getBlockX(), center.getBlockZ(), ZONE_HALF,   ZONE_DUST);
                    drawSquare(p, world, center.getBlockX(), center.getBlockZ(), DETECT_HALF, DETECT_DUST);
                }

                // ── 발광 판정: 적 감지영역 내 ──────────────────────────────
                if (!flag.isNeutral() && isEnemyFlag(flag, p) && dist2 <= DETECT_HALF_SQ) {
                    inEnemyDetect = true;
                }
            }

            // ── 발광 적용 / 해제 ─────────────────────────────────────────
            if (inEnemyDetect) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, GLOW_DURATION, 0, true, false));
            } else {
                p.removePotionEffect(PotionEffectType.GLOWING);
            }
        }
    }

    // ─── 내부 ────────────────────────────────────────────────────────────────

    /** 깃발이 해당 플레이어에게 "적" 깃발인지 판정. */
    private boolean isEnemyFlag(Flag flag, Player p) {
        var pTeam = teamManager.getTeamByPlayer(p.getUniqueId());
        if (pTeam == null) return true; // 방랑자 → 모든 점령 깃발이 적
        return !flag.isOwnedBy(pTeam.getId());
    }

    /**
     * 정사각형 테두리를 지형 표면에 파티클로 그림 (1블록 간격).
     * @param half 중심에서 각 변까지의 블록 수 (반변)
     */
    private void drawSquare(Player player, World world,
                            int cx, int cz, double half,
                            Particle.DustOptions dust) {
        int h    = (int) half;
        int side = h * 2;
        for (int i = 0; i <= side; i++) {
            int t = -h + i;
            spawnAt(player, world, cx + t, cz - h, dust); // 북쪽 변
            spawnAt(player, world, cx + t, cz + h, dust); // 남쪽 변
            spawnAt(player, world, cx - h, cz + t, dust); // 서쪽 변
            spawnAt(player, world, cx + h, cz + t, dust); // 동쪽 변
        }
    }

    private void spawnAt(Player player, World world, int bx, int bz, Particle.DustOptions dust) {
        int by = getGroundY(world, bx, bz);
        player.spawnParticle(Particle.DUST, bx + 0.5, by + 1.1, bz + 0.5, 1, 0, 0, 0, 0, dust);
    }

    /** 지형 Y 높이를 캐시에서 가져옴 (처음 조회 시 HeightMap 사용). */
    private int getGroundY(World world, int x, int z) {
        long key = ((long) x << 32) | (z & 0xFFFFFFFFL);
        return heightCache.computeIfAbsent(key, k -> {
            world.getChunkAt(x >> 4, z >> 4).load(false);
            return world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        });
    }
}
