package com.tapflag.listener;

import com.tapflag.GameManager;
import com.tapflag.team.TeamManager;
import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;

import java.util.UUID;

/**
 * 게임 진행 중 비팀원 접속 차단.
 * 팀에 속하지 않은 플레이어를 pendingPlayers에 추가하고 접속을 막는다.
 * 팀장이 팀원 뽑기 GUI에서 영입하면 removePending → 재접속 가능.
 */
public class PlayerLoginListener implements Listener {

    private final GameManager gameManager;
    private final TeamManager teamManager;

    public PlayerLoginListener(GameManager gameManager, TeamManager teamManager) {
        this.gameManager = gameManager;
        this.teamManager = teamManager;
    }

    @EventHandler
    public void onLogin(PlayerLoginEvent event) {
        if (!gameManager.isRunning()) return;

        UUID uuid = event.getPlayer().getUniqueId();

        // 팀원이면 허용
        if (teamManager.getTeamByPlayer(uuid) != null) return;

        // 대기 목록에 추가 (이미 있어도 무시)
        gameManager.addPending(uuid);

        event.disallow(
            PlayerLoginEvent.Result.KICK_OTHER,
            ChatColor.YELLOW + "게임 진행 중입니다.\n"
            + ChatColor.WHITE + "팀장이 당신을 영입할 때까지 기다려주세요.\n"
            + ChatColor.GRAY + "(영입 후 재접속하세요)"
        );
    }
}
