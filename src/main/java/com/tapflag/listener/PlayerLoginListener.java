package com.tapflag.listener;

import com.tapflag.GameManager;
import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;

/** 팀원 모집 대기 중인 플레이어의 접속을 차단 */
public class PlayerLoginListener implements Listener {

    private final GameManager gameManager;

    public PlayerLoginListener(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    @EventHandler
    public void onLogin(PlayerLoginEvent event) {
        if (!gameManager.isRunning()) return;
        if (!gameManager.isPending(event.getPlayer().getUniqueId())) return;

        event.disallow(
            PlayerLoginEvent.Result.KICK_OTHER,
            ChatColor.YELLOW + "팀원 모집 대기 중입니다.\n" +
            ChatColor.WHITE + "팀장이 당신을 뽑을 때까지 기다려주세요."
        );
    }
}
