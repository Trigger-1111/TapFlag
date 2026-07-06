package com.tapflag.listener;

import com.tapflag.GameManager;
import com.tapflag.TapFlagPlugin;
import com.tapflag.util.MessageUtil;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

/**
 * 게임 진행 중 Y좌표 범위 밖의 채굴/건축을 차단.
 * config.yml: map.min-mine-y (이 값 이하 굴착 금지), map.max-build-y (이 값 이상 건축 금지)
 */
public class BuildListener implements Listener {

    private final TapFlagPlugin plugin;
    private final GameManager gameManager;

    public BuildListener(TapFlagPlugin plugin, GameManager gameManager) {
        this.plugin = plugin;
        this.gameManager = gameManager;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!gameManager.isRunning()) return;
        int minY = plugin.getConfig().getInt("map.min-mine-y", -60);
        int blockY = event.getBlock().getY();
        if (blockY < minY) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(MessageUtil.warn("Y=" + minY + " 이하로는 채굴할 수 없습니다."));
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!gameManager.isRunning()) return;
        int maxY = plugin.getConfig().getInt("map.max-build-y", 200);
        int blockY = event.getBlock().getY();
        if (blockY > maxY) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(MessageUtil.warn("Y=" + maxY + " 이상으로는 건축할 수 없습니다."));
        }
    }
}
