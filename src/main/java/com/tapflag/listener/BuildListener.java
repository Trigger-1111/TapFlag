package com.tapflag.listener;

import com.tapflag.GameManager;
import com.tapflag.TapFlagPlugin;
import com.tapflag.flag.Flag;
import com.tapflag.flag.FlagManager;
import com.tapflag.flag.FlagZoneDisplay;
import com.tapflag.team.Team;
import com.tapflag.team.TeamManager;
import com.tapflag.util.MessageUtil;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 게임 중 블록 파괴/설치 제한:
 *  - 깃발 구성 블록(기반암/기둥/배너) 파괴 차단
 *  - map.min-mine-y 이하 채굴 금지
 *  - map.max-build-y 이상 건축 금지
 *  - BARREL은 자기 팀 깃발 ZONE_HALF(25블록) 이내에만 설치 가능
 */
public class BuildListener implements Listener {

    private static final double ZONE_HALF    = FlagZoneDisplay.ZONE_HALF;
    private static final int    DEPTH_LIMIT  = 3;    // 지표면 아래 최대 채굴 깊이
    private static final int    HEIGHT_LIMIT = 50;   // 지표면 위 최대 건축 높이

    /** X/Z 컬럼별 지표 Y 캐시 */
    private final Map<Long, Integer> surfaceCache = new HashMap<>();

    /** 자기 팀 깃발 영역에만 설치 가능한 블록 목록 */
    private static final Set<Material> ZONE_ONLY = Set.of(
        Material.BARREL,
        Material.ENCHANTING_TABLE,
        Material.BOOKSHELF,
        Material.SMITHING_TABLE,
        // 주민 직업 블록
        Material.BLAST_FURNACE,
        Material.SMOKER,
        Material.CARTOGRAPHY_TABLE,
        Material.FLETCHING_TABLE,
        Material.GRINDSTONE,
        Material.LECTERN,
        Material.LOOM,
        Material.STONECUTTER,
        Material.COMPOSTER,
        Material.BREWING_STAND
    );

    private final TapFlagPlugin plugin;
    private final GameManager   gameManager;
    private final FlagManager   flagManager;
    private final TeamManager   teamManager;

    public BuildListener(TapFlagPlugin plugin, GameManager gameManager,
                         FlagManager flagManager, TeamManager teamManager) {
        this.plugin      = plugin;
        this.gameManager = gameManager;
        this.flagManager = flagManager;
        this.teamManager = teamManager;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!gameManager.isRunning()) return;

        Location loc = event.getBlock().getLocation();

        // 깃발 블록 파괴 차단
        for (Flag flag : flagManager.getAllFlags().values()) {
            if (isFlagBlock(flag, loc)) {
                event.setCancelled(true);
                event.getPlayer().sendMessage(MessageUtil.warn("깃발 블록은 파괴할 수 없습니다."));
                return;
            }
        }

        // 지형 기준 채굴 깊이 제한
        int surfaceY = getSurfaceY(loc.getWorld(), loc.getBlockX(), loc.getBlockZ());
        if (loc.getBlockY() < surfaceY - DEPTH_LIMIT) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(
                MessageUtil.warn("지표면에서 " + DEPTH_LIMIT + "칸 이하로는 채굴할 수 없습니다."));
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!gameManager.isRunning()) return;

        // 영역 제한 블록 — 자기 팀 소유 깃발 ZONE_HALF(25블록) 이내에만 설치 허용
        if (ZONE_ONLY.contains(event.getBlock().getType())) {
            Team team = teamManager.getTeamByPlayer(event.getPlayer().getUniqueId());
            if (team == null) {
                event.setCancelled(true);
                event.getPlayer().sendMessage(MessageUtil.warn("팀에 속해야 이 블록을 설치할 수 있습니다."));
                return;
            }
            Location bLoc = event.getBlock().getLocation();
            boolean inZone = false;
            for (Flag flag : flagManager.getAllFlags().values()) {
                if (!flag.isOwnedBy(team.getId())) continue;
                Location fLoc = flag.getLocation();
                double dx = Math.abs(bLoc.getX() - fLoc.getX());
                double dz = Math.abs(bLoc.getZ() - fLoc.getZ());
                if (dx <= ZONE_HALF && dz <= ZONE_HALF) { inZone = true; break; }
            }
            if (!inZone) {
                event.setCancelled(true);
                event.getPlayer().sendMessage(
                    MessageUtil.warn("이 블록은 자기 팀 깃발 " + (int)ZONE_HALF + "블록 이내에만 설치할 수 있습니다."));
            }
            return;
        }

        // 지형 기준 건축 높이 제한
        Location bLoc = event.getBlock().getLocation();
        int surfaceY2 = getSurfaceY(bLoc.getWorld(), bLoc.getBlockX(), bLoc.getBlockZ());
        if (bLoc.getBlockY() > surfaceY2 + HEIGHT_LIMIT) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(
                MessageUtil.warn("지표면에서 " + HEIGHT_LIMIT + "칸 이상으로는 건축할 수 없습니다."));
        }
    }

    private boolean isFlagBlock(Flag flag, Location loc) {
        return locMatch(flag.getGroundBlock(), loc)
            || locMatch(flag.getPoleBlock(),   loc)
            || locMatch(flag.getBannerBlock(),  loc);
    }

    private boolean locMatch(Location a, Location b) {
        if (a == null) return false;
        return a.getBlockX() == b.getBlockX()
            && a.getBlockY() == b.getBlockY()
            && a.getBlockZ() == b.getBlockZ();
    }

    private int getSurfaceY(World world, int x, int z) {
        long key = ((long) x << 32) | (z & 0xFFFFFFFFL);
        return surfaceCache.computeIfAbsent(key, k ->
            world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES));
    }
}
