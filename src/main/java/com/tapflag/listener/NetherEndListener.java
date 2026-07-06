package com.tapflag.listener;

import com.tapflag.util.MessageUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerPortalEvent;

/** 지옥 / 엔드 포탈 진입 전면 차단 */
public class NetherEndListener implements Listener {

    @EventHandler
    public void onPortal(PlayerPortalEvent event) {
        event.setCancelled(true);
        if (event.getPlayer() instanceof Player player) {
            player.sendMessage(MessageUtil.warn("지옥과 엔드 진입이 금지되어 있습니다."));
        }
    }
}
