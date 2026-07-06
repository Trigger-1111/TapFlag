package com.tapflag;

import com.tapflag.flag.FlagManager;
import com.tapflag.team.TeamManager;
import com.tapflag.timer.GameTimer;
import com.tapflag.world.OreManager;
import com.tapflag.util.MessageUtil;
import org.bukkit.ChatColor;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class GameManager {

    private final TapFlagPlugin plugin;
    private final TeamManager teamManager;
    private final FlagManager flagManager;
    private final GameTimer gameTimer;
    private final OreManager oreManager;

    private boolean running = false;

    /** 플레이테스트 1분 토글 태스크 */
    private BukkitTask playtestToggleTask;

    /** 팀원 모집 대기 중 — 로그인 차단 */
    private final Set<UUID> pendingPlayers = new HashSet<>();

    private static final List<String> TEAM_NAMES =
        List.of("빨강", "주황", "노랑", "초록", "파랑");

    private static final int BORDER_SIZE   = 2000;
    private static final double BORDER_BUFFER = 5.0;

    public GameManager(TapFlagPlugin plugin, TeamManager teamManager,
                       FlagManager flagManager, GameTimer gameTimer, OreManager oreManager) {
        this.plugin = plugin;
        this.teamManager = teamManager;
        this.flagManager = flagManager;
        this.gameTimer = gameTimer;
        this.oreManager = oreManager;
    }

    // ─── 게임 시작 ───────────────────────────────────────────────────────────

    /**
     * @param leaderUuids 팀장 UUID 목록 (2~5명). 각 플레이어가 한 팀의 팀장이 됨.
     * @return 오류 문자열, null이면 성공
     */
    public String startGame(List<UUID> leaderUuids) {
        return startGame(leaderUuids, BORDER_SIZE);
    }

    public String startGame(List<UUID> leaderUuids, int borderSize) {
        if (running) return "이미 게임이 진행 중입니다.";
        int teamCount = leaderUuids.size();
        if (teamCount < 2 || teamCount > 5) return "팀장을 2명~5명 지정해야 합니다.";

        List<String> names = buildTeamNames(teamCount);

        // 팀 생성 + 팀장 합류
        for (int i = 0; i < teamCount; i++) {
            String teamName = names.get(i);
            UUID leaderUuid = leaderUuids.get(i);
            teamManager.createTeam(teamName, leaderUuid);
            teamManager.addToTeam(teamName, leaderUuid);
        }

        // 비팀장 온라인 플레이어 → pendingPlayers + 강퇴
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            if (teamManager.getTeamByPlayer(p.getUniqueId()) == null) {
                pendingPlayers.add(p.getUniqueId());
                p.kickPlayer(ChatColor.YELLOW + "게임이 시작되었습니다.\n"
                    + ChatColor.WHITE + "팀장이 당신을 영입할 때까지 기다려주세요.\n"
                    + ChatColor.GRAY + "(영입 후 재접속하세요)");
            }
        }

        // 깃발 ID → 팀 매핑
        Map<Integer, String> flagTeamMap = new HashMap<>();
        for (int i = 0; i < teamCount; i++) flagTeamMap.put(i + 1, names.get(i));
        flagManager.setFlagTeamMap(flagTeamMap);

        // 깃발 배치
        flagManager.spawnRandomFlags(teamCount);

        // 광맥 생성
        oreManager.generateVeins();

        // 월드 보더
        applyWorldBorder(borderSize);

        // 타이머 시작
        gameTimer.start();
        running = true;
        teamManager.save();

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < teamCount; i++) {
            if (i > 0) sb.append(", ");
            Player lp = plugin.getServer().getPlayer(leaderUuids.get(i));
            String lname = lp != null ? lp.getName() : leaderUuids.get(i).toString().substring(0, 8);
            sb.append("[").append(names.get(i)).append("] ").append(lname);
        }
        plugin.getServer().broadcastMessage(
            MessageUtil.prefix() + ChatColor.GREEN + "" + ChatColor.BOLD
            + "게임 시작! 팀장: " + sb
        );

        var hud = plugin.getHudManager();
        if (hud != null) hud.refreshAll();
        return null;
    }

    // ─── 게임 중지 ───────────────────────────────────────────────────────────

    public void stopGame() {
        running = false;
        gameTimer.stop();

        for (var team : new ArrayList<>(teamManager.getAllTeams())) {
            teamManager.disbandTeam(team.getId());
        }
        teamManager.save();

        flagManager.removeAllFlags();
        flagManager.clearFlagTeamMap();
        oreManager.removeAllVeins();
        var ml = plugin.getMovementLimitListener();
        if (ml != null) ml.clearCache();

        int released = pendingPlayers.size();
        pendingPlayers.clear();

        resetWorldBorder();

        plugin.getServer().broadcastMessage(
            MessageUtil.prefix() + ChatColor.RED + "게임이 중지되었습니다."
        );

        var hud = plugin.getHudManager();
        if (hud != null) hud.refreshAll();
        if (released > 0) {
            plugin.getServer().broadcastMessage(
                MessageUtil.info(released + "명의 대기 플레이어가 이제 접속할 수 있습니다.")
            );
        }
    }

    // ─── 플레이테스트 ──────────────────────────────────────────────────────────

    /**
     * 실제 게임과 동일한 방식으로 단독 테스트.
     * 200블록 보더, 2개 팀, 깃발 2개 → 플레이어 주변에 배치.
     */
    public void startPlaytest(Player player) {
        if (running) stopGame();

        List<String> names = List.of("빨강", "파랑");
        UUID dummy = UUID.randomUUID();
        for (String name : names) teamManager.createTeam(name, dummy);

        // 플레이어 방랑자로 전환
        teamManager.addToWanderer(player.getUniqueId());

        // 플래그-팀 매핑
        Map<Integer, String> map = Map.of(1, "빨강", 2, "파랑");
        flagManager.setFlagTeamMap(map);

        // 깃발 플레이어 주변 배치 (100블록 이상 간격 보장)
        var base = player.getLocation();
        flagManager.placeFlag(1, findNearSurface(base,  60, 0));
        flagManager.placeFlag(2, findNearSurface(base, -60, 0));

        // 광맥 생성 (테스트용)
        oreManager.generateVeins();

        // 월드 보더 (테스트용 소형)
        applyWorldBorder(400);

        gameTimer.start();
        gameTimer.setForceCapture(true);
        running = true;
        teamManager.save();

        // 1분(1200틱)마다 점령 가능 시간 토글
        playtestToggleTask = new BukkitRunnable() {
            @Override public void run() {
                boolean next = !gameTimer.isCapturePhase();
                gameTimer.setForceCapture(next);
                plugin.getServer().broadcastMessage(next
                    ? MessageUtil.success("[테스트] 점령 가능 시간 시작!")
                    : MessageUtil.warn("[테스트] 점령 불가 시간 시작!")
                );
            }
        }.runTaskTimer(plugin, 1200L, 1200L);

        player.sendMessage(MessageUtil.success("플레이테스트 시작!"));
        player.sendMessage(MessageUtil.info(
            "방랑자 상태입니다. 깃발을 점령하여 팀에 합류하세요."));
        player.sendMessage(MessageUtil.info(
            "깃발 #1 → 빨강, 깃발 #2 → 파랑"));
        player.sendMessage(MessageUtil.info(
            "팀 전환 테스트: /tapflag playtest jointeam <팀id>"));
    }

    public void stopPlaytest() {
        if (playtestToggleTask != null) {
            playtestToggleTask.cancel();
            playtestToggleTask = null;
        }
        gameTimer.setForceCapture(false);
        stopGame();
    }

    /** 테스트용 팀 강제 전환 */
    public String playtestJoinTeam(Player player, String teamId) {
        if (!running) return "게임이 진행 중이 아닙니다.";
        var currentTeam = teamManager.getTeamByPlayer(player.getUniqueId());
        if (currentTeam != null) {
            teamManager.removeFromTeam(currentTeam.getId(), player.getUniqueId());
        } else {
            teamManager.addToWanderer(player.getUniqueId());
        }
        if (teamManager.getTeam(teamId) == null) return "존재하지 않는 팀: " + teamId;
        teamManager.addToTeam(teamId, player.getUniqueId());
        return null;
    }

    // ─── 팀원 모집 ────────────────────────────────────────────────────────────

    @SuppressWarnings("deprecation")
    public String recruitPlayer(Player leader, String targetName) {
        var leaderTeam = teamManager.getTeamByPlayer(leader.getUniqueId());
        if (leaderTeam == null) return "팀에 속해 있지 않습니다.";
        if (!leaderTeam.getLeader().equals(leader.getUniqueId())) return "팀장만 팀원을 영입할 수 있습니다.";

        for (UUID uuid : new ArrayList<>(pendingPlayers)) {
            org.bukkit.OfflinePlayer op = plugin.getServer().getOfflinePlayer(uuid);
            if (targetName.equalsIgnoreCase(op.getName())) {
                pendingPlayers.remove(uuid);
                teamManager.addToTeam(leaderTeam.getId(), uuid);
                teamManager.save();
                Player target = plugin.getServer().getPlayer(uuid);
                if (target != null)
                    target.sendMessage(MessageUtil.success("[" + leaderTeam.getId() + "] 팀에 영입되었습니다!"));
                return null;
            }
        }
        return "대기 목록에 없음: " + targetName;
    }

    // ─── 월드 보더 ───────────────────────────────────────────────────────────

    private void applyWorldBorder(int size) {
        World world = plugin.getServer().getWorlds().get(0);
        WorldBorder border = world.getWorldBorder();
        int cx = plugin.getConfig().getInt("map.center-x", 0);
        int cz = plugin.getConfig().getInt("map.center-z", 0);
        border.setCenter(cx, cz);
        border.setSize(size);
        border.setDamageBuffer(BORDER_BUFFER);
        border.setDamageAmount(0.5);
        border.setWarningDistance(50);
        border.setWarningTime(15);
    }

    private void resetWorldBorder() {
        World world = plugin.getServer().getWorlds().get(0);
        world.getWorldBorder().reset();
    }

    // ─── 조회 ────────────────────────────────────────────────────────────────

    public boolean isRunning()              { return running; }
    public boolean isPending(UUID uuid)     { return pendingPlayers.contains(uuid); }
    public void addPending(UUID uuid)       { pendingPlayers.add(uuid); }
    public void removePending(UUID uuid)    { pendingPlayers.remove(uuid); }
    public Set<UUID> getPendingPlayers()    { return pendingPlayers; }

    // ─── 내부 ────────────────────────────────────────────────────────────────

    private Location findNearSurface(Location base, int dx, int dz) {
        World world = base.getWorld();
        int x = base.getBlockX() + dx;
        int z = base.getBlockZ() + dz;
        world.getChunkAt(x >> 4, z >> 4).load();
        int y = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        return new Location(world, x + 0.5, y + 1, z + 0.5);
    }

    private static List<String> buildTeamNames(int count) {
        List<String> result = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            result.add(i < TEAM_NAMES.size() ? TEAM_NAMES.get(i) : "팀" + (i + 1));
        }
        return result;
    }
}
