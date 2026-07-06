package com.tapflag.hud;

import com.tapflag.GameManager;
import com.tapflag.TapFlagPlugin;
import com.tapflag.team.Team;
import com.tapflag.team.TeamManager;
import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scoreboard.*;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

/**
 * HUD 통합 관리자.
 *  - 이름표 위 팀/방랑자 프리픽스 (Scoreboard Team)
 *  - 우측 사이드바: 팀·깃발·팀원 생존·금화
 *  - 1초 주기 갱신 (BukkitRunnable)
 */
@SuppressWarnings("UnstableApiUsage")
public class HudManager extends BukkitRunnable implements Listener {

    private static final int    MAX_LINES = 20;
    private static final String OBJ_NAME  = "tapflag_hud";

    private final TapFlagPlugin plugin;
    private final TeamManager   teamManager;

    private final Map<UUID, Scoreboard> boards     = new HashMap<>();
    /** UUID → 밴 만료 시각(ms) — 사망 직후 저장 */
    private final Map<UUID, Long>       deathExpiry = new HashMap<>();

    public HudManager(TapFlagPlugin plugin, TeamManager teamManager) {
        this.plugin      = plugin;
        this.teamManager = teamManager;
    }

    // ─── Listener (join / quit) ───────────────────────────────────────────────

    @EventHandler
    public void onJoin(PlayerJoinEvent e) { initPlayer(e.getPlayer()); }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) { cleanupPlayer(e.getPlayer()); }

    // ─── BukkitRunnable (1초 주기) ────────────────────────────────────────────

    @Override
    public void run() {
        for (Player p : plugin.getServer().getOnlinePlayers()) updatePlayer(p);
    }

    // ─── 공개 API ─────────────────────────────────────────────────────────────

    public void initPlayer(Player p) {
        Scoreboard board = plugin.getServer().getScoreboardManager().getNewScoreboard();
        boards.put(p.getUniqueId(), board);
        p.setScoreboard(board);
        updatePlayer(p);
    }

    public void cleanupPlayer(Player p) { boards.remove(p.getUniqueId()); }

    /** DeathBanListener 에서 호출 — 사망 시각 기록 */
    public void recordDeath(UUID uuid, long banExpiryMillis) {
        deathExpiry.put(uuid, banExpiryMillis);
    }

    /** 팀 합류, 깃발 점령, 게임 시작/종료 시 즉시 전체 갱신 */
    public void refreshAll() {
        for (Player p : plugin.getServer().getOnlinePlayers()) updatePlayer(p);
    }

    // ─── 내부 업데이트 ────────────────────────────────────────────────────────

    private void updatePlayer(Player viewer) {
        Scoreboard board = boards.computeIfAbsent(
            viewer.getUniqueId(),
            k -> plugin.getServer().getScoreboardManager().getNewScoreboard()
        );
        if (!viewer.getScoreboard().equals(board)) viewer.setScoreboard(board);

        updateNameTags(board);
        updateSidebar(viewer, board);
    }

    // ─── 이름표 프리픽스 ──────────────────────────────────────────────────────

    private void updateNameTags(Scoreboard board) {
        GameManager gm = plugin.getGameManager();
        if (gm == null || !gm.isRunning()) return;

        ensureTag(board, "_wanderer_", "§7[방랑자] ", ChatColor.GRAY);
        for (Team t : teamManager.getAllTeams()) {
            ChatColor c = teamChatColor(t.getId());
            ensureTag(board, "_" + t.getId() + "_", c + "[" + t.getId() + "] ", c);
        }

        for (Player p : plugin.getServer().getOnlinePlayers()) {
            Team t     = teamManager.getTeamByPlayer(p.getUniqueId());
            String key = t == null ? "_wanderer_" : "_" + t.getId() + "_";
            var tag    = board.getTeam(key);
            if (tag != null && !tag.hasEntry(p.getName())) {
                board.getTeams().forEach(bt -> bt.removeEntry(p.getName()));
                tag.addEntry(p.getName());
            }
        }
    }

    private void ensureTag(Scoreboard board, String name, String prefix, ChatColor color) {
        var tag = board.getTeam(name);
        if (tag == null) tag = board.registerNewTeam(name);
        tag.setPrefix(prefix);
        tag.setColor(color);
    }

    // ─── 사이드바 ─────────────────────────────────────────────────────────────

    private void updateSidebar(Player viewer, Scoreboard board) {
        Objective obj = board.getObjective(OBJ_NAME);
        if (obj == null) {
            obj = board.registerNewObjective(OBJ_NAME, Criteria.DUMMY,
                Component.text("★ TapFlag ★")
                    .color(TextColor.fromHexString("#FFD700"))
                    .decorate(TextDecoration.BOLD));
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        }

        List<Component> lines = buildLines(viewer);

        // 미사용 슬롯 숨김
        for (int i = lines.size(); i < MAX_LINES; i++) board.resetScores("l" + i);

        // 라인 설정 (높은 score = 위)
        final Objective finalObj = obj;
        for (int i = 0; i < lines.size(); i++) {
            Score s = finalObj.getScore("l" + i);
            s.setScore(lines.size() - i);
            s.customName(lines.get(i));
            s.numberFormat(NumberFormat.blank());
        }
    }

    // ─── 사이드바 내용 빌드 ───────────────────────────────────────────────────

    private List<Component> buildLines(Player viewer) {
        List<Component> lines = new ArrayList<>();
        GameManager gm = plugin.getGameManager();

        lines.add(sep());

        if (gm == null || !gm.isRunning()) {
            lines.add(Component.text("게임 대기 중", NamedTextColor.GRAY));
            return lines;
        }

        Team myTeam = teamManager.getTeamByPlayer(viewer.getUniqueId());

        if (myTeam == null) {
            // 방랑자
            lines.add(Component.text("신분: ", NamedTextColor.WHITE)
                .append(Component.text("방랑자", NamedTextColor.GRAY)));
            lines.add(sep());
            lines.add(Component.text("깃발 점령 → 팀 합류", NamedTextColor.YELLOW));
        } else {
            TextColor tc = teamTextColor(myTeam.getId());

            // 팀명
            lines.add(Component.text("팀: [", NamedTextColor.WHITE)
                .append(Component.text(myTeam.getId(), tc))
                .append(Component.text("]", NamedTextColor.WHITE)));
            lines.add(sep());

            // 보유 깃발
            String flags = myTeam.getOwnedFlagIds().isEmpty() ? "없음"
                : myTeam.getOwnedFlagIds().stream().sorted()
                    .map(id -> "#" + id).reduce((a, b) -> a + " " + b).orElse("없음");
            lines.add(Component.text("깃발: ", NamedTextColor.YELLOW)
                .append(Component.text(flags, NamedTextColor.WHITE)));
            lines.add(sep());

            // 팀원 생존 상태
            lines.add(Component.text("팀원:", NamedTextColor.GREEN));
            for (UUID uuid : myTeam.getMembers()) {
                if (isRealPlayer(uuid)) lines.add(memberLine(uuid));
            }
            lines.add(sep());

            // 금화
            lines.add(Component.text("금화: ", NamedTextColor.GOLD)
                .append(Component.text(myTeam.getGold() + "개", NamedTextColor.WHITE)));
        }

        return lines;
    }

    private Component memberLine(UUID uuid) {
        Player p    = plugin.getServer().getPlayer(uuid);
        String name = resolveName(uuid);

        if (p != null && p.isOnline()) {
            return Component.text("  " + name + " ", NamedTextColor.WHITE)
                .append(Component.text("●", NamedTextColor.GREEN));
        }

        Long expiry = deathExpiry.get(uuid);
        if (expiry != null && expiry > System.currentTimeMillis()) {
            return Component.text("  " + name + " ", NamedTextColor.WHITE)
                .append(Component.text("✗ " + fmtMs(expiry - System.currentTimeMillis()),
                    NamedTextColor.RED));
        }

        return Component.text("  " + name, NamedTextColor.DARK_GRAY);
    }

    // ─── 유틸리티 ─────────────────────────────────────────────────────────────

    private Component sep() {
        return Component.text("──────────────", NamedTextColor.DARK_GRAY);
    }

    private static String fmtMs(long ms) {
        long s = ms / 1000;
        return String.format("%d:%02d", s / 60, s % 60);
    }

    @SuppressWarnings("deprecation")
    private String resolveName(UUID uuid) {
        Player p = plugin.getServer().getPlayer(uuid);
        if (p != null) return p.getName();
        String name = plugin.getServer().getOfflinePlayer(uuid).getName();
        return name != null ? name : "???";
    }

    private boolean isRealPlayer(UUID uuid) {
        Player p = plugin.getServer().getPlayer(uuid);
        if (p != null) return true;
        OfflinePlayer op = plugin.getServer().getOfflinePlayer(uuid);
        return op.hasPlayedBefore();
    }

    // ─── 팀 색상 매핑 ─────────────────────────────────────────────────────────

    static ChatColor teamChatColor(String id) {
        return switch (id) {
            case "빨강" -> ChatColor.RED;
            case "파랑" -> ChatColor.BLUE;
            case "초록" -> ChatColor.GREEN;
            case "노랑" -> ChatColor.YELLOW;
            case "보라" -> ChatColor.DARK_PURPLE;
            case "주황" -> ChatColor.GOLD;
            case "하늘" -> ChatColor.AQUA;
            case "분홍" -> ChatColor.LIGHT_PURPLE;
            default     -> ChatColor.WHITE;
        };
    }

    static TextColor teamTextColor(String id) {
        return switch (id) {
            case "빨강" -> NamedTextColor.RED;
            case "파랑" -> NamedTextColor.BLUE;
            case "초록" -> NamedTextColor.GREEN;
            case "노랑" -> NamedTextColor.YELLOW;
            case "보라" -> NamedTextColor.DARK_PURPLE;
            case "주황" -> NamedTextColor.GOLD;
            case "하늘" -> NamedTextColor.AQUA;
            case "분홍" -> NamedTextColor.LIGHT_PURPLE;
            default     -> NamedTextColor.WHITE;
        };
    }
}
