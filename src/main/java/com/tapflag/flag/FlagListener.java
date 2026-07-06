package com.tapflag.flag;

import com.tapflag.team.TeamManager;
import com.tapflag.timer.GameTimer;
import com.tapflag.util.MessageUtil;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * 깃발 공격 감지:
 *  - EntityDamageByEntityEvent: ArmorStand 엔티티 피격
 *  - PlayerInteractEvent(LEFT_CLICK_BLOCK): 깃발 블록 직접 좌클릭 (펜스/배너 중앙 보완)
 */
public class FlagListener implements Listener {

    private final FlagManager flagManager;
    private final TeamManager teamManager;
    private final GameTimer   gameTimer;

    public FlagListener(FlagManager flagManager, TeamManager teamManager, GameTimer gameTimer) {
        this.flagManager = flagManager;
        this.teamManager = teamManager;
        this.gameTimer   = gameTimer;
    }

    // ─── ArmorStand 피격 (위/아래 영역) ──────────────────────────────────────

    @EventHandler
    public void onFlagHit(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof ArmorStand stand)) return;
        if (!(event.getDamager() instanceof Player player))   return;

        Integer flagId = flagManager.getFlagIdByEntity(stand.getUniqueId());
        if (flagId == null) return;

        event.setCancelled(true);
        applyHit(player, flagId, Math.max(1, (int) Math.round(event.getDamage())));
    }

    // ─── 블록 직접 좌클릭 (중앙 보완) ────────────────────────────────────────

    @EventHandler
    public void onFlagBlockHit(PlayerInteractEvent event) {
        if (event.getAction() != Action.LEFT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;  // 오프핸드 중복 발사 방지
        Block block = event.getClickedBlock();
        if (block == null) return;

        Integer flagId = flagManager.getFlagIdByBlock(block.getLocation());
        if (flagId == null) return;

        event.setCancelled(true);
        applyHit(event.getPlayer(), flagId, weaponDamage(event.getPlayer()));
    }

    // ─── 공통 히트 처리 ───────────────────────────────────────────────────────

    private void applyHit(Player player, int flagId, int damage) {
        if (!gameTimer.isCapturePhase()) {
            player.sendMessage(MessageUtil.warn("현재 점령 불가 시간입니다."));
            return;
        }
        Flag flag = flagManager.getFlagById(flagId);
        if (flag == null) return;

        var team = teamManager.getTeamByPlayer(player.getUniqueId());
        if (team != null && flag.isOwnedBy(team.getId())) {
            player.sendMessage(MessageUtil.warn("자신의 팀 깃발은 공격할 수 없습니다."));
            return;
        }

        boolean captured = flagManager.hitFlag(flagId, player.getUniqueId(), damage);
        if (!captured) {
            player.sendMessage(ChatColor.GRAY + "깃발 [" + FlagManager.getDisplayName(flagId)
                + "] HP: " + flag.getHp() + "/" + flag.getMaxHp()
                + " (데미지: " + damage + ")");
        }
    }

    // ─── 무기 데미지 ──────────────────────────────────────────────────────────

    private static int weaponDamage(Player player) {
        AttributeInstance atk = player.getAttribute(Attribute.ATTACK_DAMAGE);
        return (atk != null) ? Math.max(1, (int) Math.round(atk.getValue())) : 1;
    }
}
