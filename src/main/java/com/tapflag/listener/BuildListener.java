package com.tapflag.listener;

import com.tapflag.GameManager;
import com.tapflag.TapFlagPlugin;
import com.tapflag.flag.Flag;
import com.tapflag.flag.FlagManager;
import com.tapflag.flag.FlagZoneDisplay;
import com.tapflag.team.Team;
import com.tapflag.team.TeamManager;
import com.tapflag.util.MessageUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

/**
 * 게임 중 블록 파괴/설치 제한:
 *  - 깃발 구성 블록(기반암/기둥/배너) 파괴 차단
 *  - map.min-mine-y 이하 채굴 금지
 *  - map.max-build-y 이상 건축 금지
 *  - BARREL은 자기 팀 깃발 ZONE_HALF(25블록) 이내에만 설치 가능
 */
public class BuildListener implements Listener {

    private static final double ZONE_HALF = FlagZoneDisplay.ZONE_HALF;

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

        // Y 범위 제한
        int minY = plugin.getConfig().getInt("map.min-mine-y", -60);
        if (loc.getBlockY() < minY) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(MessageUtil.warn("Y=" + minY + " 이하로는 채굴할 수 없습니다."));
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!gameManager.isRunning()) return;

        // 통(BARREL) — 자기 팀 깃발 영역에만 설치 허용
        if (event.getBlock().getType() == Material.BARREL) {
            Team team = teamManager.getTeamByPlayer(event.getPlayer().getUniqueId());
            if (team == null) {
                event.setCancelled(true);
                event.getPlayer().sendMessage(MessageUtil.warn("팀에 속해야 통을 설치할 수 있습니다."));
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
                    MessageUtil.warn("통은 자기 팀 깃발 " + (int)ZONE_HALF + "블록 이내에만 설치할 수 있습니다."));
            }
            return;
        }

        int maxY = plugin.getConfig().getInt("map.max-build-y", 200);
        if (event.getBlock().getY() > maxY) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(MessageUtil.warn("Y=" + maxY + " 이상으로는 건축할 수 없습니다."));
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
}
