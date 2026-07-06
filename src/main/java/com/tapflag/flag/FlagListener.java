package com.tapflag.flag;

import com.tapflag.team.TeamManager;
import com.tapflag.timer.GameTimer;
import com.tapflag.util.MessageUtil;
import org.bukkit.ChatColor;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/**
 * 얇은 이벤트 핸들러 — 판단 로직은 FlagManager / GameTimer 에 위임
 */
public class FlagListener implements Listener {

    private final FlagManager flagManager;
    private final TeamManager teamManager;
    private final GameTimer gameTimer;

    public FlagListener(FlagManager flagManager, TeamManager teamManager, GameTimer gameTimer) {
        this.flagManager = flagManager;
        this.teamManager = teamManager;
        this.gameTimer = gameTimer;
    }

    @EventHandler
    public void onFlagHit(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof ArmorStand stand)) return;
        if (!(event.getDamager() instanceof Player player)) return;

        Integer flagId = flagManager.getFlagIdByEntity(stand.getUniqueId());
        if (flagId == null) return;

        event.setCancelled(true); // 기본 데미지 취소, 직접 HP 처리

        if (!gameTimer.isCapturePhase()) {
            player.sendMessage(MessageUtil.warn("현재 점령 불가 시간입니다."));
            return;
        }

        Flag flag = flagManager.getFlagById(flagId);
        if (flag == null) return;

        var team = teamManager.getTeamByPlayer(player.getUniqueId());
        if (team == null) {
            // 방랑자: 중립 깃발만 공격 가능 (HP 0 → 팀 자동 합류)
            if (!flag.isNeutral()) {
                player.sendMessage(MessageUtil.warn("방랑자는 이미 점령된 깃발을 공격할 수 없습니다."));
                return;
            }
        } else {
            // 팀원: 자신의 깃발 공격 불가
            if (flag.isOwnedBy(team.getId())) {
                player.sendMessage(MessageUtil.warn("자신의 팀 깃발은 공격할 수 없습니다."));
                return;
            }
        }

        // 실제 무기 데미지 사용 (쿨타임·인챈트 자동 반영)
        int damage = (int) Math.max(1, Math.round(event.getDamage()));
        boolean captured = flagManager.hitFlag(flagId, player.getUniqueId(), damage);

        if (!captured) {
            player.sendMessage(ChatColor.GRAY + "깃발 #" + flagId
                + " HP: " + flag.getHp() + "/" + flag.getMaxHp()
                + " (데미지: " + damage + ")");
        }
    }
}
